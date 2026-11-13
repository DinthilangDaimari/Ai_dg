const test = require('node:test');
const assert = require('node:assert/strict');
const telephony = require('./telephony');

test('TelephonyProvider base class defines required abstraction methods', () => {
  const proto = telephony.TelephonyProvider.prototype;
  assert.equal(typeof proto.createOutboundCall, 'function');
  assert.equal(typeof proto.getCallStatus, 'function');
  assert.equal(typeof proto.endCall, 'function');
  assert.equal(typeof proto.handleCallWebhook, 'function');
});

test('getTelephonyConfig reports NOT CONFIGURED when provider credentials are missing', () => {
  const prevSid = process.env.TELEPHONY_ACCOUNT_SID;
  const prevTok = process.env.TELEPHONY_AUTH_TOKEN;
  const prevFrom = process.env.TELEPHONY_FROM_NUMBER;
  delete process.env.TELEPHONY_ACCOUNT_SID;
  delete process.env.TELEPHONY_AUTH_TOKEN;
  delete process.env.TELEPHONY_FROM_NUMBER;

  const config = telephony.getTelephonyConfig();
  assert.equal(config.isConfigured, false);
  assert.equal(config.statusLabel, 'NOT CONFIGURED');

  if (prevSid) process.env.TELEPHONY_ACCOUNT_SID = prevSid;
  if (prevTok) process.env.TELEPHONY_AUTH_TOKEN = prevTok;
  if (prevFrom) process.env.TELEPHONY_FROM_NUMBER = prevFrom;
});

test('AI Talk call fails honestly with NOT CONFIGURED when no telephony provider is set', async () => {
  const session = await telephony.createSession({
    recipientName: 'Clinic Desk',
    phoneNumber: '+919876543210',
    userName: 'Alex',
    goal: 'Confirm appointment',
    mode: 'AI'
  });

  assert.equal(session.status, telephony.CALL_STATUSES.FAILED);
  assert.ok(session.failureReason.includes('NOT CONFIGURED'));
  assert.equal(session.summary.outcome, 'Failed');
  assert.equal(session.summary.reasonForCall, 'Confirm appointment');
  assert.ok(Array.isArray(session.summary.importantDetails));
});

test('Human Talk call creates connected session and completes with real summary fields', async () => {
  const session = await telephony.createSession({
    recipientName: 'Clinic Desk',
    phoneNumber: '+919876543210',
    userName: 'Alex',
    goal: 'Speak directly about test report',
    notes: 'Ask for PDF copy',
    mode: 'HUMAN'
  });

  assert.equal(session.status, telephony.CALL_STATUSES.CONNECTED);
  assert.equal(session.mode, 'HUMAN');

  const ended = await telephony.endSession({
    sessionId: session.id,
    reason: 'human_call_completed',
    geminiApiKey: ''
  });

  assert.equal(ended.status, telephony.CALL_STATUSES.COMPLETED);
  assert.ok(ended.durationSeconds >= 1);
  assert.equal(ended.summary.reasonForCall, 'Speak directly about test report');
  assert.ok(Array.isArray(ended.summary.importantDetails));
  assert.ok(Array.isArray(ended.summary.decisions));
  assert.ok(Array.isArray(ended.summary.actionItems));
  assert.ok(ended.summary.actionItems.some(item => item.includes('Ask for PDF copy')));
});

test('TwilioTelephonyProvider.handleCallWebhook manages call lifecycle and AI disclosure', async () => {
  const provider = new telephony.TwilioTelephonyProvider({
    accountSid: 'AC00000000000000000000000000000000',
    authToken: 'test_token_only',
    fromNumber: '+919800000000',
    webhookBaseUrl: 'https://example.run.app'
  });

  // Create a session manually in map via HUMAN then switch mode to verify webhook state machine
  const session = await telephony.createSession({
    recipientName: 'Support',
    phoneNumber: '+919811111111',
    userName: 'Dinthi User',
    goal: 'Verify pickup',
    mode: 'HUMAN'
  });

  await provider.handleCallWebhook('status', session.id, 'CallStatus=ringing', {
    webhookBaseUrl: 'https://example.run.app',
    geminiApiKey: ''
  });
  assert.equal(telephony.getSession(session.id).status, telephony.CALL_STATUSES.RINGING);

  const voiceResp = await provider.handleCallWebhook('voice', session.id, '', {
    webhookBaseUrl: 'https://example.run.app',
    geminiApiKey: ''
  });
  assert.equal(voiceResp.statusCode, 200);
  assert.ok(voiceResp.body.includes('Dinthi AI'));
  assert.ok(voiceResp.body.includes('AI assistant calling on behalf of Dinthi User'));
  assert.equal(telephony.getSession(session.id).status, telephony.CALL_STATUSES.AI_SPEAKING);

  await provider.handleCallWebhook('status', session.id, 'CallStatus=completed', {
    webhookBaseUrl: 'https://example.run.app',
    geminiApiKey: ''
  });
  assert.equal(telephony.getSession(session.id).status, telephony.CALL_STATUSES.COMPLETED);
});
