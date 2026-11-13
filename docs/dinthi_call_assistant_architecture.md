# Dinthi AI — Complete Feature Architecture & Screen Map

## 1. Product Identity & Design Principles
- **Brand Identity:** Dinthi AI is an original, intelligent AI Call Assistant and Telephony Productivity suite designed for Indian mobile users and international callers.
- **Original Aesthetics:** High-contrast modern Material Design 3 with Electric Cyan (#00E5FF), Tech Violet (#7C4DFF), and Slate Charcoal surfaces. Distinctive icons, dual-speaker chat bubbles, dynamic waveform audio activity, and zero proprietary assets from other apps.
- **Telecom & Platform Realism:** Strict adherence to Android 10+ security models. Legitimate usage of `CallScreeningService`, `NotificationManager`, `AlarmManager`/WorkManager for scheduled calls, `MediaRecorder` for authorized microphone voice notes, and `Intent.ACTION_DIAL`/`ACTION_CALL`.

---

## 2. Complete Feature Matrix

| Feature | Android & Telecom Architecture | Dinthi AI Implementation |
|---|---|---|
| **1. AI Call Screening / Handling** | `CallScreeningService` + State Machine | Intercepts incoming calls; Dinthi AI answers autonomously via TTS & Gemini server proxy; handles caller dialogue. |
| **2. Live Call Transcription** | Dual-speaker real-time state stream | Streams live transcript bubbles distinguishing Caller, Dinthi AI, and User intervention with audio wave indicators. |
| **3. Automatic Post-Call Summaries** | Server Proxy Gemini API (`/api/screen/summary`) | Auto-generates structured summary, intent, urgency score, and bulleted takeaways on call hang-up. |
| **4. Call Recordings (Legal Compliance)** | Microphone Voice Memos + Synthesized Audio Playback | Clearly states Android 10+ cellular recording boundaries; provides high-fidelity speech-to-text transcript logs and local voice note recordings. |
| **5. Post-Call Actions** | Android Intents (`ACTION_DIAL`, `ACTION_INSERT` Calendar & Contacts) | One-tap confirmable action cards (Call Back, Add to Calendar, Save Contact, Block Spam, Note). |
| **6. One-Tap Join / Take Over** | Screening State Engine + Telecom handoff | One-tap takeover button disables AI voice and hands live audio to the user. |
| **7. Saved Contacts vs Unknown Screening** | `ContactsContract` resolver + `READ_CONTACTS` | Distinguishes known contacts from unknown numbers; configurable screening sensitivity. |
| **8. User-Controlled Screening Rules** | Room DB + SharedPreferences | Screening modes: *Unknown & Spam*, *All Calls*, *Spam Only*, *VIP Whitelist*. |
| **9. Multiple Indian-Language Support** | Gemini multi-lingual prompt + Android TTS | Supports English (India), Hindi, Bengali, Telugu, Tamil, Kannada, and Marathi. |
| **10. Ask Dinthi AI to Place a Call** | Assisted Outbound Call Engine | User dictates call intent (e.g. "Call Dr. Rao clinic to ask about slots"); Dinthi prepares brief & dials via intent. |
| **11. Schedule a Call for Later** | `ScheduledCallEntity` + Android `AlarmManager` | Schedule recipient, date/time, agenda/objective, and automatic reminder notification. |
| **12. Modify / Cancel Scheduled Calls** | Room DB + Reactive UI | View upcoming scheduled calls list, reschedule time, edit agenda, or cancel with 1 tap. |
| **13. Voice Notes & Memos** | `MediaRecorder` / Audio + AI Transcription | Record audio memos, transcribe via SpeechRecognizer/Gemini, and extract AI summary & action items. |
| **14. Read-Receipt Controls** | Notification & Read status preference | Toggle read markers on screened call summaries; explains carrier SMS/RCS read-receipt boundary. |
| **15. Enhanced Call History** | Room DB (`calls` table) + Search & Filter | Full history with duration, spam probability tag, category chips, search, and detail drill-down. |
| **16. Caller / Number Intelligence** | Telecom & Spam reputation scoring | Shows carrier/location estimate where available, contact status, spam risk score (0–100%), and call frequency. |
| **17. High-Priority Call Notifications** | Android `NotificationManager` Heads-Up | Posts notification with direct [View Summary] or [Call Back] for urgent calls. |
| **18. Privacy & Data Deletion Controls** | Local Room DAO deletion + Export | Per-call deletion, bulk call log purge, voice notes deletion, and zero cloud retention of user data. |

---

## 3. Screen Map & Navigation Hierarchy

```
MainNavigation
├── DashboardScreen (Primary Home)
│   ├── Assistant Active Master Toggle
│   ├── Quick Metrics (Screened, Spam Blocked, Time Saved)
│   ├── Quick Actions Bar:
│   │   ├── [Simulate Call] -> CallSimulatorScreen
│   │   ├── [New Outbound / Ask Dinthi] -> OutboundCallDialog
│   │   ├── [Schedule Call] -> ScheduleCallScreen
│   │   └── [Voice Note] -> VoiceNotesScreen
│   ├── Search & Filter Chips (All, Screened, Spam, Delivery, Healthcare, Recruiter)
│   └── Recent Calls List (Tap -> CallDetailScreen)
│
├── LiveScreeningScreen (Active Incoming Screening HUD)
│   ├── Caller Profile & Spam Risk Gauge
│   ├── Animated Audio Wave Visualizer
│   ├── Dual-Speaker Live Transcript (Caller, Dinthi AI, User Intervention)
│   ├── Quick Intervention Chips & Custom Prompts
│   ├── [Take Over Call] Button
│   └── [End Call / Hang Up] Button
│
├── CallDetailScreen (Post-Call Intelligence)
│   ├── Caller Overview & Urgency Badge
│   ├── Executive AI Summary & Key Takeaways
│   ├── Action Cards (Call Back, Add to Calendar, Save Contact, Block)
│   ├── Full Audio Replay (TTS Readout)
│   ├── Full Transcript with Turn Timestamps
│   └── Legal Telecom Disclosure Note
│
├── ScheduleCallScreen (Call Scheduling & Management)
│   ├── New Scheduled Call Creator (Contact, Number, Date/Time, Agenda)
│   ├── List of Upcoming Scheduled Calls
│   └── Edit / Reschedule / Cancel Actions
│
├── VoiceNotesScreen (Audio Memos & Transcripts)
│   ├── Live Voice Recorder (Microphone input)
│   ├── Voice Notes List with Duration & Timestamp
│   ├── AI Transcription & Action Item Extraction
│   └── Playback & Delete Controls
│
├── CallSimulatorScreen (Emulator Sandbox)
│   ├── 4 Pre-built Scenarios (Courier, Clinic, Recruiter, Loan Spam)
│   └── Custom Simulated Caller Builder
│
└── SettingsScreen (Preferences & Legal Disclosures)
    ├── Call Screening Policies (Unknown, All, Spam only)
    ├── Indian Language Selector (English, Hindi, Bengali, Telugu, Tamil, Kannada, Marathi)
    ├── Voice Persona & TTS Pitch/Speed
    ├── Outbound & Read-Receipt Settings
    ├── Notification Channels & Urgent Call Alerts
    ├── Privacy, Data Purge & Local Storage Controls
    └── Secure Server Proxy Status (Zero API Keys in APK verification)
```
