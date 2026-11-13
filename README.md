# Dinthi AI — Production Backend Deployment Guide

Dinthi AI has three distinct components:
1. **Android App (`app/`)**: Jetpack Compose client with Android Telecom call screening and outbound call controls.
2. **GitHub Pages Frontend (`https://dinthilangdaimari.github.io/Dinthi.github.io/`)**: Static web frontend hosted on GitHub Pages.
3. **Node.js Backend (`server/server.js`)**: Separate Node.js HTTPS backend handling Gemini AI calls, `/api/telephony/*` endpoints, and Twilio Programmable Voice webhooks.

---

## Architecture & Hosting Separation

> **Do NOT deploy `server/server.js` to GitHub Pages.**  
> GitHub Pages hosts **static frontend files only** (`https://dinthilangdaimari.github.io/Dinthi.github.io/`) and cannot run Node.js, handle Twilio HTTP `POST` webhooks, or protect server secrets (`GEMINI_API_KEY`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`).

Deploy the `server/` directory to any **Node.js-compatible HTTPS hosting service** (Render, Railway, Google Cloud Run, Fly.io, or Heroku):

```
GitHub Pages Frontend (https://dinthilangdaimari.github.io/Dinthi.github.io/)
Android App (app/)
        │
        │ HTTPS (Production CORS + Bearer Session Token)
        ▼
Node.js HTTPS Service (server/server.js listening on process.env.PORT)
        │
        ├── Google Gemini API (GEMINI_API_KEY, server-side only)
        └── Twilio Programmable Voice (server/telephony.js)
                │
                ▼
         Twilio Webhooks (PUBLIC_WEBHOOK_URL/api/telephony/webhook/*)
```

---

## Deployment-Ready Files
- `server/server.js` — Production HTTP server listening on `process.env.PORT`, with GitHub Pages CORS (`https://dinthilangdaimari.github.io`), request validation, Twilio webhook signature/AccountSid verification, security headers, and graceful shutdown.
- `server/telephony.js` — `TelephonyProvider` abstraction and `TwilioTelephonyProvider` webhook/call lifecycle handler (`github.io` and `localhost` are rejected for webhook URLs).
- `server/package.json` — `"engines": { "node": ">=18.0.0" }`, `"start": "node server.js"`, `"test": "node --test telephony.test.js server.test.js"`. Zero external npm dependencies.
- `server/Dockerfile` — Container build for Cloud Run, Railway, Fly.io, or Render Docker deployments.
- `server/Procfile` — Standard `web: node server.js` entrypoint for PaaS hosts.
- `server/.env.example` — Environment variable template with empty placeholders (zero real credentials).
- `.gitignore` — Excludes `.env`, `.env.*`, `server/.env`, `server/.env.*`, `*.pem`, `*.key`, `*.p12`, `*.jks`, and service-account JSONs.

---

## Required Environment Variables

Set these variables in your Node.js hosting provider's environment settings (**never commit `.env` or put secrets in Android/GitHub Pages code**):

| Variable | Required | Description |
|---|---|---|
| `PORT` | Yes (auto-injected by cloud hosts) | Server listening port from `process.env.PORT` (defaults to `3000` locally). |
| `NODE_ENV` | Yes (`production`) | Enables production CORS and automatic Twilio webhook signature verification when Twilio credentials are present. |
| `GEMINI_API_KEY` | Yes | Server-side Google Gemini API key. |
| `PUBLIC_WEBHOOK_URL` | Yes (for Twilio calls) | Public **HTTPS** base URL of this Node.js backend (e.g. `https://dinthi-ai-backend.onrender.com`). Must **not** be `github.io` or `localhost`. |
| `CORS_ALLOWED_ORIGINS` | Optional | Comma-separated allowed browser origins. `https://dinthilangdaimari.github.io` is allowed by default. |
| `TELEPHONY_PROVIDER` | Required for AI Talk | Set to `twilio` when live PSTN calling is configured. |
| `TWILIO_ACCOUNT_SID` | Required for AI Talk | Twilio Account SID (`AC...`). |
| `TWILIO_AUTH_TOKEN` | Required for AI Talk | Twilio Auth Token (also used to verify `X-Twilio-Signature` on incoming webhooks). |
| `TWILIO_FROM_NUMBER` | Required for AI Talk | Legitimate E.164 Twilio phone number. |
| `TWILIO_VALIDATE_SIGNATURES` | Optional | Automatically enabled when `NODE_ENV=production` with `PUBLIC_WEBHOOK_URL` + `TWILIO_AUTH_TOKEN`; set `true` to enforce in non-production or `false` to skip during tunnel testing. |
| `APP_CLIENT_TOKEN` | Optional | Optional static client bearer token; Android obtains ephemeral session tokens via `POST /api/auth/session`. |

> **Current Telephony Status:** Twilio is **NOT CONFIGURED** until real `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER`, and `PUBLIC_WEBHOOK_URL` values are set on the backend host.

---

## Startup Command & Deployment Steps

### Startup Command
- If your Node.js host uses `server/` as the **Root Directory** (recommended for Render/Railway/Heroku):
  ```bash
  npm start
  ```
- If deploying from repository root without setting Root Directory:
  ```bash
  node server/server.js
  ```
- Docker deploy (`server/Dockerfile`):
  ```bash
  docker build -t dinthi-ai-backend ./server
  docker run -p 8080:8080 -e PORT=8080 -e NODE_ENV=production -e GEMINI_API_KEY=... dinthi-ai-backend
  ```

### Step-by-Step Deployment (e.g. Render / Railway / Cloud Run)
1. Create a new Web Service connected to this repository, set **Root Directory** to `server` (or Dockerfile `server/Dockerfile`), and set Start Command to `npm start`.
2. Add the environment variables above (`NODE_ENV=production`, `GEMINI_API_KEY`, `PUBLIC_WEBHOOK_URL=https://<your-deployed-backend-domain>`, plus Twilio credentials when ready).
3. Deploy and verify the health endpoint:
   ```bash
   curl https://<your-deployed-backend-domain>/health
   ```
   Expected JSON: `{"status":"ok",...}`.

---

## Webhook URLs & Endpoints

### Public Health Check
- `GET /health`

### Telephony Session Endpoints (`/api/telephony/*`, Authenticated)
- `GET /api/telephony/status`
- `POST /api/telephony/call`
- `GET /api/telephony/session/:id`
- `POST /api/telephony/session/:id/status`
- `POST /api/telephony/session/:id/turn`
- `POST /api/telephony/session/:id/end`

### Required Twilio Webhook URLs
Dinthi AI automatically constructs per-session callback URLs from `PUBLIC_WEBHOOK_URL`:
- Voice answer (TwiML): `https://<your-deployed-backend-domain>/api/telephony/webhook/voice/:id`
- Speech gather turn: `https://<your-deployed-backend-domain>/api/telephony/webhook/gather/:id`
- Call status callback: `https://<your-deployed-backend-domain>/api/telephony/webhook/status/:id`

If you configure a fallback/inbound Voice webhook in your Twilio Console phone number settings, use your Node.js backend HTTPS URL — **never** `https://dinthilangdaimari.github.io/Dinthi.github.io/`.

---

## Running Tests

### Backend Tests
```bash
npm --prefix server test
```

### Android Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```
