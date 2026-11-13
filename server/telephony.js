const fs = require('fs');
const path = require('path');

// Load environment variables from server/.env if present
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

// Active sessions map: sessionId -> CallSession
const sessions = new Map();

const CALL_STATUSES = {
  PREPARING: 'PREPARING',
  CALLING: 'CALLING',
  RINGING: 'RINGING',
  CONNECTED: 'CONNECTED',
  AI_SPEAKING: 'AI_SPEAKING',
  LISTENING: 'LISTENING',
  PROCESSING: 'PROCESSING',
  ENDING: 'ENDING',
  COMPLETED: 'COMPLETED',
  FAILED: 'FAILED'
};

function maskPhoneNumber(num) {
  if (!num || num.length < 6) return num;
  return `${num.slice(0, 3)}****${num.slice(-3)}`;
}

function escapeXml(unsafe) {
  return String(unsafe || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function mapSpeechLanguageCode(lang) {
  const l = (lang || '').toLowerCase();
  if (l.includes('hindi') || l.includes('hinglish')) return 'hi-IN';
  if (l.includes('bengali')) return 'bn-IN';
  if (l.includes('tamil')) return 'ta-IN';
  if (l.includes('telugu')) return 'te-IN';
  if (l.includes('kannada')) return 'kn-IN';
  if (l.includes('marathi')) return 'mr-IN';
  return 'en-IN';
}

function normalizeWebhookBaseUrl(rawUrl) {
  return String(rawUrl || '').trim().replace(/\/+$/, '');
}

function isDisallowedWebhookHost(rawUrl) {
  const normalized = normalizeWebhookBaseUrl(rawUrl).toLowerCase();
  if (!normalized) return true;
  if (
    normalized.includes('localhost') ||
    normalized.includes('127.0.0.1') ||
    normalized.includes('0.0.0.0') ||
    normalized.includes('github.io')
  ) {
    return true;
  }
  return false;
}

function hasValidPublicWebhookUrl(rawUrl = process.env.PUBLIC_WEBHOOK_URL) {
  const normalized = normalizeWebhookBaseUrl(rawUrl);
  return /^https:\/\/[a-z0-9.-]+/i.test(normalized) && !isDisallowedWebhookHost(normalized);
}

function verifyTwilioSignature(signatureHeader, fullUrl, formBody, authToken) {
  if (!signatureHeader || !fullUrl || !authToken) return false;
  const crypto = require('crypto');
  const params = new URLSearchParams(formBody || '');
  const sortedKeys = Array.from(new Set(params.keys())).sort();
  let data = fullUrl;
  for (const key of sortedKeys) {
    const values = params.getAll(key);
    for (const value of values) {
      data += key + value;
    }
  }
  const expected = crypto.createHmac('sha1', authToken).update(data, 'utf8').digest('base64');
  const expectedBuf = Buffer.from(expected, 'utf8');
  const actualBuf = Buffer.from(String(signatureHeader).trim(), 'utf8');
  if (expectedBuf.length !== actualBuf.length) return false;
  return crypto.timingSafeEqual(expectedBuf, actualBuf);
}

async function fetchWithTimeout(url, options = {}, timeoutMs = 12000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Provider-agnostic TelephonyProvider interface
 * Decouples Dinthi AI backend from any single telephony vendor (Twilio, Exotel, Plivo, etc.)
 */
class TelephonyProvider {
  getName() {
    throw new Error('Not implemented');
  }

  isConfigured() {
    return false;
  }

  getFromNumberMasked() {
    return null;
  }

  getAccountIdMasked() {
    return null;
  }

  getCapabilities() {
    return {
      bargeIn: false,
      hindi: true,
      english: true,
      hinglish: true,
      silenceHandling: false,
      liveCallTakeover: false
    };
  }

  async createOutboundCall(_session, _webhookBaseUrl) {
    throw new Error('Telephony provider is not configured');
  }

  async getCallStatus(_providerCallId) {
    return null;
  }

  async endCall(_providerCallId) {
    return false;
  }

  async handleCallWebhook(_eventType, _sessionId, _payload, _context) {
    return { statusCode: 400, contentType: 'text/plain', body: 'Provider not configured' };
  }
}

/**
 * Unconfigured fallback provider when no live PSTN/VoIP credentials exist in server/.env
 */
class UnconfiguredTelephonyProvider extends TelephonyProvider {
  constructor(requestedProviderName = 'Not configured') {
    super();
    this.requestedProviderName = requestedProviderName;
  }

  getName() {
    return this.requestedProviderName;
  }

  isConfigured() {
    return false;
  }

  async createOutboundCall(_session, _webhookBaseUrl) {
    return {
      ok: false,
      notConfigured: true,
      error: 'NOT CONFIGURED: Telephony provider is not configured on the secure backend. Set TELEPHONY_PROVIDER, TELEPHONY_ACCOUNT_SID, TELEPHONY_AUTH_TOKEN, TELEPHONY_FROM_NUMBER, and PUBLIC_WEBHOOK_URL on the server.'
    };
  }

  async getCallStatus(_providerCallId) {
    return {
      configured: false,
      providerStatus: 'not_configured',
      mappedStatus: CALL_STATUSES.FAILED
    };
  }

  async endCall(_providerCallId) {
    return false;
  }

  async handleCallWebhook(_eventType, _sessionId, _payload, _context) {
    return {
      statusCode: 503,
      contentType: 'text/xml',
      body: '<?xml version="1.0" encoding="UTF-8"?><Response><Hangup/></Response>'
    };
  }
}

/**
 * Legitimate Twilio Programmable Voice TelephonyProvider adapter
 * Requires server-side credentials in server/.env and a reachable PUBLIC_WEBHOOK_URL.
 */
class TwilioTelephonyProvider extends TelephonyProvider {
  constructor({ accountSid, authToken, fromNumber, webhookBaseUrl }) {
    super();
    this.accountSid = accountSid;
    this.authToken = authToken;
    this.fromNumber = fromNumber;
    this.webhookBaseUrl = webhookBaseUrl;
  }

  getName() {
    return 'Twilio Voice';
  }

  isConfigured() {
    return !!(this.accountSid && this.authToken && this.fromNumber);
  }

  getFromNumberMasked() {
    return maskPhoneNumber(this.fromNumber);
  }

  getAccountIdMasked() {
    if (!this.accountSid) return null;
    return `${this.accountSid.substring(0, 4)}...${this.accountSid.slice(-4)}`;
  }

  getCapabilities() {
    return {
      bargeIn: true,
      hindi: true,
      english: true,
      hinglish: true,
      silenceHandling: true,
      liveCallTakeover: false
    };
  }

  async createOutboundCall(session, fallbackBaseUrl) {
    if (!this.isConfigured()) {
      return { ok: false, notConfigured: true, error: 'Missing Twilio credentials on secure backend' };
    }

    const candidateUrl = normalizeWebhookBaseUrl(this.webhookBaseUrl) || normalizeWebhookBaseUrl(fallbackBaseUrl);
    if (!candidateUrl || !candidateUrl.startsWith('https://') || isDisallowedWebhookHost(candidateUrl)) {
      return {
        ok: false,
        notConfigured: true,
        error: 'NOT CONFIGURED: PUBLIC_WEBHOOK_URL must be a publicly reachable HTTPS backend URL (never localhost or github.io).'
      };
    }

    const endpoint = `https://api.twilio.com/2010-04-01/Accounts/${encodeURIComponent(this.accountSid)}/Calls.json`;
    const authHeader = 'Basic ' + Buffer.from(`${this.accountSid}:${this.authToken}`).toString('base64');

    const params = new URLSearchParams();
    params.append('To', session.phoneNumber);
    params.append('From', this.fromNumber);
    params.append('Url', `${candidateUrl}/api/telephony/webhook/voice/${session.id}`);
    params.append('StatusCallback', `${candidateUrl}/api/telephony/webhook/status/${session.id}`);
    params.append('StatusCallbackEvent', 'initiated ringing answered completed');
    params.append('StatusCallbackMethod', 'POST');

    const response = await fetchWithTimeout(endpoint, {
      method: 'POST',
      headers: {
        Authorization: authHeader,
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      body: params.toString()
    });

    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      return {
        ok: false,
        error: data?.message || `Telephony provider error HTTP ${response.status}`
      };
    }

    return {
      ok: true,
      providerCallId: data.sid,
      providerStatus: data.status
    };
  }

  async getCallStatus(providerCallId) {
    if (!this.isConfigured() || !providerCallId) return null;
    const endpoint = `https://api.twilio.com/2010-04-01/Accounts/${encodeURIComponent(this.accountSid)}/Calls/${encodeURIComponent(providerCallId)}.json`;
    const authHeader = 'Basic ' + Buffer.from(`${this.accountSid}:${this.authToken}`).toString('base64');
    try {
      const response = await fetchWithTimeout(endpoint, {
        method: 'GET',
        headers: { Authorization: authHeader }
      });
      if (!response.ok) return null;
      const data = await response.json();
      const rawStatus = (data.status || '').toLowerCase();
      const mappedStatus = mapTwilioStatus(rawStatus);
      return {
        configured: true,
        providerStatus: rawStatus,
        mappedStatus,
        durationSeconds: data.duration ? parseInt(data.duration, 10) || 0 : 0
      };
    } catch (_e) {
      return null;
    }
  }

  async endCall(providerCallId) {
    if (!this.isConfigured() || !providerCallId) return false;
    const endpoint = `https://api.twilio.com/2010-04-01/Accounts/${encodeURIComponent(this.accountSid)}/Calls/${encodeURIComponent(providerCallId)}.json`;
    const authHeader = 'Basic ' + Buffer.from(`${this.accountSid}:${this.authToken}`).toString('base64');
    const params = new URLSearchParams();
    params.append('Status', 'completed');

    try {
      const response = await fetchWithTimeout(endpoint, {
        method: 'POST',
        headers: {
          Authorization: authHeader,
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: params.toString()
      });
      return response.ok;
    } catch (_e) {
      return false;
    }
  }

  async handleCallWebhook(eventType, sessionId, payload, { webhookBaseUrl, geminiApiKey }) {
    if (eventType === 'voice') {
      const twiml = buildInitialVoiceTwiml(sessionId, webhookBaseUrl);
      return { statusCode: 200, contentType: 'text/xml', body: twiml };
    }
    if (eventType === 'gather') {
      const params = new URLSearchParams(payload || '');
      const speechResult = params.get('SpeechResult') || '';
      const turnResult = await processTurn({
        sessionId,
        callerStatement: speechResult,
        geminiApiKey
      });
      const twiml = buildTurnTwiml(sessionId, turnResult, webhookBaseUrl);
      return { statusCode: 200, contentType: 'text/xml', body: twiml };
    }
    if (eventType === 'status') {
      const params = new URLSearchParams(payload || '');
      const callStatus = (params.get('CallStatus') || '').toLowerCase();
      if (callStatus === 'queued' || callStatus === 'initiated') {
        updateSessionStatus(sessionId, CALL_STATUSES.CALLING);
      } else if (callStatus === 'ringing') {
        updateSessionStatus(sessionId, CALL_STATUSES.RINGING);
      } else if (callStatus === 'in-progress') {
        updateSessionStatus(sessionId, CALL_STATUSES.CONNECTED);
      } else if (callStatus === 'completed') {
        await endSession({ sessionId, reason: 'provider_completed', geminiApiKey });
      } else if (['busy', 'no-answer', 'failed', 'canceled'].includes(callStatus)) {
        await endSession({ sessionId, reason: 'failed', geminiApiKey });
      }
      return { statusCode: 200, contentType: 'text/plain', body: 'OK' };
    }
    return { statusCode: 400, contentType: 'text/plain', body: 'Unknown webhook event type' };
  }
}

function mapTwilioStatus(rawStatus) {
  switch ((rawStatus || '').toLowerCase()) {
    case 'queued':
    case 'initiated':
      return CALL_STATUSES.CALLING;
    case 'ringing':
      return CALL_STATUSES.RINGING;
    case 'in-progress':
      return CALL_STATUSES.CONNECTED;
    case 'completed':
      return CALL_STATUSES.COMPLETED;
    case 'busy':
    case 'no-answer':
    case 'failed':
    case 'canceled':
      return CALL_STATUSES.FAILED;
    default:
      return null;
  }
}

function resolveTelephonyProvider() {
  const providerType = (process.env.TELEPHONY_PROVIDER || '').toLowerCase();
  const accountSid = process.env.TELEPHONY_ACCOUNT_SID || process.env.TWILIO_ACCOUNT_SID || '';
  const authToken = process.env.TELEPHONY_AUTH_TOKEN || process.env.TWILIO_AUTH_TOKEN || '';
  const fromNumber = process.env.TELEPHONY_FROM_NUMBER || process.env.TWILIO_FROM_NUMBER || '';
  const webhookBaseUrl = process.env.PUBLIC_WEBHOOK_URL || '';

  if (accountSid && authToken && fromNumber && (!providerType || providerType.includes('twilio'))) {
    return new TwilioTelephonyProvider({ accountSid, authToken, fromNumber, webhookBaseUrl });
  }

  return new UnconfiguredTelephonyProvider(process.env.TELEPHONY_PROVIDER || 'Not configured');
}

function getTelephonyConfig() {
  const provider = resolveTelephonyProvider();
  const isConfigured = provider.isConfigured();

  return {
    provider: provider.getName(),
    isConfigured,
    statusLabel: isConfigured ? 'CONFIGURED' : 'NOT CONFIGURED',
    accountSid: provider.getAccountIdMasked(),
    fromNumber: provider.getFromNumberMasked(),
    aiReady: !!process.env.GEMINI_API_KEY,
    voiceReady: isConfigured,
    publicWebhookConfigured: hasValidPublicWebhookUrl(),
    backendStatus: 'ONLINE',
    capabilities: provider.getCapabilities(),
    regulations: {
      traiCompliance: 'TRAI regulations require verified CLI and prohibit automated marketing calls to DND registry numbers. Caller ID spoofing is strictly prohibited.',
      aiTransparency: 'Dinthi AI always identifies itself as an artificial intelligence assistant when placing calls on behalf of the user.'
    }
  };
}

/**
 * Create a new Outbound CallSession and dispatch via TelephonyProvider when AI mode is selected.
 */
async function createSession({
  recipientName,
  phoneNumber,
  userName = 'the user',
  goal,
  notes = '',
  mode = 'AI',
  language = 'English (India)',
  persona = 'Professional',
  webhookBaseUrl = ''
}) {
  const sessionId = 'call_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7);
  const now = Date.now();
  const provider = resolveTelephonyProvider();
  const cleanUser = (userName || '').trim() || 'the user';
  const openingGreeting = `Hello, I'm Dinthi AI, an AI assistant calling on behalf of ${cleanUser}. Is this a good time to talk?`;

  const session = {
    id: sessionId,
    recipientName: (recipientName || '').trim() || phoneNumber,
    phoneNumber: (phoneNumber || '').trim(),
    userName: cleanUser,
    goal: (goal || '').trim() || 'General Inquiry',
    notes: (notes || '').trim(),
    mode: mode === 'HUMAN' ? 'HUMAN' : 'AI',
    language: language || 'English (India)',
    persona: persona || 'Professional',
    status: CALL_STATUSES.PREPARING,
    providerCallId: null,
    failureReason: null,
    silenceCount: 0,
    startTime: now,
    connectedTime: null,
    endTime: null,
    durationSeconds: 0,
    transcript: [],
    summary: null,
    telephonyConfig: getTelephonyConfig()
  };

  session.transcript.push({
    id: 'evt_init_' + now,
    speaker: 'system',
    text: `Outbound ${session.mode === 'AI' ? 'AI' : 'Human Direct'} call session created for ${session.recipientName} (${session.phoneNumber}).`,
    timestamp: now
  });

  if (session.mode === 'HUMAN') {
    session.status = CALL_STATUSES.CONNECTED;
    session.connectedTime = now;
    session.transcript.push({
      id: 'evt_human_' + now,
      speaker: 'system',
      text: 'Handed off to Android device telephone dialer so you can speak directly with the recipient.',
      timestamp: now + 10
    });
    sessions.set(sessionId, session);
    return session;
  }

  // AI Talk mode: MUST route through a real configured TelephonyProvider
  const result = await provider.createOutboundCall(session, webhookBaseUrl);
  if (!result.ok) {
    session.status = CALL_STATUSES.FAILED;
    session.endTime = Date.now();
    session.failureReason = result.error;
    session.transcript.push({
      id: 'evt_fail_' + Date.now(),
      speaker: 'system',
      text: `Outbound AI call could not be placed: ${result.error}`,
      timestamp: Date.now()
    });
    session.summary = {
      summary: `Outbound AI call to ${session.recipientName} failed before dialing: ${result.error}`,
      reasonForCall: session.goal,
      outcome: 'Failed',
      importantDetails: [`Mode: AI Talk`, `Status: ${result.notConfigured ? 'NOT CONFIGURED' : 'Failed'}`],
      decisions: [],
      actionItems: ['Configure real telephony provider credentials on the secure backend or use Human Talk mode via device dialer.'],
      sentiment: 'Neutral',
      durationSeconds: 0
    };
    sessions.set(sessionId, session);
    return session;
  }

  session.providerCallId = result.providerCallId;
  session.status = CALL_STATUSES.CALLING;
  session.openingGreeting = openingGreeting;
  sessions.set(sessionId, session);
  return session;
}

function getSession(sessionId) {
  const session = sessions.get(sessionId);
  if (!session) return null;

  if (session.connectedTime && !session.endTime) {
    session.durationSeconds = Math.max(0, Math.floor((Date.now() - session.connectedTime) / 1000));
  }
  return session;
}

async function getSessionWithSync(sessionId, geminiApiKey = '') {
  const session = getSession(sessionId);
  if (!session) return null;

  const isTerminal = session.status === CALL_STATUSES.COMPLETED || session.status === CALL_STATUSES.FAILED;
  if (!isTerminal && session.providerCallId) {
    const provider = resolveTelephonyProvider();
    if (provider.isConfigured()) {
      const liveStatus = await provider.getCallStatus(session.providerCallId);
      if (liveStatus?.mappedStatus && liveStatus.mappedStatus !== session.status) {
        if (liveStatus.mappedStatus === CALL_STATUSES.COMPLETED) {
          return await endSession({ sessionId, reason: 'provider_completed', geminiApiKey });
        } else if (liveStatus.mappedStatus === CALL_STATUSES.FAILED) {
          return await endSession({ sessionId, reason: 'failed', geminiApiKey });
        } else if (session.status === CALL_STATUSES.CALLING || session.status === CALL_STATUSES.RINGING) {
          updateSessionStatus(sessionId, liveStatus.mappedStatus);
        }
      }
    }
  }
  return getSession(sessionId);
}

async function handleWebhook(eventType, sessionId, payload, context) {
  const provider = resolveTelephonyProvider();
  return provider.handleCallWebhook(eventType, sessionId, payload, context);
}

function updateSessionStatus(sessionId, status, detail = '') {
  const session = sessions.get(sessionId);
  if (!session) return null;

  session.status = status;
  if (status === CALL_STATUSES.CONNECTED && !session.connectedTime) {
    session.connectedTime = Date.now();
  }
  if (detail) {
    session.transcript.push({
      id: 'evt_st_' + Date.now(),
      speaker: 'system',
      text: detail,
      timestamp: Date.now()
    });
  }
  return session;
}

async function callGeminiREST(prompt, apiKey, jsonMode = true) {
  const modelName = 'gemini-3.5-flash-lite';
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent?key=${apiKey}`;

  const body = {
    contents: [{ role: 'user', parts: [{ text: prompt }] }],
    generationConfig: {
      temperature: 0.3
    }
  };

  if (jsonMode) {
    body.generationConfig.responseMimeType = 'application/json';
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 12000);

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: controller.signal
    });
    clearTimeout(timer);

    if (!response.ok) {
      const errText = await response.text();
      throw new Error(`Gemini API error ${response.status}: ${String(errText || '').slice(0, 300)}`);
    }

    const data = await response.json();
    return data.candidates?.[0]?.content?.parts?.[0]?.text || '';
  } catch (err) {
    clearTimeout(timer);
    throw err;
  }
}

/**
 * Process a real recipient speech turn via Gemini
 */
async function processTurn({ sessionId, callerStatement, geminiApiKey }) {
  const session = sessions.get(sessionId);
  if (!session) {
    throw new Error('Session not found: ' + sessionId);
  }

  const now = Date.now();
  const cleanSpeech = (callerStatement || '').trim();

  if (!cleanSpeech) {
    session.silenceCount += 1;
    if (session.silenceCount >= 2) {
      const endMsg = 'I did not hear a response, so I will end the call now. Have a good day.';
      session.transcript.push({
        id: 'evt_' + now + '_ai',
        speaker: 'dinthi_ai',
        text: endMsg,
        timestamp: now
      });
      session.status = CALL_STATUSES.ENDING;
      return { replyText: endMsg, intent: 'Silence Hangup', actionItem: null, shouldEndCall: true };
    }
    const promptMsg = 'Hello? Are you still there?';
    session.transcript.push({
      id: 'evt_' + now + '_ai',
      speaker: 'dinthi_ai',
      text: promptMsg,
      timestamp: now
    });
    session.status = CALL_STATUSES.AI_SPEAKING;
    return { replyText: promptMsg, intent: 'Silence Check', actionItem: null, shouldEndCall: false };
  }

  session.silenceCount = 0;
  session.transcript.push({
    id: 'evt_' + now + '_rec',
    speaker: 'recipient',
    text: cleanSpeech,
    timestamp: now
  });
  session.status = CALL_STATUSES.PROCESSING;

  if (!geminiApiKey) {
    throw new Error('Gemini API key is not configured on the secure backend.');
  }

  const transcriptHistory = session.transcript
    .filter(t => t.speaker === 'recipient' || t.speaker === 'dinthi_ai')
    .map(t => `${t.speaker === 'dinthi_ai' ? 'Dinthi AI' : session.recipientName}: ${t.text}`)
    .join('\n');

  const prompt = `You are Dinthi AI, an intelligent, polite, and helpful phone assistant making an outbound call on behalf of ${session.userName}.

CRITICAL IDENTITY & COMPLIANCE RULES:
1. You are Dinthi AI. Never claim to be human or impersonate ${session.userName}.
2. Recipient: ${session.recipientName} (${session.phoneNumber})
3. Call Purpose / Goal: "${session.goal}"
4. Additional User Instructions: "${session.notes || 'None'}"
5. Spoken Language Preference: ${session.language} (If Hinglish or Hindi is selected, respond naturally in that style.)
6. Persona: ${session.persona}

Conversation so far:
${transcriptHistory}

Latest statement by recipient:
"${cleanSpeech}"

TASK:
Produce the next spoken response for Dinthi AI.
Keep it natural, polite, and concise (1-2 sentences) suitable for phone audio. Do not output markdown.

Output valid JSON only:
{
  "replyText": "Spoken text for Dinthi AI to say next",
  "intent": "Short intent label",
  "actionItem": "Action item if mentioned or null",
  "shouldEndCall": boolean
}`;

  const rawText = await callGeminiREST(prompt, geminiApiKey, true);
  let parsed;
  try {
    parsed = JSON.parse(rawText);
  } catch (e) {
    parsed = {
      replyText: rawText.trim(),
      intent: 'Conversation',
      actionItem: null,
      shouldEndCall: false
    };
  }

  session.transcript.push({
    id: 'evt_' + (now + 200) + '_ai',
    speaker: 'dinthi_ai',
    text: parsed.replyText || 'Thank you for speaking with me.',
    timestamp: now + 200
  });

  session.status = parsed.shouldEndCall ? CALL_STATUSES.ENDING : CALL_STATUSES.AI_SPEAKING;
  return parsed;
}

/**
 * Generate TwiML for initial call pickup with barge-in and Indian language support
 */
function buildInitialVoiceTwiml(sessionId, webhookBaseUrl) {
  const session = sessions.get(sessionId);
  if (!session) {
    return `<?xml version="1.0" encoding="UTF-8"?><Response><Hangup/></Response>`;
  }

  const greeting = session.openingGreeting || `Hello, I'm Dinthi AI, an AI assistant calling on behalf of ${session.userName}. Is this a good time to talk?`;
  const speechLang = mapSpeechLanguageCode(session.language);

  session.status = CALL_STATUSES.AI_SPEAKING;
  if (!session.connectedTime) session.connectedTime = Date.now();
  session.transcript.push({
    id: 'evt_greet_' + Date.now(),
    speaker: 'dinthi_ai',
    text: greeting,
    timestamp: Date.now()
  });

  const actionUrl = `${webhookBaseUrl.replace(/\/+$/, '')}/api/telephony/webhook/gather/${session.id}`;
  return `<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Gather input="speech" bargeIn="true" speechTimeout="auto" language="${escapeXml(speechLang)}" action="${escapeXml(actionUrl)}" method="POST">
    <Say language="${escapeXml(speechLang)}">${escapeXml(greeting)}</Say>
    <Pause length="1"/>
  </Gather>
  <Redirect method="POST">${escapeXml(actionUrl)}</Redirect>
</Response>`;
}

/**
 * Generate TwiML for subsequent speech turns
 */
function buildTurnTwiml(sessionId, turnResult, webhookBaseUrl) {
  const session = sessions.get(sessionId);
  const speechLang = mapSpeechLanguageCode(session?.language);
  const replyText = turnResult?.replyText || 'Thank you. Goodbye.';

  if (turnResult?.shouldEndCall) {
    return `<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Say language="${escapeXml(speechLang)}">${escapeXml(replyText)}</Say>
  <Pause length="1"/>
  <Hangup/>
</Response>`;
  }

  if (session) session.status = CALL_STATUSES.LISTENING;
  const actionUrl = `${webhookBaseUrl.replace(/\/+$/, '')}/api/telephony/webhook/gather/${sessionId}`;
  return `<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Gather input="speech" bargeIn="true" speechTimeout="auto" language="${escapeXml(speechLang)}" action="${escapeXml(actionUrl)}" method="POST">
    <Say language="${escapeXml(speechLang)}">${escapeXml(replyText)}</Say>
  </Gather>
  <Redirect method="POST">${escapeXml(actionUrl)}</Redirect>
</Response>`;
}

/**
 * End session, hang up provider call if active, and build post-call summary from actual transcript
 */
async function endSession({ sessionId, reason = 'user_ended', geminiApiKey }) {
  const session = sessions.get(sessionId);
  if (!session) return null;

  if (session.status === CALL_STATUSES.COMPLETED || session.status === CALL_STATUSES.FAILED) {
    return session;
  }

  session.status = CALL_STATUSES.ENDING;
  const provider = resolveTelephonyProvider();
  if (session.providerCallId && provider.isConfigured()) {
    await provider.endCall(session.providerCallId).catch(() => {});
  }

  const now = Date.now();
  session.status = reason === 'failed' ? CALL_STATUSES.FAILED : CALL_STATUSES.COMPLETED;
  session.endTime = now;
  if (session.connectedTime) {
    session.durationSeconds = Math.max(1, Math.floor((now - session.connectedTime) / 1000));
  } else {
    session.durationSeconds = 0;
  }

  session.transcript.push({
    id: 'evt_end_' + now,
    speaker: 'system',
    text: `Call ended (${reason}). Duration: ${session.durationSeconds}s.`,
    timestamp: now
  });

  const spokenLines = session.transcript.filter(t => t.speaker === 'recipient' || t.speaker === 'dinthi_ai');

  if (geminiApiKey && spokenLines.length > 0) {
    try {
      const conversationText = spokenLines
        .map(t => `${t.speaker === 'dinthi_ai' ? 'Dinthi AI' : session.recipientName}: ${t.text}`)
        .join('\n');

      const prompt = `Analyze this outbound phone call conducted by Dinthi AI on behalf of ${session.userName}. Use ONLY facts present in the transcript below; do NOT invent details.

Recipient: ${session.recipientName} (${session.phoneNumber})
Call Goal: ${session.goal}
User Instructions: ${session.notes}
Duration: ${session.durationSeconds} seconds

Transcript:
${conversationText}

Output strictly valid JSON:
{
  "summary": "Concise executive summary based strictly on the transcript",
  "reasonForCall": "${session.goal}",
  "outcome": "${session.status === CALL_STATUSES.FAILED ? 'Failed' : 'Completed'}",
  "importantDetails": ["Key detail or fact actually shared during the call"],
  "decisions": ["Decision or agreement actually stated"],
  "actionItems": ["Follow-up action item based on call"],
  "sentiment": "Positive"
}`;

      const rawText = await callGeminiREST(prompt, geminiApiKey, true);
      const parsed = JSON.parse(rawText);
      session.summary = {
        summary: parsed.summary || 'Outbound call completed.',
        reasonForCall: parsed.reasonForCall || session.goal,
        outcome: parsed.outcome || (session.status === CALL_STATUSES.FAILED ? 'Failed' : 'Completed'),
        importantDetails: parsed.importantDetails || [],
        decisions: parsed.decisions || [],
        actionItems: parsed.actionItems || [],
        sentiment: parsed.sentiment || 'Neutral',
        durationSeconds: session.durationSeconds
      };
    } catch (e) {
      console.error('Failed to generate summary with Gemini:', e);
      session.summary = buildActualSummaryFallback(session);
    }
  } else {
    session.summary = buildActualSummaryFallback(session);
  }

  return session;
}

function buildActualSummaryFallback(session) {
  const isHuman = session.mode === 'HUMAN';
  const isFailed = session.status === CALL_STATUSES.FAILED;
  const summaryText = isFailed
    ? `Outbound call to ${session.recipientName} could not connect.`
    : isHuman
      ? `Direct human call via device dialer to ${session.recipientName} regarding "${session.goal}".`
      : `Outbound AI call session to ${session.recipientName} ended (${session.durationSeconds}s).`;

  return {
    summary: summaryText,
    reasonForCall: session.goal,
    outcome: isFailed ? 'Failed' : 'Completed',
    importantDetails: [
      `Call Mode: ${isHuman ? 'Human Talk (Device Dialer)' : 'AI Talk'}`,
      `Actual Duration: ${session.durationSeconds}s`
    ],
    decisions: [],
    actionItems: session.notes ? [`Note: ${session.notes}`] : [],
    sentiment: 'Neutral',
    durationSeconds: session.durationSeconds
  };
}

module.exports = {
  CALL_STATUSES,
  TelephonyProvider,
  TwilioTelephonyProvider,
  UnconfiguredTelephonyProvider,
  resolveTelephonyProvider,
  getTelephonyConfig,
  normalizeWebhookBaseUrl,
  isDisallowedWebhookHost,
  hasValidPublicWebhookUrl,
  verifyTwilioSignature,
  createSession,
  getSession,
  getSessionWithSync,
  updateSessionStatus,
  handleWebhook,
  processTurn,
  buildInitialVoiceTwiml,
  buildTurnTwiml,
  endSession
};
