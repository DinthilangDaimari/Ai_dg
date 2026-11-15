const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { once } = require('node:events');
const serverModule = require('./server');
const telephony = require('./telephony');

async function startTestServer() {
  const server = serverModule.createAppServer();
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  const address = server.address();
  return {
    server,
    baseUrl: `http://127.0.0.1:${address.port}`
  };
}

async function getAuthToken(baseUrl) {
  const res = await fetch(`${baseUrl}/api/auth/session`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      appId: 'com.aistudio.dinthiai.wxyvqa',
      installId: 'test-install-1'
    })
  });
  assert.equal(res.status, 200);
  const data = await res.json();
  assert.ok(data.token);
  return data.token;
}

function signTwilioPayload(fullUrl, formBody, authToken) {
  const params = new URLSearchParams(formBody);
  const sortedKeys = Array.from(new Set(params.keys())).sort();
  let data = fullUrl;
  for (const key of sortedKeys) {
    for (const val of params.getAll(key)) {
      data += key + val;
    }
  }
  return crypto.createHmac('sha1', authToken).update(data, 'utf8').digest('base64');
}

test('getPort prioritizes process.env.PORT over fallback port variables', () => {
  const prevPort = process.env.PORT;
  const prevAppPort = process.env.APP_PORT;
  process.env.PORT = '8080';
  process.env.APP_PORT = '3001';
  assert.equal(serverModule.getPort(), 8080);

  delete process.env.PORT;
  assert.equal(serverModule.getPort(), 3001);

  if (prevPort !== undefined) process.env.PORT = prevPort; else delete process.env.PORT;
  if (prevAppPort !== undefined) process.env.APP_PORT = prevAppPort; else delete process.env.APP_PORT;
});

test('GET /health returns public readiness without exposing secret keys', async () => {
  const { server, baseUrl } = await startTestServer();
  try {
    const res = await fetch(`${baseUrl}/health`);
    assert.equal(res.status, 200);
    const body = await res.json();
    assert.equal(body.status, 'ok');
    assert.equal(typeof body.geminiConfigured, 'boolean');
    assert.equal(typeof body.telephonyConfigured, 'boolean');
    assert.equal(typeof body.publicWebhookConfigured, 'boolean');
    assert.equal(body.GEMINI_API_KEY, undefined);
    assert.equal(body.TWILIO_AUTH_TOKEN, undefined);
    assert.equal(body.TWILIO_ACCOUNT_SID, undefined);
  } finally {
    server.close();
  }
});

test('Production CORS allows GitHub Pages origin and blocks unknown origins', async () => {
  const prevNodeEnv = process.env.NODE_ENV;
  process.env.NODE_ENV = 'production';
  const { server, baseUrl } = await startTestServer();
  try {
    const okPreflight = await fetch(`${baseUrl}/api/telephony/call`, {
      method: 'OPTIONS',
      headers: { Origin: 'https://dinthilangdaimari.github.io' }
    });
    assert.equal(okPreflight.status, 204);
    assert.equal(
      okPreflight.headers.get('access-control-allow-origin'),
      'https://dinthilangdaimari.github.io'
    );

    const badPreflight = await fetch(`${baseUrl}/api/telephony/call`, {
      method: 'OPTIONS',
      headers: { Origin: 'https://unauthorized-site.example.com' }
    });
    assert.equal(badPreflight.status, 403);
    assert.equal(badPreflight.headers.get('access-control-allow-origin'), null);

    const badApiCall = await fetch(`${baseUrl}/api/telephony/status`, {
      method: 'GET',
      headers: { Origin: 'https://unauthorized-site.example.com' }
    });
    assert.equal(badApiCall.status, 403);
  } finally {
    server.close();
    if (prevNodeEnv !== undefined) process.env.NODE_ENV = prevNodeEnv; else delete process.env.NODE_ENV;
  }
});

test('GitHub Pages URL is rejected as PUBLIC_WEBHOOK_URL / Twilio webhook host', () => {
  const prevWebhook = process.env.PUBLIC_WEBHOOK_URL;
  process.env.PUBLIC_WEBHOOK_URL = 'https://dinthilangdaimari.github.io/Dinthi.github.io/';
  assert.equal(telephony.hasValidPublicWebhookUrl(), false);
  assert.equal(serverModule.resolveWebhookBaseUrl({ headers: { host: 'dinthilangdaimari.github.io' } }), '');
  if (prevWebhook !== undefined) process.env.PUBLIC_WEBHOOK_URL = prevWebhook; else delete process.env.PUBLIC_WEBHOOK_URL;
});

test('Outbound call lifecycle routes validate inputs and execute cleanly', async () => {
  const { server, baseUrl } = await startTestServer();
  try {
    const token = await getAuthToken(baseUrl);
    const authHeaders = {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    };

    const badJson = await fetch(`${baseUrl}/api/telephony/call`, {
      method: 'POST',
      headers: authHeaders,
      body: '{invalid-json'
    });
    assert.equal(badJson.status, 400);

    const badPhone = await fetch(`${baseUrl}/api/telephony/call`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({ phoneNumber: 'not-a-phone', mode: 'HUMAN' })
    });
    assert.equal(badPhone.status, 400);

    const badMode = await fetch(`${baseUrl}/api/telephony/call`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({ phoneNumber: '+919876543210', mode: 'INVALID' })
    });
    assert.equal(badMode.status, 400);

    const createRes = await fetch(`${baseUrl}/api/telephony/call`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({
        recipientName: 'Dr. Clinic',
        phoneNumber: '+91 98765-43210',
        userName: 'Alex',
        goal: 'Schedule checkup',
        mode: 'HUMAN'
      })
    });
    assert.equal(createRes.status, 201);
    const created = await createRes.json();
    const sessionId = created.session.id;
    assert.ok(sessionId);

    const getRes = await fetch(`${baseUrl}/api/telephony/session/${sessionId}`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    assert.equal(getRes.status, 200);

    const badStatusRes = await fetch(`${baseUrl}/api/telephony/session/${sessionId}/status`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({ status: 'UNKNOWN_STATE' })
    });
    assert.equal(badStatusRes.status, 400);

    const statusRes = await fetch(`${baseUrl}/api/telephony/session/${sessionId}/status`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({ status: 'CONNECTED' })
    });
    assert.equal(statusRes.status, 200);

    const turnRes = await fetch(`${baseUrl}/api/telephony/session/${sessionId}/turn`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({ text: '' })
    });
    assert.equal(turnRes.status, 200);

    // Unconfigured provider returns 503 Hangup TwiML
    const unconfiguredVoice = await fetch(`${baseUrl}/api/telephony/webhook/voice/${sessionId}`, {
      method: 'POST'
    });
    assert.equal(unconfiguredVoice.status, 503);
    assert.ok((await unconfiguredVoice.text()).includes('<Hangup/>'));

    // End call: POST /api/telephony/session/:id/end
    const endRes = await fetch(`${baseUrl}/api/telephony/session/${sessionId}/end`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({ reason: 'user_ended' })
    });
    assert.equal(endRes.status, 200);
    const ended = await endRes.json();
    assert.equal(ended.status, 'ended');
    assert.equal(ended.session.status, 'COMPLETED');
  } finally {
    server.close();
  }
});

test('Twilio webhook validation enforces X-Twilio-Signature and AccountSid in production', async () => {
  const prevEnv = {
    NODE_ENV: process.env.NODE_ENV,
    PUBLIC_WEBHOOK_URL: process.env.PUBLIC_WEBHOOK_URL,
    TELEPHONY_PROVIDER: process.env.TELEPHONY_PROVIDER,
    TWILIO_ACCOUNT_SID: process.env.TWILIO_ACCOUNT_SID,
    TWILIO_AUTH_TOKEN: process.env.TWILIO_AUTH_TOKEN,
    TWILIO_FROM_NUMBER: process.env.TWILIO_FROM_NUMBER,
    TWILIO_VALIDATE_SIGNATURES: process.env.TWILIO_VALIDATE_SIGNATURES
  };

  const publicUrl = 'https://dinthi-ai-backend.example.com';
  const accountSid = 'AC00000000000000000000000000000000';
  const authToken = 'test_twilio_auth_token_12345';

  process.env.NODE_ENV = 'production';
  process.env.PUBLIC_WEBHOOK_URL = publicUrl;
  process.env.TELEPHONY_PROVIDER = 'twilio';
  process.env.TWILIO_ACCOUNT_SID = accountSid;
  process.env.TWILIO_AUTH_TOKEN = authToken;
  process.env.TWILIO_FROM_NUMBER = '+919800000000';
  delete process.env.TWILIO_VALIDATE_SIGNATURES;

  const { server, baseUrl } = await startTestServer();
  try {
    const token = await getAuthToken(baseUrl);
    const sessionRes = await fetch(`${baseUrl}/api/telephony/call`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({
        recipientName: 'Support Desk',
        phoneNumber: '+919811111111',
        userName: 'Dinthi User',
        goal: 'Verify webhook security',
        mode: 'HUMAN'
      })
    });
    const sessionId = (await sessionRes.json()).session.id;

    // 1. Missing signature rejected with 403
    const missingSigRes = await fetch(`${baseUrl}/api/telephony/webhook/status/${sessionId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `AccountSid=${accountSid}&CallStatus=ringing`
    });
    assert.equal(missingSigRes.status, 403);

    // 2. Wrong AccountSid rejected with 403
    const wrongAccountBody = 'AccountSid=AC99999999999999999999999999999999&CallStatus=ringing';
    const wrongAccountRes = await fetch(`${baseUrl}/api/telephony/webhook/status/${sessionId}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'X-Twilio-Signature': signTwilioPayload(
          `${publicUrl}/api/telephony/webhook/status/${sessionId}`,
          wrongAccountBody,
          authToken
        )
      },
      body: wrongAccountBody
    });
    assert.equal(wrongAccountRes.status, 403);

    // 3. Valid Twilio signature & matching AccountSid accepted across /voice, /gather, /status
    const voiceBody = `AccountSid=${accountSid}&CallSid=CA11111111111111111111111111111111`;
    const voiceRes = await fetch(`${baseUrl}/api/telephony/webhook/voice/${sessionId}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'X-Twilio-Signature': signTwilioPayload(
          `${publicUrl}/api/telephony/webhook/voice/${sessionId}`,
          voiceBody,
          authToken
        )
      },
      body: voiceBody
    });
    assert.equal(voiceRes.status, 200);
    assert.ok((await voiceRes.text()).includes('Dinthi AI'));

    const gatherBody = `AccountSid=${accountSid}&SpeechResult=`;
    const gatherRes = await fetch(`${baseUrl}/api/telephony/webhook/gather/${sessionId}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'X-Twilio-Signature': signTwilioPayload(
          `${publicUrl}/api/telephony/webhook/gather/${sessionId}`,
          gatherBody,
          authToken
        )
      },
      body: gatherBody
    });
    assert.equal(gatherRes.status, 200);

    const statusBody = `AccountSid=${accountSid}&CallStatus=completed`;
    const statusRes = await fetch(`${baseUrl}/api/telephony/webhook/status/${sessionId}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'X-Twilio-Signature': signTwilioPayload(
          `${publicUrl}/api/telephony/webhook/status/${sessionId}`,
          statusBody,
          authToken
        )
      },
      body: statusBody
    });
    assert.equal(statusRes.status, 200);
  } finally {
    server.close();
    for (const [k, v] of Object.entries(prevEnv)) {
      if (v !== undefined) process.env[k] = v;
      else delete process.env[k];
    }
  }
});

test('Direct APK download endpoints serve Dinthi-AI.apk with proper headers, Range support, and metadata', async () => {
  const { server, baseUrl } = await startTestServer();
  try {
    // 1. Metadata endpoint with GitHub Pages CORS
    const infoRes = await fetch(`${baseUrl}/api/app/apk-info`, {
      headers: { Origin: 'https://dinthilangdaimari.github.io' }
    });
    assert.equal(infoRes.status, 200);
    assert.equal(
      infoRes.headers.get('access-control-allow-origin'),
      'https://dinthilangdaimari.github.io'
    );
    const info = await infoRes.json();
    assert.equal(info.available, true);
    assert.equal(info.filename, 'Dinthi-AI.apk');
    assert.equal(info.mimeType, 'application/vnd.android.package-archive');
    assert.ok(info.sizeBytes > 1000);
    assert.match(info.sha256, /^[a-f0-9]{64}$/);

    // 2. HEAD /download/dinthi-ai.apk
    const headRes = await fetch(`${baseUrl}/download/dinthi-ai.apk`, { method: 'HEAD' });
    assert.equal(headRes.status, 200);
    assert.equal(headRes.headers.get('content-type'), 'application/vnd.android.package-archive');
    assert.equal(headRes.headers.get('content-disposition'), 'attachment; filename="Dinthi-AI.apk"');
    assert.equal(headRes.headers.get('accept-ranges'), 'bytes');
    assert.equal(Number(headRes.headers.get('content-length')), info.sizeBytes);

    // 3. Range GET (first 4 bytes of APK must be ZIP/APK header "PK\x03\x04")
    const rangeRes = await fetch(`${baseUrl}/download/apk`, {
      headers: { Range: 'bytes=0-3' }
    });
    assert.equal(rangeRes.status, 206);
    assert.equal(rangeRes.headers.get('content-range'), `bytes 0-3/${info.sizeBytes}`);
    const magic = new Uint8Array(await rangeRes.arrayBuffer());
    assert.deepEqual(Array.from(magic), [0x50, 0x4b, 0x03, 0x04]);

    // 4. Download landing page (/download and browser GET /)
    const pageRes = await fetch(`${baseUrl}/download`);
    assert.equal(pageRes.status, 200);
    const html = await pageRes.text();
    assert.ok(html.includes('/download/dinthi-ai.apk'));
    assert.ok(html.includes(info.sha256));

    const browserRootRes = await fetch(`${baseUrl}/`, {
      headers: { Accept: 'text/html,application/xhtml+xml' }
    });
    assert.equal(browserRootRes.status, 200);
    assert.match(browserRootRes.headers.get('content-type') || '', /text\/html/);
    assert.ok((await browserRootRes.text()).includes('/download/dinthi-ai.apk'));

    const apiRootRes = await fetch(`${baseUrl}/`, {
      headers: { Accept: 'application/json' }
    });
    assert.equal(apiRootRes.status, 200);
    assert.equal((await apiRootRes.json()).status, 'ok');
  } finally {
    server.close();
  }
});
