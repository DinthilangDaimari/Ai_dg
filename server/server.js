const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const telephony = require('./telephony');

// Load environment variables from server/.env for local development (never overrides platform env vars)
const envPath = path.join(__dirname, '.env');
if (fs.existsSync(envPath)) {
  const envContent = fs.readFileSync(envPath, 'utf8');
  envContent.split('\n').forEach(line => {
    const trimmed = line.trim();
    if (trimmed && !trimmed.startsWith('#')) {
      const idx = trimmed.indexOf('=');
      if (idx > 0) {
        const key = trimmed.substring(0, idx).trim();
        const val = trimmed.substring(idx + 1).trim().replace(/^["']|["']$/g, '');
        if (!process.env[key]) {
          process.env[key] = val;
        }
      }
    }
  });
}

const DEFAULT_ALLOWED_ORIGINS = ['https://dinthilangdaimari.github.io'];
const ALLOWED_MODES = new Set(['AI', 'HUMAN']);
const ALLOWED_CALL_STATUSES = new Set(Object.values(telephony.CALL_STATUSES));
const DEFAULT_GEMINI_MODEL = process.env.GEMINI_MODEL || 'gemini-3.5-flash-lite';

// Ephemeral device session store (eliminates hardcoded static tokens in APK)
const SESSION_TTL_MS = 60 * 60 * 1000;
const ephemeralSessions = new Map();

// Rate Limiting: In-memory sliding window limiter (max 30 requests per minute per IP)
const RATE_LIMIT_WINDOW_MS = 60 * 1000;
const MAX_REQUESTS_PER_WINDOW = 30;
const rateLimitMap = new Map();

class HttpError extends Error {
  constructor(statusCode, message) {
    super(message);
    this.name = 'HttpError';
    this.statusCode = statusCode;
  }
}

function getPort() {
  const raw = process.env.PORT || process.env.APP_PORT || process.env.SERVER_PORT || '3000';
  const parsed = parseInt(raw, 10);
  return Number.isInteger(parsed) && parsed > 0 && parsed <= 65535 ? parsed : 3000;
}

function getGeminiApiKey() {
  return (process.env.GEMINI_API_KEY || '').trim();
}

function getAppClientToken() {
  return (process.env.APP_CLIENT_TOKEN || '').trim();
}

function normalizeCorsOrigin(rawOrigin) {
  if (!rawOrigin || typeof rawOrigin !== 'string') return '';
  const trimmed = rawOrigin.trim().replace(/\/+$/, '');
  if (!trimmed) return '';
  if (trimmed === '*') return '*';
  try {
    const parsed = new URL(trimmed);
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return '';
    return parsed.origin;
  } catch (_e) {
    return '';
  }
}

function getAllowedOrigins() {
  const origins = new Set(DEFAULT_ALLOWED_ORIGINS);
  const configured = (process.env.CORS_ALLOWED_ORIGINS || '').trim();
  if (configured) {
    for (const part of configured.split(',')) {
      const normalized = normalizeCorsOrigin(part);
      if (normalized) {
        origins.add(normalized);
      }
    }
  }
  return origins;
}

function isOriginAllowed(rawOrigin) {
  const origin = normalizeCorsOrigin(rawOrigin);
  if (!origin) return false;
  const allowed = getAllowedOrigins();
  if (allowed.has('*')) return true;
  if (allowed.has(origin)) return true;

  if (process.env.NODE_ENV !== 'production') {
    if (/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/i.test(origin)) {
      return true;
    }
  }
  return false;
}

function getCorsHeaders(req) {
  const rawOrigin = req.headers.origin;
  if (!rawOrigin) {
    return {};
  }
  const normalized = normalizeCorsOrigin(rawOrigin);
  if (!normalized || !isOriginAllowed(normalized)) {
    return null;
  }
  return {
    'Access-Control-Allow-Origin': normalized,
    'Vary': 'Origin',
    'Access-Control-Allow-Methods': 'GET, HEAD, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-App-Client-Token',
    'Access-Control-Max-Age': '86400'
  };
}

function getSecurityHeaders() {
  return {
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
    'Referrer-Policy': 'no-referrer',
    'Cache-Control': 'no-store'
  };
}

function resolveWebhookBaseUrl(req) {
  const configured = telephony.normalizeWebhookBaseUrl(process.env.PUBLIC_WEBHOOK_URL);
  if (configured) {
    if (telephony.isDisallowedWebhookHost(configured)) {
      return '';
    }
    return configured;
  }

  const protoHeader = String(req.headers['x-forwarded-proto'] || 'https').split(',')[0].trim().toLowerCase();
  const proto = protoHeader === 'http' || protoHeader === 'https' ? protoHeader : 'https';
  const host = String(req.headers['x-forwarded-host'] || req.headers.host || '').split(',')[0].trim();
  if (!host) return '';
  const candidate = `${proto}://${host}`;
  if (telephony.isDisallowedWebhookHost(candidate)) {
    return '';
  }
  return candidate;
}

function issueEphemeralToken(appId, installId) {
  const token = crypto.randomBytes(32).toString('hex');
  ephemeralSessions.set(token, {
    appId,
    installId,
    expiresAt: Date.now() + SESSION_TTL_MS
  });
  return token;
}

function checkRateLimit(ip) {
  const now = Date.now();
  let clientRecord = rateLimitMap.get(ip);
  if (!clientRecord) {
    clientRecord = [];
    rateLimitMap.set(ip, clientRecord);
  }
  const validTimestamps = clientRecord.filter(t => now - t < RATE_LIMIT_WINDOW_MS);
  rateLimitMap.set(ip, validTimestamps);

  if (validTimestamps.length >= MAX_REQUESTS_PER_WINDOW) {
    return false;
  }
  validTimestamps.push(now);
  return true;
}

function sendJson(req, res, statusCode, data, extraHeaders = {}) {
  const corsHeaders = getCorsHeaders(req);
  const resolvedCors = corsHeaders === null ? {} : corsHeaders;
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    ...getSecurityHeaders(),
    ...resolvedCors,
    ...extraHeaders
  });
  res.end(JSON.stringify(data));
}

function authenticateRequest(req) {
  const authHeader = req.headers['authorization'] || '';
  const customHeader = req.headers['x-app-client-token'] || '';

  const bearerToken = authHeader.startsWith('Bearer ') ? authHeader.substring(7).trim() : '';
  const token = bearerToken || String(customHeader).trim();

  if (!token) return false;

  const appClientToken = getAppClientToken();
  if (appClientToken) {
    const a = Buffer.from(token, 'utf8');
    const b = Buffer.from(appClientToken, 'utf8');
    if (a.length === b.length && crypto.timingSafeEqual(a, b)) {
      return true;
    }
  }

  const session = ephemeralSessions.get(token);
  if (session && session.expiresAt > Date.now()) {
    return true;
  }
  return false;
}

function readBodyBuffer(req, maxBytes = 256 * 1024) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let totalBytes = 0;
    let settled = false;

    req.on('data', chunk => {
      if (settled) return;
      totalBytes += chunk.length;
      if (totalBytes > maxBytes) {
        settled = true;
        req.destroy();
        reject(new HttpError(413, `Request payload exceeds limit of ${maxBytes} bytes`));
        return;
      }
      chunks.push(chunk);
    });

    req.on('end', () => {
      if (settled) return;
      settled = true;
      resolve(Buffer.concat(chunks));
    });

    req.on('error', () => {
      if (settled) return;
      settled = true;
      reject(new HttpError(400, 'Failed to read request body'));
    });
  });
}

async function readJsonBody(req, maxBytes = 256 * 1024) {
  const buffer = await readBodyBuffer(req, maxBytes);
  const raw = buffer.toString('utf8').trim();
  if (!raw) return {};
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (_e) {
    throw new HttpError(400, 'Invalid JSON payload');
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new HttpError(400, 'JSON payload must be an object');
  }
  return parsed;
}

async function readTextBody(req, maxBytes = 256 * 1024) {
  const buffer = await readBodyBuffer(req, maxBytes);
  return buffer.toString('utf8');
}

function assertOptionalString(value, fieldName, maxLength = 2000) {
  if (value === undefined || value === null) return '';
  if (typeof value !== 'string') {
    throw new HttpError(400, `${fieldName} must be a string`);
  }
  if (value.length > maxLength) {
    throw new HttpError(400, `${fieldName} exceeds maximum length of ${maxLength} characters`);
  }
  return value;
}

function validatePhoneNumber(rawPhone) {
  if (typeof rawPhone !== 'string') return null;
  const trimmed = rawPhone.trim();
  if (!trimmed) return null;
  const compact = trimmed.replace(/[\s\-().]/g, '');
  if (!/^\+?[0-9]{7,15}$/.test(compact)) {
    return null;
  }
  return trimmed;
}

function validateWebhookRequest(req, rawBody, url, sessionId) {
  const params = new URLSearchParams(rawBody || '');
  const configuredAccountSid = (process.env.TELEPHONY_ACCOUNT_SID || process.env.TWILIO_ACCOUNT_SID || '').trim();
  const configuredAuthToken = (process.env.TELEPHONY_AUTH_TOKEN || process.env.TWILIO_AUTH_TOKEN || '').trim();

  // Verify AccountSid if present in webhook payload and configured on server
  const payloadAccountSid = (params.get('AccountSid') || '').trim();
  if (configuredAccountSid && payloadAccountSid && payloadAccountSid !== configuredAccountSid) {
    return { valid: false, reason: 'Mismatched Twilio AccountSid' };
  }

  // Verify CallSid matches stored session providerCallId when both exist
  const payloadCallSid = (params.get('CallSid') || '').trim();
  const session = telephony.getSession(sessionId);
  if (session && session.providerCallId && payloadCallSid && payloadCallSid !== session.providerCallId) {
    return { valid: false, reason: 'Mismatched Twilio CallSid for session' };
  }

  // Enforce X-Twilio-Signature in production when Twilio & PUBLIC_WEBHOOK_URL are configured (unless explicitly false),
  // or in any environment when TWILIO_VALIDATE_SIGNATURES=true
  const signatureFlag = (process.env.TWILIO_VALIDATE_SIGNATURES || '').trim().toLowerCase();
  const hasPublicUrl = telephony.hasValidPublicWebhookUrl();
  const shouldVerifySignature =
    signatureFlag === 'true' ||
    (process.env.NODE_ENV === 'production' && signatureFlag !== 'false' && Boolean(configuredAuthToken) && hasPublicUrl);

  if (!shouldVerifySignature) {
    return { valid: true };
  }

  if (!configuredAuthToken) {
    return { valid: false, reason: 'Twilio auth token is missing for signature verification' };
  }

  const signature = req.headers['x-twilio-signature'];
  const baseUrl = resolveWebhookBaseUrl(req);
  if (!signature || !baseUrl) {
    return { valid: false, reason: 'Missing X-Twilio-Signature header or PUBLIC_WEBHOOK_URL' };
  }

  const fullWebhookUrl = `${baseUrl}${url.pathname}${url.search || ''}`;
  const signatureOk = telephony.verifyTwilioSignature(signature, fullWebhookUrl, rawBody, configuredAuthToken);
  return signatureOk ? { valid: true } : { valid: false, reason: 'Invalid X-Twilio-Signature' };
}

let cachedApkHash = null;

function getExternalApkDownloadUrl() {
  const raw = String(process.env.APK_DOWNLOAD_URL || '').trim();
  if (!raw) return '';
  try {
    const parsed = new URL(raw);
    if (parsed.protocol === 'https:' || (process.env.NODE_ENV !== 'production' && parsed.protocol === 'http:')) {
      return parsed.toString();
    }
    return '';
  } catch (_e) {
    return '';
  }
}

function resolveApkSource() {
  const explicitPath = String(process.env.APK_FILE_PATH || '').trim();
  const candidates = [
    explicitPath ? path.resolve(explicitPath) : '',
    path.join(__dirname, 'downloads', 'Dinthi-AI.apk'),
    path.resolve(__dirname, '../app/build/outputs/apk/debug/app-debug.apk'),
    path.resolve(__dirname, '../.build-outputs/app-debug.apk'),
    path.resolve(__dirname, '../Dinthi-AI.apk')
  ].filter(Boolean);

  for (const candidate of candidates) {
    try {
      const stat = fs.statSync(candidate);
      if (stat.isFile() && stat.size > 0) {
        return { filePath: candidate, sizeBytes: stat.size, mtimeMs: stat.mtimeMs };
      }
    } catch (_e) {
      // Check next candidate
    }
  }
  return null;
}

function getApkSha256(apkInfo) {
  if (!apkInfo) return null;
  if (
    cachedApkHash &&
    cachedApkHash.filePath === apkInfo.filePath &&
    cachedApkHash.sizeBytes === apkInfo.sizeBytes &&
    cachedApkHash.mtimeMs === apkInfo.mtimeMs
  ) {
    return cachedApkHash.sha256;
  }
  const hash = crypto.createHash('sha256');
  const fd = fs.openSync(apkInfo.filePath, 'r');
  try {
    const buf = Buffer.allocUnsafe(64 * 1024);
    let bytesRead = 0;
    while ((bytesRead = fs.readSync(fd, buf, 0, buf.length, null)) > 0) {
      hash.update(buf.subarray(0, bytesRead));
    }
  } finally {
    fs.closeSync(fd);
  }
  const sha256 = hash.digest('hex');
  cachedApkHash = { ...apkInfo, sha256 };
  return sha256;
}

function streamApkDownload(req, res) {
  const apkInfo = resolveApkSource();
  const corsHeaders = getCorsHeaders(req);
  const resolvedCors = corsHeaders === null ? {} : corsHeaders;

  if (!apkInfo) {
    const externalUrl = getExternalApkDownloadUrl();
    if (externalUrl) {
      res.writeHead(302, {
        Location: externalUrl,
        ...getSecurityHeaders(),
        ...resolvedCors
      });
      res.end();
      return;
    }
    sendJson(req, res, 404, { error: 'APK file is not available on this server yet.' });
    return;
  }

  const totalSize = apkInfo.sizeBytes;
  const baseHeaders = {
    'Content-Type': 'application/vnd.android.package-archive',
    'Content-Disposition': 'attachment; filename="Dinthi-AI.apk"',
    'Accept-Ranges': 'bytes',
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'no-referrer',
    'Cache-Control': 'public, max-age=300',
    ...resolvedCors
  };

  const rangeHeader = req.headers.range;
  if (rangeHeader && /^bytes=\d*-\d*$/i.test(rangeHeader.trim())) {
    const [rawStart, rawEnd] = rangeHeader.trim().replace(/^bytes=/i, '').split('-');
    let start = rawStart === '' ? 0 : parseInt(rawStart, 10);
    let end = rawEnd === '' ? totalSize - 1 : parseInt(rawEnd, 10);

    if (rawStart === '' && rawEnd !== '') {
      const suffixLength = parseInt(rawEnd, 10);
      start = Math.max(0, totalSize - suffixLength);
      end = totalSize - 1;
    }

    if (!Number.isFinite(start) || !Number.isFinite(end) || start < 0 || end >= totalSize || start > end) {
      res.writeHead(416, {
        'Content-Range': `bytes */${totalSize}`,
        ...getSecurityHeaders(),
        ...resolvedCors
      });
      res.end();
      return;
    }

    const chunkSize = end - start + 1;
    res.writeHead(206, {
      ...baseHeaders,
      'Content-Range': `bytes ${start}-${end}/${totalSize}`,
      'Content-Length': String(chunkSize)
    });

    if (req.method === 'HEAD') {
      res.end();
      return;
    }

    const stream = fs.createReadStream(apkInfo.filePath, { start, end });
    stream.on('error', err => {
      console.error('APK range stream error:', err.message);
      if (!res.headersSent) {
        sendJson(req, res, 500, { error: 'Failed to stream APK file' });
      } else if (!res.writableEnded) {
        res.destroy();
      }
    });
    stream.pipe(res);
    return;
  }

  res.writeHead(200, {
    ...baseHeaders,
    'Content-Length': String(totalSize)
  });

  if (req.method === 'HEAD') {
    res.end();
    return;
  }

  const stream = fs.createReadStream(apkInfo.filePath);
  stream.on('error', err => {
    console.error('APK stream error:', err.message);
    if (!res.headersSent) {
      sendJson(req, res, 500, { error: 'Failed to stream APK file' });
    } else if (!res.writableEnded) {
      res.destroy();
    }
  });
  stream.pipe(res);
}

function sendApkDownloadPage(req, res) {
  const apkInfo = resolveApkSource();
  const externalUrl = getExternalApkDownloadUrl();
  const baseUrl = resolveWebhookBaseUrl(req);
  const sizeMb = apkInfo ? (apkInfo.sizeBytes / (1024 * 1024)).toFixed(2) : null;
  const sha256 = apkInfo ? getApkSha256(apkInfo) : null;
  const directPath = '/download/dinthi-ai.apk';
  const directHref = apkInfo ? directPath : externalUrl;
  const fullDirectUrl = apkInfo ? (baseUrl ? `${baseUrl}${directPath}` : directPath) : externalUrl;

  const html = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Download Dinthi AI APK</title>
  <style>
    body { font-family: system-ui, -apple-system, sans-serif; background: #0B1020; color: #F1F5F9; margin: 0; display: grid; place-items: center; min-height: 100vh; padding: 24px; }
    .card { max-width: 460px; width: 100%; background: #141B2D; border: 1px solid #26324D; border-radius: 20px; padding: 28px; box-sizing: border-box; }
    h1 { margin: 0 0 8px; font-size: 24px; }
    p { color: #94A3B8; line-height: 1.5; margin: 0 0 20px; }
    .btn { display: block; width: 100%; box-sizing: border-box; text-align: center; background: #00E5FF; color: #06101F; font-weight: 700; text-decoration: none; padding: 14px 18px; border-radius: 12px; margin-bottom: 12px; border: none; cursor: pointer; font-size: 16px; }
    .btn-secondary { display: block; width: 100%; box-sizing: border-box; text-align: center; background: #1E293B; color: #E2E8F0; font-weight: 600; text-decoration: none; padding: 12px 16px; border-radius: 12px; border: 1px solid #334155; margin-bottom: 12px; cursor: pointer; font-size: 15px; }
    .status { font-size: 13px; color: #38BDF8; min-height: 18px; margin-bottom: 8px; text-align: center; }
    .meta { margin-top: 16px; font-size: 13px; color: #CBD5E1; word-break: break-all; }
    .meta code { color: #38BDF8; }
  </style>
</head>
<body>
  <main class="card">
    <h1>Dinthi AI for Android</h1>
    <p>Tap below to download the Android APK directly to your device.</p>
    ${directHref
      ? `<a class="btn" href="${directHref}" download="Dinthi-AI.apk">Download Dinthi-AI.apk${sizeMb ? ` (${sizeMb} MB)` : ''}</a>
         <button class="btn-secondary" id="streamBtn" type="button">Direct Save (Fallback)</button>
         <a class="btn-secondary" href="${directHref}" target="_blank" rel="noopener noreferrer">Open Direct Link in New Tab</a>
         <div class="status" id="dlStatus"></div>`
      : `<p>APK download is currently unavailable. Build the APK or set <code>APK_DOWNLOAD_URL</code> on the server.</p>`}
    ${fullDirectUrl ? `<div class="meta">Direct URL:<br /><code>${fullDirectUrl}</code></div>` : ''}
    ${sha256 ? `<div class="meta">SHA-256:<br /><code>${sha256}</code></div>` : ''}
  </main>
  ${directHref
    ? `<script>
    (function () {
      var btn = document.getElementById('streamBtn');
      var status = document.getElementById('dlStatus');
      if (!btn) return;
      btn.addEventListener('click', async function () {
        btn.disabled = true;
        status.textContent = 'Downloading APK...';
        try {
          var res = await fetch('${directHref}', { credentials: 'include' });
          if (!res.ok) throw new Error('HTTP ' + res.status);
          var blob = await res.blob();
          var url = URL.createObjectURL(blob);
          var a = document.createElement('a');
          a.href = url;
          a.download = 'Dinthi-AI.apk';
          document.body.appendChild(a);
          a.click();
          a.remove();
          setTimeout(function () { URL.revokeObjectURL(url); }, 10000);
          status.textContent = 'Download started (' + (blob.size / (1024 * 1024)).toFixed(2) + ' MB).';
        } catch (err) {
          status.textContent = 'Fallback error: ' + (err && err.message ? err.message : 'failed');
        } finally {
          btn.disabled = false;
        }
      });
    })();
  </script>`
    : ''}
</body>
</html>`;

  res.writeHead(apkInfo || externalUrl ? 200 : 404, {
    'Content-Type': 'text/html; charset=utf-8',
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'no-referrer',
    'Cache-Control': 'no-store'
  });
  if (req.method === 'HEAD') {
    res.end();
    return;
  }
  res.end(html);
}

const APK_FILE_DOWNLOAD_PATHS = new Set([
  '/download/apk',
  '/downloads/apk',
  '/download/dinthi-ai.apk',
  '/downloads/dinthi-ai.apk',
  '/download/app-debug.apk',
  '/downloads/app-debug.apk',
  '/dinthi-ai.apk',
  '/app-debug.apk'
]);

const APK_PAGE_PATHS = new Set([
  '/download',
  '/downloads',
  '/apk'
]);

function createAppServer() {
  const server = http.createServer(async (req, res) => {
    try {
      const corsHeaders = getCorsHeaders(req);

      // Handle CORS Preflight
      if (req.method === 'OPTIONS') {
        if (corsHeaders === null) {
          sendJson(req, res, 403, { error: 'Origin not allowed by CORS policy' });
          return;
        }
        res.writeHead(204, {
          ...getSecurityHeaders(),
          ...corsHeaders
        });
        res.end();
        return;
      }

      let url;
      try {
        url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
      } catch (_e) {
        sendJson(req, res, 400, { error: 'Malformed request URL' });
        return;
      }

      const normalizedPath = url.pathname.length > 1 ? url.pathname.replace(/\/+$/, '') || '/' : url.pathname;
      const lowerPath = normalizedPath.toLowerCase();

      const clientIp =
        String(req.headers['x-forwarded-for'] || '').split(',')[0].trim() ||
        req.socket?.remoteAddress ||
        'unknown';
      const geminiApiKey = getGeminiApiKey();

      // Browser root opens one-tap APK download page; health checks / API clients receive JSON
      const acceptsHtml = String(req.headers.accept || '').toLowerCase().includes('text/html');
      if ((req.method === 'GET' || req.method === 'HEAD') && normalizedPath === '/' && acceptsHtml) {
        sendApkDownloadPage(req, res);
        return;
      }

      // Health check endpoint (public, no auth required)
      if (req.method === 'GET' && (normalizedPath === '/health' || normalizedPath === '/')) {
        const telephonyConfig = telephony.getTelephonyConfig();
        sendJson(req, res, 200, {
          status: 'ok',
          service: 'Dinthi AI Secure Backend',
          version: '1.0.0',
          environment: process.env.NODE_ENV || 'development',
          geminiConfigured: Boolean(geminiApiKey),
          telephonyConfigured: Boolean(telephonyConfig.isConfigured),
          publicWebhookConfigured: Boolean(telephonyConfig.publicWebhookConfigured),
          timestamp: Date.now()
        });
        return;
      }

      // Reject browser requests from disallowed origins on /api/* routes (exempt Twilio webhooks)
      if (
        url.pathname.startsWith('/api/') &&
        !url.pathname.startsWith('/api/telephony/webhook/') &&
        corsHeaders === null
      ) {
        sendJson(req, res, 403, { error: 'Origin not allowed by CORS policy' });
        return;
      }

      // Direct APK Download Metadata Endpoint (public, CORS enabled for GitHub Pages frontend)
      if ((req.method === 'GET' || req.method === 'HEAD') && normalizedPath === '/api/app/apk-info') {
        const apkInfo = resolveApkSource();
        const externalUrl = getExternalApkDownloadUrl();
        const baseUrl = resolveWebhookBaseUrl(req);
        const downloadPath = '/download/dinthi-ai.apk';
        const payload = {
          available: Boolean(apkInfo || externalUrl),
          filename: 'Dinthi-AI.apk',
          mimeType: 'application/vnd.android.package-archive',
          sizeBytes: apkInfo ? apkInfo.sizeBytes : null,
          sha256: apkInfo ? getApkSha256(apkInfo) : null,
          downloadPath,
          downloadUrl: baseUrl ? `${baseUrl}${downloadPath}` : (externalUrl || downloadPath),
          externalRedirectUrl: externalUrl || null
        };
        if (req.method === 'HEAD') {
          const resolvedCors = corsHeaders === null ? {} : corsHeaders;
          res.writeHead(200, {
            'Content-Type': 'application/json; charset=utf-8',
            ...getSecurityHeaders(),
            ...resolvedCors
          });
          res.end();
          return;
        }
        sendJson(req, res, 200, payload);
        return;
      }

      // Direct APK File Download Endpoints (case-insensitive)
      if ((req.method === 'GET' || req.method === 'HEAD') && APK_FILE_DOWNLOAD_PATHS.has(lowerPath)) {
        streamApkDownload(req, res);
        return;
      }

      // APK Download Landing Page
      if ((req.method === 'GET' || req.method === 'HEAD') && APK_PAGE_PATHS.has(lowerPath)) {
        sendApkDownloadPage(req, res);
        return;
      }

      // Ephemeral session handshake endpoint
      if (req.method === 'POST' && url.pathname === '/api/auth/session') {
        const payload = await readJsonBody(req, 32 * 1024);
        const appId = assertOptionalString(payload.appId, 'appId', 128).trim();
        const installId = assertOptionalString(payload.installId, 'installId', 128).trim() || 'unknown';
        if (!appId.startsWith('com.aistudio.dinthiai')) {
          sendJson(req, res, 403, { error: 'Forbidden: Unrecognized application ID' });
          return;
        }
        const token = issueEphemeralToken(appId, installId);
        sendJson(req, res, 200, {
          token,
          expiresInSeconds: Math.floor(SESSION_TTL_MS / 1000)
        });
        return;
      }

      // Status check endpoint (requires auth)
      if (req.method === 'GET' && url.pathname === '/api/status') {
        if (!authenticateRequest(req)) {
          sendJson(req, res, 401, { error: 'Unauthorized: Invalid or missing client token' });
          return;
        }
        sendJson(req, res, 200, {
          status: 'operational',
          rateLimitWindowSeconds: 60,
          maxRequestsPerWindow: MAX_REQUESTS_PER_WINDOW,
          model: DEFAULT_GEMINI_MODEL
        });
        return;
      }

      // Rate Limiting check (exempt telephony webhook callbacks and lightweight status/session polling)
      const isExemptFromAiRateLimit =
        url.pathname.startsWith('/api/telephony/webhook/') ||
        (req.method === 'GET' && url.pathname.startsWith('/api/telephony/'));

      if (!isExemptFromAiRateLimit && !checkRateLimit(clientIp)) {
        sendJson(
          req,
          res,
          429,
          {
            error: 'Too Many Requests',
            message: 'Rate limit exceeded. Please wait a moment before sending more messages.'
          },
          { 'Retry-After': '60' }
        );
        return;
      }

      // Chat Streaming Endpoint: POST /api/chat/stream & POST /api/chat
      if (req.method === 'POST' && (url.pathname === '/api/chat/stream' || url.pathname === '/api/chat')) {
        if (!authenticateRequest(req)) {
          sendJson(req, res, 401, { error: 'Unauthorized: Invalid or missing client token' });
          return;
        }

        if (!geminiApiKey) {
          sendJson(req, res, 503, {
            error: 'Server Misconfiguration',
            message: 'Gemini API key is not configured on the server.'
          });
          return;
        }

        const payload = await readJsonBody(req, 10 * 1024 * 1024);
        const prompt = assertOptionalString(payload.prompt, 'prompt', 32000);
        const persona = assertOptionalString(payload.persona, 'persona', 64) || 'Balanced';
        const history = payload.history === undefined ? [] : payload.history;
        if (!Array.isArray(history) || history.length > 50) {
          throw new HttpError(400, 'history must be an array of at most 50 messages');
        }
        const imageBase64 = payload.imageBase64 ? assertOptionalString(payload.imageBase64, 'imageBase64', 8 * 1024 * 1024) : null;
        const imageMimeType = assertOptionalString(payload.imageMimeType, 'imageMimeType', 64) || 'image/jpeg';
        if (imageBase64 && !/^image\/(jpeg|jpg|png|webp|gif)$/i.test(imageMimeType)) {
          throw new HttpError(400, 'Unsupported imageMimeType');
        }

        if (!prompt.trim() && !imageBase64) {
          throw new HttpError(400, 'Prompt or image is required');
        }

        const geminiPayload = buildGeminiPayload(prompt, history, persona, imageBase64, imageMimeType);
        const isStream = url.pathname === '/api/chat/stream';

        if (isStream) {
          res.writeHead(200, {
            'Content-Type': 'text/event-stream',
            'Cache-Control': 'no-cache, no-transform',
            'Connection': 'keep-alive',
            'X-Accel-Buffering': 'no',
            'X-Content-Type-Options': 'nosniff',
            ...(corsHeaders || {})
          });

          try {
            await streamGeminiToClient(geminiPayload, res, req, geminiApiKey);
          } catch (err) {
            console.error('Error streaming from Gemini:', err.message);
            if (!res.writableEnded) {
              res.write(`data: ${JSON.stringify({ error: 'Upstream AI streaming error' })}\n\n`);
              res.end();
            }
          }
        } else {
          try {
            const resultText = await callGeminiUnary(geminiPayload, geminiApiKey);
            sendJson(req, res, 200, { text: resultText });
          } catch (err) {
            console.error('Error calling Gemini unary:', err.message);
            sendJson(req, res, 502, { error: 'Upstream AI request failed' });
          }
        }
        return;
      }

      // Call Screening Turn Endpoint: POST /api/screen/turn
      if (req.method === 'POST' && url.pathname === '/api/screen/turn') {
        if (!authenticateRequest(req)) {
          sendJson(req, res, 401, { error: 'Unauthorized: Invalid or missing client token' });
          return;
        }

        if (!geminiApiKey) {
          sendJson(req, res, 503, { error: 'Server Misconfiguration', message: 'Gemini API key is not configured.' });
          return;
        }

        const payload = await readJsonBody(req, 2 * 1024 * 1024);
        const callerStatement = assertOptionalString(payload.callerStatement, 'callerStatement', 4000);
        const conversationHistory = payload.conversationHistory === undefined ? [] : payload.conversationHistory;
        if (!Array.isArray(conversationHistory)) {
          throw new HttpError(400, 'conversationHistory must be an array');
        }
        const callerInfo = payload.callerInfo && typeof payload.callerInfo === 'object'
          ? payload.callerInfo
          : { name: 'Unknown Caller', number: '' };
        const language = assertOptionalString(payload.language, 'language', 64) || 'English (India)';
        const persona = assertOptionalString(payload.persona, 'persona', 64) || 'Professional';

        const prompt = `You are Dinthi AI, a polite, professional, and concise phone call screening assistant.
You are speaking live to the caller on behalf of the phone owner.
The caller says: "${callerStatement}".
Caller Info: Name: ${callerInfo.name || 'Unknown'}, Number: ${callerInfo.number || 'Unknown'}.
Target Language: ${language}. Persona: ${persona}.

Conversation so far:
${conversationHistory.map(h => `${h.speaker}: ${h.text}`).join('\n')}

INSTRUCTIONS:
1. Speak naturally as Dinthi AI in 1 or 2 concise, spoken sentences (no Markdown, no asterisks, no bullets).
2. Clarify who they are and why they are calling if not yet known.
3. Classify their intent into one of: "Delivery", "Healthcare", "Recruiter", "Telemarketing/Spam", "Personal", "Financial", "General".
4. Estimate spam score from 0 to 100.
5. Provide 3 short action chips for the phone owner (e.g. "I'll call back", "Leave at door", "Hang up").

Respond ONLY in valid JSON matching this schema:
{
  "replyText": "your spoken 1-2 sentence response here",
  "intent": "Delivery/Healthcare/Recruiter/Telemarketing/Spam/Personal/Financial/General",
  "spamScore": 10,
  "isSpam": false,
  "suggestedQuickReplies": ["Option 1", "Option 2", "Option 3"]
}`;

        const geminiPayload = {
          contents: [{ role: 'user', parts: [{ text: prompt }] }],
          generationConfig: {
            temperature: 0.3,
            responseMimeType: 'application/json'
          }
        };

        try {
          const rawJson = await callGeminiUnary(geminiPayload, geminiApiKey);
          let parsed;
          try {
            parsed = JSON.parse(rawJson);
          } catch (_e) {
            parsed = {
              replyText: 'Hello, I am Dinthi AI screening this call. Could you please share your name and the reason for calling?',
              intent: 'General',
              spamScore: 10,
              isSpam: false,
              suggestedQuickReplies: ["Who is this?", "I'll call back", 'Decline call']
            };
          }
          sendJson(req, res, 200, parsed);
        } catch (err) {
          console.error('Error generating screen turn:', err.message);
          sendJson(req, res, 200, {
            replyText: 'Thank you for calling. Could you please state your name and the purpose of your call?',
            intent: 'General',
            spamScore: 15,
            isSpam: false,
            suggestedQuickReplies: ['Ask purpose', "I'll call back later", 'Decline']
          });
        }
        return;
      }

      // Call Screening Summary Endpoint: POST /api/screen/summary
      if (req.method === 'POST' && url.pathname === '/api/screen/summary') {
        if (!authenticateRequest(req)) {
          sendJson(req, res, 401, { error: 'Unauthorized: Invalid or missing client token' });
          return;
        }

        if (!geminiApiKey) {
          sendJson(req, res, 503, { error: 'Server Misconfiguration', message: 'Gemini API key is not configured.' });
          return;
        }

        const payload = await readJsonBody(req, 2 * 1024 * 1024);
        const conversation = payload.conversation === undefined ? [] : payload.conversation;
        if (!Array.isArray(conversation)) {
          throw new HttpError(400, 'conversation must be an array');
        }
        const callerInfo = payload.callerInfo && typeof payload.callerInfo === 'object'
          ? payload.callerInfo
          : { name: 'Unknown Caller', number: '' };
        const durationSeconds = payload.durationSeconds === undefined ? 0 : Number(payload.durationSeconds);
        if (!Number.isFinite(durationSeconds) || durationSeconds < 0) {
          throw new HttpError(400, 'durationSeconds must be a non-negative number');
        }

        const prompt = `You are Dinthi AI Call Analyst. Analyze the following screened phone conversation:
Caller Info: ${callerInfo.name} (${callerInfo.number})
Call Duration: ${durationSeconds} seconds

Transcript:
${conversation.map(c => `[${c.speaker}]: ${c.text}`).join('\n')}

Generate a comprehensive post-call intelligence summary in valid JSON:
{
  "summary": "1-2 clear paragraphs summarizing the call conversation and caller request",
  "reasonForCall": "Single sentence explaining the primary reason",
  "urgency": "High" | "Medium" | "Low",
  "category": "Delivery" | "Healthcare" | "Work/Career" | "Personal" | "Spam/Robocall" | "Financial" | "General",
  "isSpam": boolean,
  "spamScore": number 0-100,
  "keyDetails": ["extracted point 1", "extracted point 2"],
  "recommendedActions": [
    {
      "type": "CALLBACK" | "CALENDAR_REMINDER" | "SAVE_CONTACT" | "BLOCK_SPAM" | "NOTE",
      "title": "Short title",
      "detail": "Description of action"
    }
  ]
}`;

        const geminiPayload = {
          contents: [{ role: 'user', parts: [{ text: prompt }] }],
          generationConfig: {
            temperature: 0.2,
            responseMimeType: 'application/json'
          }
        };

        try {
          const rawJson = await callGeminiUnary(geminiPayload, geminiApiKey);
          let parsed;
          try {
            parsed = JSON.parse(rawJson);
          } catch (_e) {
            parsed = {
              summary: `Call completed with ${callerInfo.name}. The caller reached out regarding general inquiries.`,
              reasonForCall: 'General inquiry',
              urgency: 'Medium',
              category: 'General',
              isSpam: false,
              spamScore: 10,
              keyDetails: ['Spoke with Dinthi AI screening assistant'],
              recommendedActions: [
                { type: 'CALLBACK', title: 'Call Back', detail: `Return call to ${callerInfo.number}` }
              ]
            };
          }
          sendJson(req, res, 200, parsed);
        } catch (err) {
          console.error('Error generating summary:', err.message);
          sendJson(req, res, 200, {
            summary: `Call was screened by Dinthi AI for ${durationSeconds} seconds.`,
            reasonForCall: `Inquiry from ${callerInfo.name}`,
            urgency: 'Medium',
            category: 'General',
            isSpam: false,
            spamScore: 10,
            keyDetails: ['Call handled by Dinthi AI'],
            recommendedActions: [
              { type: 'CALLBACK', title: 'Call Back', detail: `Call ${callerInfo.number}` }
            ]
          });
        }
        return;
      }

      // Outbound Call Assistant Planner: POST /api/assistant/outbound_plan
      if (req.method === 'POST' && url.pathname === '/api/assistant/outbound_plan') {
        if (!authenticateRequest(req)) {
          sendJson(req, res, 401, { error: 'Unauthorized: Invalid or missing client token' });
          return;
        }

        const payload = await readJsonBody(req, 512 * 1024);
        const recipientName = assertOptionalString(payload.recipientName, 'recipientName', 120) || 'Recipient';
        const goal = assertOptionalString(payload.goal, 'goal', 1000) || 'General inquiry';
        const language = assertOptionalString(payload.language, 'language', 64) || 'English (India)';

        const prompt = `You are Dinthi AI Outbound Call Assistant.
The user wants to place a call to "${recipientName}" with the objective: "${goal}".
Target language: ${language}.

Generate an optimal phone call plan in strictly valid JSON:
{
  "openingScript": "Clear 1-2 sentence spoken opener for the call",
  "talkingPoints": ["Bullet 1 key point to cover", "Bullet 2 question to ask", "Bullet 3 closure point"],
  "fallbackScript": "What to say if the recipient is unavailable or answers with voicemail",
  "suggestedDurationMinutes": 3
}`;

        try {
          if (!geminiApiKey) throw new Error('Gemini API key missing');
          const geminiPayload = {
            contents: [{ role: 'user', parts: [{ text: prompt }] }],
            generationConfig: { temperature: 0.2, responseMimeType: 'application/json' }
          };
          const rawJson = await callGeminiUnary(geminiPayload, geminiApiKey);
          const parsed = JSON.parse(rawJson);
          sendJson(req, res, 200, parsed);
        } catch (_e) {
          sendJson(req, res, 200, {
            openingScript: `Hello ${recipientName}, I am calling regarding ${goal}.`,
            talkingPoints: [`State purpose: ${goal}`, 'Ask for best next steps', 'Confirm timing'],
            fallbackScript: `Please call me back regarding ${goal} at your earliest convenience.`,
            suggestedDurationMinutes: 3
          });
        }
        return;
      }

      // Voice Note Summarizer Endpoint: POST /api/voicenote/summarize
      if (req.method === 'POST' && url.pathname === '/api/voicenote/summarize') {
        if (!authenticateRequest(req)) {
          sendJson(req, res, 401, { error: 'Unauthorized: Invalid or missing client token' });
          return;
        }

        const payload = await readJsonBody(req, 512 * 1024);
        const transcription = assertOptionalString(payload.transcription, 'transcription', 50000).trim();
        if (!transcription) {
          throw new HttpError(400, 'Transcription text required');
        }

        const prompt = `You are Dinthi AI Note Analyst. Analyze this transcribed voice note:
"${transcription}"

Return strictly valid JSON:
{
  "title": "Short descriptive 3-5 word title",
  "summary": "1-2 sentence concise summary",
  "actionItems": ["Action 1", "Action 2"],
  "tags": ["Tag1", "Tag2"]
}`;

        try {
          if (!geminiApiKey) throw new Error('Gemini API key missing');
          const geminiPayload = {
            contents: [{ role: 'user', parts: [{ text: prompt }] }],
            generationConfig: { temperature: 0.2, responseMimeType: 'application/json' }
          };
          const rawJson = await callGeminiUnary(geminiPayload, geminiApiKey);
          const parsed = JSON.parse(rawJson);
          sendJson(req, res, 200, parsed);
        } catch (_e) {
          sendJson(req, res, 200, {
            title: 'Voice Memo Note',
            summary: transcription.substring(0, 100),
            actionItems: ['Review voice note transcription'],
            tags: ['VoiceNote', 'Personal']
          });
        }
        return;
      }

      // ==========================================================
      // REAL OUTBOUND TELEPHONY LAYER ENDPOINTS
      // ==========================================================

      // Telephony status and provider configuration
      if (req.method === 'GET' && url.pathname === '/api/telephony/status') {
        if (!authenticateRequest(req)) {
          sendJson(req, res, 401, { error: 'Unauthorized: Invalid or missing client token' });
          return;
        }
        const config = telephony.getTelephonyConfig();
        sendJson(req, res, 200, config);
        return;
      }

      // Initiate Outbound Call Session: POST /api/telephony/call
      if (req.method === 'POST' && url.pathname === '/api/telephony/call') {
        if (!authenticateRequest(req)) {
          sendJson(req, res, 401, { error: 'Unauthorized: Invalid or missing client token' });
          return;
        }

        const payload = await readJsonBody(req, 64 * 1024);
        const validPhone = validatePhoneNumber(payload.phoneNumber);
        if (!validPhone) {
          throw new HttpError(400, 'Valid recipient phone number is required (7 to 15 digits, optional + prefix)');
        }

        const mode = payload.mode === undefined ? 'AI' : String(payload.mode).trim().toUpperCase();
        if (!ALLOWED_MODES.has(mode)) {
          throw new HttpError(400, 'Invalid mode: must be AI or HUMAN');
        }

        const recipientName = assertOptionalString(payload.recipientName, 'recipientName', 120).trim() || validPhone;
        const userName = assertOptionalString(payload.userName, 'userName', 120).trim() || 'the user';
        const goal = assertOptionalString(payload.goal, 'goal', 500).trim() || 'General Call';
        const notes = assertOptionalString(payload.notes, 'notes', 2000).trim();
        const language = assertOptionalString(payload.language, 'language', 64).trim() || 'English (India)';
        const persona = assertOptionalString(payload.persona, 'persona', 64).trim() || 'Professional';

        const webhookBaseUrl = resolveWebhookBaseUrl(req);
        const session = await telephony.createSession({
          recipientName,
          phoneNumber: validPhone,
          userName,
          goal,
          notes,
          mode,
          language,
          persona,
          webhookBaseUrl
        });

        sendJson(req, res, 201, {
          status: session.status === telephony.CALL_STATUSES.FAILED ? 'failed' : 'initiated',
          notConfigured: !telephony.resolveTelephonyProvider().isConfigured() && session.mode === 'AI',
          session
        });
        return;
      }

      // Telephony Provider Voice Pickup Webhook: POST /api/telephony/webhook/voice/:id
      const webhookVoiceMatch = url.pathname.match(/^\/api\/telephony\/webhook\/voice\/([a-zA-Z0-9_-]{3,80})$/);
      if (req.method === 'POST' && webhookVoiceMatch) {
        const sessionId = webhookVoiceMatch[1];
        const rawBody = await readTextBody(req, 256 * 1024);
        const webhookCheck = validateWebhookRequest(req, rawBody, url, sessionId);
        if (!webhookCheck.valid) {
          res.writeHead(403, { 'Content-Type': 'text/plain', ...getSecurityHeaders() });
          res.end(webhookCheck.reason || 'Invalid webhook request');
          return;
        }
        const webhookBaseUrl = resolveWebhookBaseUrl(req);
        try {
          const result = await telephony.handleWebhook('voice', sessionId, rawBody, {
            webhookBaseUrl,
            geminiApiKey
          });
          res.writeHead(result.statusCode || 200, {
            'Content-Type': result.contentType || 'text/xml',
            ...getSecurityHeaders()
          });
          res.end(result.body || '');
        } catch (err) {
          console.error('Webhook voice error:', err.message);
          res.writeHead(200, { 'Content-Type': 'text/xml', ...getSecurityHeaders() });
          res.end('<?xml version="1.0" encoding="UTF-8"?><Response><Hangup/></Response>');
        }
        return;
      }

      // Telephony Provider Speech Gather Webhook: POST /api/telephony/webhook/gather/:id
      const webhookGatherMatch = url.pathname.match(/^\/api\/telephony\/webhook\/gather\/([a-zA-Z0-9_-]{3,80})$/);
      if (req.method === 'POST' && webhookGatherMatch) {
        const sessionId = webhookGatherMatch[1];
        const rawBody = await readTextBody(req, 256 * 1024);
        const webhookCheck = validateWebhookRequest(req, rawBody, url, sessionId);
        if (!webhookCheck.valid) {
          res.writeHead(403, { 'Content-Type': 'text/plain', ...getSecurityHeaders() });
          res.end(webhookCheck.reason || 'Invalid webhook request');
          return;
        }
        const webhookBaseUrl = resolveWebhookBaseUrl(req);
        try {
          const result = await telephony.handleWebhook('gather', sessionId, rawBody, {
            webhookBaseUrl,
            geminiApiKey
          });
          res.writeHead(result.statusCode || 200, {
            'Content-Type': result.contentType || 'text/xml',
            ...getSecurityHeaders()
          });
          res.end(result.body || '');
        } catch (err) {
          console.error('Webhook gather error:', err.message);
          res.writeHead(200, { 'Content-Type': 'text/xml', ...getSecurityHeaders() });
          res.end('<?xml version="1.0" encoding="UTF-8"?><Response><Hangup/></Response>');
        }
        return;
      }

      // Telephony Provider Status Callback Webhook: POST /api/telephony/webhook/status/:id
      const webhookStatusMatch = url.pathname.match(/^\/api\/telephony\/webhook\/status\/([a-zA-Z0-9_-]{3,80})$/);
      if (req.method === 'POST' && webhookStatusMatch) {
        const sessionId = webhookStatusMatch[1];
        const rawBody = await readTextBody(req, 256 * 1024);
        const webhookCheck = validateWebhookRequest(req, rawBody, url, sessionId);
        if (!webhookCheck.valid) {
          res.writeHead(403, { 'Content-Type': 'text/plain', ...getSecurityHeaders() });
          res.end(webhookCheck.reason || 'Invalid webhook request');
          return;
        }
        try {
          const result = await telephony.handleWebhook('status', sessionId, rawBody, {
            webhookBaseUrl: resolveWebhookBaseUrl(req),
            geminiApiKey
          });
          res.writeHead(result.statusCode || 200, {
            'Content-Type': result.contentType || 'text/plain',
            ...getSecurityHeaders()
          });
          res.end(result.body || 'OK');
        } catch (err) {
          console.error('Webhook status error:', err.message);
          res.writeHead(200, { 'Content-Type': 'text/plain', ...getSecurityHeaders() });
          res.end('OK');
        }
        return;
      }

      // Get Session status & transcript: GET /api/telephony/session/:id
      const sessionGetMatch = url.pathname.match(/^\/api\/telephony\/session\/([a-zA-Z0-9_-]{3,80})$/);
      if (req.method === 'GET' && sessionGetMatch) {
        if (!authenticateRequest(req)) {
          sendJson(req, res, 401, { error: 'Unauthorized: Invalid or missing client token' });
          return;
        }
        const sessionId = sessionGetMatch[1];
        const session = await telephony.getSessionWithSync(sessionId, geminiApiKey);
        if (!session) {
          sendJson(req, res, 404, { error: 'Call session not found' });
          return;
        }
        sendJson(req, res, 200, { session });
        return;
      }

      // Update session status: POST /api/telephony/session/:id/status
      const sessionStatusMatch = url.pathname.match(/^\/api\/telephony\/session\/([a-zA-Z0-9_-]{3,80})\/status$/);
      if (req.method === 'POST' && sessionStatusMatch) {
        if (!authenticateRequest(req)) {
          sendJson(req, res, 401, { error: 'Unauthorized: Invalid or missing client token' });
          return;
        }
        const sessionId = sessionStatusMatch[1];
        const payload = await readJsonBody(req, 32 * 1024);
        const requestedStatus = assertOptionalString(payload.status, 'status', 32).trim().toUpperCase();
        if (!ALLOWED_CALL_STATUSES.has(requestedStatus)) {
          throw new HttpError(400, 'Invalid call status');
        }
        const detail = assertOptionalString(payload.detail, 'detail', 500);
        const updated = telephony.updateSessionStatus(sessionId, requestedStatus, detail);
        if (!updated) {
          sendJson(req, res, 404, { error: 'Call session not found' });
          return;
        }
        sendJson(req, res, 200, { session: updated });
        return;
      }

      // Execute dialogue turn: POST /api/telephony/session/:id/turn
      const sessionTurnMatch = url.pathname.match(/^\/api\/telephony\/session\/([a-zA-Z0-9_-]{3,80})\/turn$/);
      if (req.method === 'POST' && sessionTurnMatch) {
        if (!authenticateRequest(req)) {
          sendJson(req, res, 401, { error: 'Unauthorized: Invalid or missing client token' });
          return;
        }
        const sessionId = sessionTurnMatch[1];
        const existingSession = telephony.getSession(sessionId);
        if (!existingSession) {
          sendJson(req, res, 404, { error: 'Call session not found' });
          return;
        }

        const payload = await readJsonBody(req, 64 * 1024);
        const text = assertOptionalString(payload.text, 'text', 4000);
        if (text.trim() && !geminiApiKey) {
          sendJson(req, res, 503, { error: 'Gemini API key is not configured on the secure backend.' });
          return;
        }

        const turnResult = await telephony.processTurn({
          sessionId,
          callerStatement: text,
          geminiApiKey
        });
        const currentSession = telephony.getSession(sessionId);
        sendJson(req, res, 200, {
          turn: turnResult,
          session: currentSession
        });
        return;
      }

      // End Outbound Call & generate summary: POST /api/telephony/session/:id/end
      const sessionEndMatch = url.pathname.match(/^\/api\/telephony\/session\/([a-zA-Z0-9_-]{3,80})\/end$/);
      if (req.method === 'POST' && sessionEndMatch) {
        if (!authenticateRequest(req)) {
          sendJson(req, res, 401, { error: 'Unauthorized: Invalid or missing client token' });
          return;
        }
        const sessionId = sessionEndMatch[1];
        const payload = await readJsonBody(req, 32 * 1024);
        const reason = assertOptionalString(payload.reason, 'reason', 120).trim() || 'user_ended';

        const endedSession = await telephony.endSession({
          sessionId,
          reason,
          geminiApiKey
        });
        if (!endedSession) {
          sendJson(req, res, 404, { error: 'Call session not found' });
          return;
        }
        sendJson(req, res, 200, {
          status: 'ended',
          session: endedSession,
          summary: endedSession.summary
        });
        return;
      }

      sendJson(req, res, 404, { error: 'Endpoint not found' });
    } catch (err) {
      if (err instanceof HttpError) {
        if (!res.headersSent) {
          sendJson(req, res, err.statusCode, { error: err.message });
        }
        return;
      }
      console.error('Unhandled request error:', err?.message || 'Unknown error');
      if (!res.headersSent) {
        sendJson(req, res, 500, { error: 'Internal server error' });
      } else if (!res.writableEnded) {
        res.end();
      }
    }
  });

  server.requestTimeout = 30000;
  server.headersTimeout = 15000;
  server.keepAliveTimeout = 65000;

  return server;
}

function buildGeminiPayload(prompt, history, persona, imageBase64, imageMimeType) {
  const systemPrompt = {
    'Creative': 'You are Dinthi AI, a creative, visionary, and imaginative AI assistant. Use rich descriptions and innovative ideas.',
    'Concise': 'You are Dinthi AI. Provide direct, concise, bullet-pointed answers without fluff or preamble.',
    'Coder': 'You are Dinthi AI, an expert software engineer. Provide modern, clean, production-ready code with concise explanations.'
  }[persona] || 'You are Dinthi AI, a friendly, intelligent, and helpful modern Android AI assistant. Provide well-structured answers using clean Markdown and code blocks when appropriate.';

  const contents = [];

  if (Array.isArray(history)) {
    const recent = history.slice(-10);
    for (const msg of recent) {
      if (!msg || typeof msg !== 'object') continue;
      const role = msg.role === 'assistant' ? 'model' : 'user';
      contents.push({
        role,
        parts: [{ text: String(msg.content || '') }]
      });
    }
  }

  const userParts = [];
  if (imageBase64) {
    userParts.push({
      inline_data: {
        mime_type: imageMimeType,
        data: imageBase64
      }
    });
  }
  if (prompt) {
    userParts.push({ text: prompt });
  }

  contents.push({
    role: 'user',
    parts: userParts
  });

  return {
    system_instruction: {
      parts: [{ text: systemPrompt }]
    },
    contents,
    generationConfig: {
      temperature: persona === 'Creative' ? 0.9 : 0.7,
      topP: 0.95
    }
  };
}

async function streamGeminiToClient(geminiPayload, clientRes, clientReq, geminiApiKey) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(DEFAULT_GEMINI_MODEL)}:streamGenerateContent?alt=sse&key=${geminiApiKey}`;

  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(geminiPayload)
  });

  if (!response.ok) {
    console.error(`Gemini stream error: HTTP ${response.status}`);
    clientRes.write(`data: ${JSON.stringify({ error: `Upstream AI error (${response.status})` })}\n\n`);
    clientRes.end();
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  let clientAborted = false;
  clientReq.on('close', () => {
    clientAborted = true;
    reader.cancel().catch(() => {});
  });

  while (!clientAborted) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';

    for (const line of lines) {
      if (line.startsWith('data:')) {
        const jsonStr = line.substring(5).trim();
        if (jsonStr) {
          try {
            const parsed = JSON.parse(jsonStr);
            const candidates = parsed.candidates || [];
            if (candidates.length > 0) {
              const parts = candidates[0].content?.parts || [];
              for (const part of parts) {
                if (part.text) {
                  clientRes.write(`data: ${JSON.stringify({ text: part.text })}\n\n`);
                }
              }
            }
          } catch (_e) {
            // Ignore partial JSON lines in stream buffer
          }
        }
      }
    }
  }

  if (!clientAborted && !clientRes.writableEnded) {
    clientRes.write('data: [DONE]\n\n');
    clientRes.end();
  }
}

async function callGeminiUnary(geminiPayload, geminiApiKey) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(DEFAULT_GEMINI_MODEL)}:generateContent?key=${geminiApiKey}`;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15000);

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(geminiPayload),
      signal: controller.signal
    });

    if (!response.ok) {
      const errText = await response.text();
      throw new Error(`HTTP ${response.status}: ${String(errText || '').slice(0, 300)}`);
    }

    const json = await response.json();
    return json.candidates?.[0]?.content?.parts?.[0]?.text || '';
  } finally {
    clearTimeout(timer);
  }
}

function startCleanupTimer() {
  const timer = setInterval(() => {
    const now = Date.now();
    for (const [ip, timestamps] of rateLimitMap.entries()) {
      const valid = timestamps.filter(t => now - t < RATE_LIMIT_WINDOW_MS);
      if (valid.length === 0) {
        rateLimitMap.delete(ip);
      } else {
        rateLimitMap.set(ip, valid);
      }
    }
    for (const [tok, info] of ephemeralSessions.entries()) {
      if (info.expiresAt < now) {
        ephemeralSessions.delete(tok);
      }
    }
  }, 5 * 60 * 1000);
  if (typeof timer.unref === 'function') {
    timer.unref();
  }
  return timer;
}

function startServer(port = getPort()) {
  const server = createAppServer();
  const cleanupTimer = startCleanupTimer();

  const shutdown = signal => {
    console.log(`Received ${signal}, shutting down Dinthi AI server gracefully...`);
    clearInterval(cleanupTimer);
    server.close(() => {
      process.exit(0);
    });
    setTimeout(() => process.exit(0), 5000).unref();
  };

  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('uncaughtException', err => {
    console.error('Uncaught Exception in Dinthi AI server:', err?.message || err);
  });
  process.on('unhandledRejection', reason => {
    console.error('Unhandled Rejection in Dinthi AI server:', reason?.message || reason);
  });

  const logStartupBanner = activePort => {
    const config = telephony.getTelephonyConfig();
    console.log(`Dinthi AI Secure Server running on port ${activePort}`);
    console.log(`Environment: ${process.env.NODE_ENV || 'development'}`);
    console.log(`Gemini API Key configured: ${getGeminiApiKey() ? 'YES (Server-side Only)' : 'NO'}`);
    console.log(`Telephony provider status: ${config.statusLabel} (${config.provider})`);
    console.log(`Public webhook URL configured: ${config.publicWebhookConfigured ? 'YES' : 'NO'}`);
    console.log(`Allowed CORS origins: ${Array.from(getAllowedOrigins()).join(', ')}`);
  };

  let activePort = port;
  const fallbackAppPort = parseInt(process.env.APP_PORT || process.env.SERVER_PORT || '', 10);
  let attemptedFallback = false;

  server.on('error', err => {
    if (
      err &&
      err.code === 'EADDRINUSE' &&
      !attemptedFallback &&
      Number.isInteger(fallbackAppPort) &&
      fallbackAppPort > 0 &&
      fallbackAppPort !== activePort
    ) {
      attemptedFallback = true;
      console.warn(`Port ${activePort} is already in use; binding to fallback APP_PORT ${fallbackAppPort}...`);
      activePort = fallbackAppPort;
      server.listen(activePort, '0.0.0.0');
      return;
    }
    console.error('Failed to start Dinthi AI server:', err?.message || err);
    process.exit(1);
  });

  server.on('listening', () => {
    logStartupBanner(activePort);
  });

  server.listen(activePort, '0.0.0.0');

  return server;
}

if (require.main === module) {
  startServer();
}

module.exports = {
  createAppServer,
  startServer,
  getPort,
  normalizeCorsOrigin,
  getAllowedOrigins,
  isOriginAllowed,
  validatePhoneNumber,
  resolveWebhookBaseUrl,
  resolveApkSource
};
