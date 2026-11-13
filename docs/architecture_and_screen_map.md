# Dinthi AI — AI Call Screening & Call Assistant Architecture

## 1. Executive Summary & Product Vision
Dinthi AI is a native Android AI call screening and call-assistance system. It monitors supported incoming calls, screens unknown callers, transcribes dialog in real-time, extracts actionable post-call summaries, and gives the user full control to answer, join, or disconnect calls with zero secret recording and zero client-side API keys.

---

## 2. Screen Map & Navigation Hierarchy

```
[Launcher] ───► [DashboardScreen (Home)]
                     │
                     ├──► [LiveScreeningScreen (Active Incoming / Screened Call)]
                     │         ├─ Caller Profile & Spam Risk Badge
                     │         ├─ Live Dual-Bubble Transcript (Caller vs AI)
                     │         ├─ Real-Time Audio Waves & AI Speaking Indicator
                     │         ├─ Quick User Guidance Chips ("Ask who is calling", "Ask to call back")
                     │         └─ Controls: [Answer / Take Over Call] [Mute] [Decline / End]
                     │
                     ├──► [CallDetailScreen (Post-Call Intelligence Dossier)]
                     │         ├─ Executive Call Summary & Apparent Intent
                     │         ├─ Actionable Takeaways (Reminders, Dates, Addresses)
                     │         ├─ Action Buttons (Add to Calendar, Call Back, Save Contact, Share)
                     │         └─ Full Audio Playback (TTS) & Line-by-Line Transcript
                     │
                     ├──► [CallHistoryScreen (All Logged Calls)]
                     │         ├─ Filter Tabs: All, Screened by AI, Answered, Missed, Spam Blocked
                     │         ├─ Search by caller name, number, or summary keyword
                     │         └─ Batch management & Export options
                     │
                     ├──► [CallSimulatorScreen (Interactive Testing Sandbox)]
                     │         ├─ Scenario 1: Courier / Package Delivery Driver
                     │         ├─ Scenario 2: Doctor's Office / Clinic Reschedule
                     │         ├─ Scenario 3: Job Recruiter / Interview Scheduling
                     │         ├─ Scenario 4: Suspicious Bank Loan Telemarketer (Spam)
                     │         └─ Scenario 5: Live Mic / Custom Caller Simulation
                     │
                     └──► [SettingsScreen (Controls, Telecom & Languages)]
                               ├─ Screening Rules: Unknown callers, All calls, Suspected spam
                               ├─ Language Selector: English (India), Hindi, Bengali, Telugu, Tamil, Kannada, Marathi
                               ├─ AI Voice Persona: Courteous Professional, Direct & Firm, Warm Friendly
                               ├─ Custom Greeting Phrase
                               ├─ Android Telecom & Carrier Legal Transparency Guide
                               └─ Backend Server Status & Diagnostics
```

---

## 3. Core Functional Modules

### A. Telecom & Call Screening (`com.example.telecom`)
- **`DinthiCallScreeningService`**: Official Android `android.telecom.CallScreeningService` (API 24+) implementation. Inspects incoming call details, checks contact status against phone rules, and triggers the AI Screening pipeline.
- **`CallScreeningEngine`**: Handles incoming phone state events, binds simulation runs with real telephony broadcasts, and manages live audio/transcription loops.
- **Android Telecom Transparency**: Legitimate call interception respects Android 10+ privacy policies. Cellular audio stream isolation is handled ethically with user knowledge and no covert recording.

### B. AI Call Brain & Secure Backend (`/server` & `com.example.data.repository`)
- **Backend Proxy (`/server/server.js`)**:
  - Securely stores `GEMINI_API_KEY` on the server only. Zero keys in APK.
  - Endpoint `POST /api/screen/turn`: Generates conversational phone replies tailored for speech synthesis.
  - Endpoint `POST /api/screen/summary`: Generates structured post-call summaries, action items, intent, and sentiment.
  - Sliding-window rate limiting & client-token authentication.
- **Android Repository (`CallScreeningRepository`)**:
  - Manages live screening dialog flow.
  - Fallback offline generator if server is disconnected.

### C. Speech & Audio Synthesizer (`com.example.voice`)
- **`SpeechRecognizerManager`**: Real-time speech recognition for live caller transcription with active mic indicators and audio visualizer.
- **`CallAudioSynthesizer`**: Android `TextToSpeech` engine configured with multi-language Indian accents (English-IN, Hindi, etc.) for clear spoken responses to callers.

### D. Data Persistence & Room Database (`com.example.data.local`)
- **`CallLogEntity`**: Stores caller name, number, call type, duration, spam score, AI summary, category, timestamp, and actions.
- **`TranscriptEntity`**: Normalized message-by-message dialog history for every call.
- **`CallDao` & `AppDatabase`**: Reactive Kotlin Coroutines `Flow` for real-time UI synchronization.

---

## 4. Security & Privacy Guarantees
1. **Zero Secret API Keys in Client**: The APK contains no Gemini API key. All AI operations are proxied through the authenticated backend.
2. **No Covert Audio Recording**: Calls display clear visual indicators when screening. The caller is transparently informed they are speaking with Dinthi AI.
3. **Explicit Consent for Actions**: Post-call triggers (calendar events, contact saving, callback) require user confirmation before execution.
