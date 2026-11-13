package com.example.data.model

data class ScreenTurnResult(
    val replyText: String,
    val intent: String,
    val spamScore: Int,
    val isSpam: Boolean,
    val suggestedQuickReplies: List<String>
)

data class RecommendedAction(
    val type: String, // "CALLBACK", "CALENDAR_REMINDER", "SAVE_CONTACT", "BLOCK_SPAM", "NOTE"
    val title: String,
    val detail: String
)

data class ScreenSummaryResult(
    val summary: String,
    val reasonForCall: String,
    val urgency: String,
    val category: String,
    val isSpam: Boolean,
    val spamScore: Int,
    val keyDetails: List<String>,
    val recommendedActions: List<RecommendedAction>
)

data class CallerProfile(
    val name: String,
    val phoneNumber: String,
    val isContact: Boolean = false,
    val initialSpamRisk: Boolean = false,
    val initialCategory: String = "General"
)

data class OutboundCallPlan(
    val openingScript: String,
    val talkingPoints: List<String>,
    val fallbackScript: String,
    val suggestedDurationMinutes: Int = 3
)

data class VoiceNoteSummaryResult(
    val title: String,
    val summary: String,
    val actionItems: List<String>,
    val tags: List<String>
)

enum class OutboundCallStatus {
    PREPARING,
    CALLING,
    RINGING,
    CONNECTED,
    AI_SPEAKING,
    LISTENING,
    PROCESSING,
    ENDING,
    COMPLETED,
    FAILED
}

data class TranscriptEvent(
    val id: String,
    val speaker: String, // "dinthi_ai", "recipient", "system", "user"
    val text: String,
    val timestamp: Long
)

data class OutboundCallSummary(
    val summary: String,
    val reasonForCall: String,
    val outcome: String,
    val importantDetails: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val actionItems: List<String> = emptyList(),
    val sentiment: String = "Neutral",
    val durationSeconds: Int = 0
)

typealias CallSummary = OutboundCallSummary

data class OutboundTurnResult(
    val replyText: String,
    val intent: String,
    val actionItem: String?,
    val shouldEndCall: Boolean
)

data class OutboundCallSession(
    val id: String,
    val recipientName: String,
    val phoneNumber: String,
    val userName: String = "the user",
    val goal: String,
    val notes: String,
    val mode: String, // "AI" or "HUMAN"
    val language: String,
    val persona: String,
    val status: String,
    val failureReason: String? = null,
    val startTime: Long,
    val durationSeconds: Int,
    val transcript: List<TranscriptEvent>,
    val summary: OutboundCallSummary?
)

typealias CallSession = OutboundCallSession

data class TelephonyCapabilities(
    val bargeIn: Boolean = false,
    val hindi: Boolean = true,
    val english: Boolean = true,
    val hinglish: Boolean = true,
    val silenceHandling: Boolean = false,
    val liveCallTakeover: Boolean = false
)

data class TelephonyStatusResponse(
    val provider: String,
    val isConfigured: Boolean,
    val statusLabel: String = if (isConfigured) "CONFIGURED" else "NOT CONFIGURED",
    val accountSid: String?,
    val fromNumber: String?,
    val aiReady: Boolean,
    val voiceReady: Boolean,
    val backendStatus: String,
    val traiCompliance: String,
    val aiTransparency: String,
    val capabilities: TelephonyCapabilities = TelephonyCapabilities()
)
