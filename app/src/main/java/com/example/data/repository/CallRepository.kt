package com.example.data.repository

import com.example.data.local.entity.CallEntity
import com.example.data.local.entity.TranscriptLineEntity
import com.example.data.model.CallerProfile
import com.example.data.model.ScreenSummaryResult
import com.example.data.model.ScreenTurnResult
import kotlinx.coroutines.flow.Flow

interface CallRepository {
    fun getAllCalls(): Flow<List<CallEntity>>
    fun getCallById(id: String): Flow<CallEntity?>
    suspend fun getCallByIdOnce(id: String): CallEntity?
    fun getTranscriptForCall(callId: String): Flow<List<TranscriptLineEntity>>
    fun searchCalls(query: String): Flow<List<CallEntity>>
    fun getScreenedCount(): Flow<Int>
    fun getSpamBlockedCount(): Flow<Int>

    suspend fun saveCall(call: CallEntity)
    suspend fun updateCall(call: CallEntity)
    suspend fun addTranscriptLine(line: TranscriptLineEntity): Long
    suspend fun deleteCall(id: String)
    suspend fun clearHistory()

    // Call screening AI engine methods (routed through secure server backend)
    suspend fun processScreenTurn(
        callerStatement: String,
        conversationHistory: List<TranscriptLineEntity>,
        callerInfo: CallerProfile,
        language: String,
        persona: String
    ): ScreenTurnResult

    suspend fun generateCallSummary(
        callId: String,
        conversation: List<TranscriptLineEntity>,
        callerInfo: CallerProfile,
        durationSeconds: Int
    ): ScreenSummaryResult

    // Seed mock samples for demo / testing initial state if database empty
    suspend fun seedSampleCallsIfEmpty()

    // Scheduled Calls
    fun getAllScheduledCalls(): Flow<List<com.example.data.local.entity.ScheduledCallEntity>>
    fun getUpcomingScheduledCalls(): Flow<List<com.example.data.local.entity.ScheduledCallEntity>>
    suspend fun saveScheduledCall(call: com.example.data.local.entity.ScheduledCallEntity)
    suspend fun updateScheduledCall(call: com.example.data.local.entity.ScheduledCallEntity)
    suspend fun deleteScheduledCall(id: String)

    // Voice Notes
    fun getAllVoiceNotes(): Flow<List<com.example.data.local.entity.VoiceNoteEntity>>
    suspend fun saveVoiceNote(note: com.example.data.local.entity.VoiceNoteEntity)
    suspend fun deleteVoiceNote(id: String)
    suspend fun clearAllVoiceNotes()

    // Outbound Planning & Voice Note Intelligence
    suspend fun planOutboundCall(recipientName: String, phoneNumber: String, goal: String, language: String): com.example.data.model.OutboundCallPlan
    suspend fun summarizeVoiceNote(transcription: String): com.example.data.model.VoiceNoteSummaryResult

    // Outbound Telephony & AI Calling Layer
    suspend fun initiateOutboundCall(
        recipientName: String,
        phoneNumber: String,
        userName: String = "the user",
        goal: String,
        notes: String,
        mode: String,
        language: String,
        persona: String
    ): com.example.data.model.OutboundCallSession?

    suspend fun getOutboundSession(sessionId: String): com.example.data.model.OutboundCallSession?
    suspend fun sendOutboundTurn(sessionId: String, userText: String): com.example.data.model.OutboundTurnResult?
    suspend fun endOutboundCall(sessionId: String, reason: String): com.example.data.model.OutboundCallSession?
    suspend fun getTelephonyStatus(): com.example.data.model.TelephonyStatusResponse?
    suspend fun saveOutboundSessionRecord(session: com.example.data.model.OutboundCallSession)
}

