package com.example.ui.navigation

sealed class Screen {
    data object Dashboard : Screen()
    data class LiveScreening(val callId: String) : Screen()
    data class CallDetail(val callId: String) : Screen()
    data object CallSimulator : Screen()
    data object ScheduleCall : Screen()
    data object VoiceNotes : Screen()
    data object Settings : Screen()
    data object Profile : Screen() // "You" tab
    data object Instructions : Screen()
    data object HealthCheck : Screen()
    data object Addresses : Screen()
    data object CallSomeone : Screen() // Ask Dinthi to Call Someone
    data class OutboundCall(val sessionId: String) : Screen() // Live outbound AI calling session
    data object TelephonyProviderSetup : Screen() // Telephony Provider & TRAI Diagnostics
    data class Chat(val conversationId: String) : Screen()
}
