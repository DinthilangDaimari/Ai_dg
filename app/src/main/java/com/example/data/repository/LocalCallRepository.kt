package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.dao.CallDao
import com.example.data.local.entity.CallEntity
import com.example.data.local.entity.CallStatus
import com.example.data.local.entity.TranscriptLineEntity
import com.example.data.local.entity.TranscriptSpeaker
import com.example.data.model.CallerProfile
import com.example.data.model.OutboundCallSession
import com.example.data.model.OutboundCallStatus
import com.example.data.model.OutboundCallSummary
import com.example.data.model.OutboundTurnResult
import com.example.data.model.RecommendedAction
import com.example.data.model.ScreenSummaryResult
import com.example.data.model.ScreenTurnResult
import com.example.data.model.TelephonyCapabilities
import com.example.data.model.TelephonyStatusResponse
import com.example.data.model.TranscriptEvent
import com.example.data.remote.BackendSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class LocalCallRepository(
    private val context: Context,
    private val callDao: CallDao,
    private val getServerUrl: () -> String = { BuildConfig.SERVER_BASE_URL }
) : CallRepository {

    private val tag = "LocalCallRepository"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private suspend fun executeAuthorized(
        serverUrl: String,
        builderProvider: () -> Request.Builder
    ): okhttp3.Response? {
        var token = BackendSessionManager.getSessionToken(okHttpClient, serverUrl) ?: return null
        val firstReq = builderProvider()
            .header("Authorization", "Bearer $token")
            .build()
        val firstResp = okHttpClient.newCall(firstReq).execute()
        if (firstResp.code == 401) {
            firstResp.close()
            token = BackendSessionManager.refreshSessionToken(okHttpClient, serverUrl) ?: return null
            val retryReq = builderProvider()
                .header("Authorization", "Bearer $token")
                .build()
            return okHttpClient.newCall(retryReq).execute()
        }
        return firstResp
    }

    override fun getAllCalls(): Flow<List<CallEntity>> = callDao.getAllCalls()

    override fun getCallById(id: String): Flow<CallEntity?> = callDao.getCallById(id)

    override suspend fun getCallByIdOnce(id: String): CallEntity? = withContext(Dispatchers.IO) {
        callDao.getCallByIdOnce(id)
    }

    override fun getTranscriptForCall(callId: String): Flow<List<TranscriptLineEntity>> =
        callDao.getTranscriptForCall(callId)

    override fun searchCalls(query: String): Flow<List<CallEntity>> = callDao.searchCalls(query)

    override fun getScreenedCount(): Flow<Int> = callDao.getScreenedCount()

    override fun getSpamBlockedCount(): Flow<Int> = callDao.getSpamBlockedCount()

    override suspend fun saveCall(call: CallEntity) = withContext(Dispatchers.IO) {
        callDao.insertCall(call)
    }

    override suspend fun updateCall(call: CallEntity) = withContext(Dispatchers.IO) {
        callDao.updateCall(call)
    }

    override suspend fun addTranscriptLine(line: TranscriptLineEntity): Long = withContext(Dispatchers.IO) {
        callDao.insertTranscriptLine(line)
    }

    override suspend fun deleteCall(id: String) = withContext(Dispatchers.IO) {
        callDao.deleteTranscriptForCall(id)
        callDao.deleteCall(id)
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) {
        callDao.clearAllTranscripts()
        callDao.clearAllCalls()
    }

    override suspend fun processScreenTurn(
        callerStatement: String,
        conversationHistory: List<TranscriptLineEntity>,
        callerInfo: CallerProfile,
        language: String,
        persona: String
    ): ScreenTurnResult = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().trim().trimEnd('/')
        val endpoint = "$serverUrl/api/screen/turn"

        try {
            val historyJson = JSONArray()
            for (h in conversationHistory) {
                val item = JSONObject()
                item.put("speaker", h.speaker)
                item.put("text", h.text)
                historyJson.put(item)
            }

            val callerJson = JSONObject()
            callerJson.put("name", callerInfo.name)
            callerJson.put("number", callerInfo.phoneNumber)

            val rootJson = JSONObject()
            rootJson.put("callerStatement", callerStatement)
            rootJson.put("conversationHistory", historyJson)
            rootJson.put("callerInfo", callerJson)
            rootJson.put("language", language)
            rootJson.put("persona", persona)

            val response = executeAuthorized(serverUrl) {
                Request.Builder()
                    .url(endpoint)
                    .post(rootJson.toString().toRequestBody("application/json".toMediaType()))
            } ?: return@withContext generateLocalScreenTurnFallback(callerStatement, callerInfo)

            if (response.isSuccessful) {
                val bodyStr = response.body?.string().orEmpty()
                val json = JSONObject(bodyStr)

                val quickReplies = mutableListOf<String>()
                val qrArray = json.optJSONArray("suggestedQuickReplies")
                if (qrArray != null) {
                    for (i in 0 until qrArray.length()) {
                        quickReplies.add(qrArray.getString(i))
                    }
                }

                return@withContext ScreenTurnResult(
                    replyText = json.optString("replyText", "Hello, I am Dinthi AI screening this call. How can I assist you?"),
                    intent = json.optString("intent", "General"),
                    spamScore = json.optInt("spamScore", 10),
                    isSpam = json.optBoolean("isSpam", false),
                    suggestedQuickReplies = if (quickReplies.isEmpty()) listOf("Who is calling?", "I'll call back", "Decline call") else quickReplies
                )
            } else {
                Log.w(tag, "Screen turn call failed with code ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error invoking screen turn API: ${e.message}", e)
        }

        // Resilient fallback screening behavior
        generateLocalScreenTurnFallback(callerStatement, callerInfo)
    }

    override suspend fun generateCallSummary(
        callId: String,
        conversation: List<TranscriptLineEntity>,
        callerInfo: CallerProfile,
        durationSeconds: Int
    ): ScreenSummaryResult = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().trim().trimEnd('/')
        val endpoint = "$serverUrl/api/screen/summary"

        try {
            val transcriptJson = JSONArray()
            for (line in conversation) {
                val item = JSONObject()
                item.put("speaker", line.speaker)
                item.put("text", line.text)
                transcriptJson.put(item)
            }

            val callerJson = JSONObject()
            callerJson.put("name", callerInfo.name)
            callerJson.put("number", callerInfo.phoneNumber)

            val rootJson = JSONObject()
            rootJson.put("conversation", transcriptJson)
            rootJson.put("callerInfo", callerJson)
            rootJson.put("durationSeconds", durationSeconds)

            val response = executeAuthorized(serverUrl) {
                Request.Builder()
                    .url(endpoint)
                    .post(rootJson.toString().toRequestBody("application/json".toMediaType()))
            } ?: return@withContext generateLocalSummaryFallback(callerInfo, conversation, durationSeconds)

            if (response.isSuccessful) {
                val bodyStr = response.body?.string().orEmpty()
                val json = JSONObject(bodyStr)

                val keyDetails = mutableListOf<String>()
                val kdArray = json.optJSONArray("keyDetails")
                if (kdArray != null) {
                    for (i in 0 until kdArray.length()) {
                        keyDetails.add(kdArray.getString(i))
                    }
                }

                val actions = mutableListOf<RecommendedAction>()
                val actArray = json.optJSONArray("recommendedActions")
                if (actArray != null) {
                    for (i in 0 until actArray.length()) {
                        val actObj = actArray.getJSONObject(i)
                        actions.add(
                            RecommendedAction(
                                type = actObj.optString("type", "CALLBACK"),
                                title = actObj.optString("title", "Call Back"),
                                detail = actObj.optString("detail", "Follow up with caller")
                            )
                        )
                    }
                }

                return@withContext ScreenSummaryResult(
                    summary = json.optString("summary", "Call screened by Dinthi AI."),
                    reasonForCall = json.optString("reasonForCall", "Inquiry from ${callerInfo.name}"),
                    urgency = json.optString("urgency", "Medium"),
                    category = json.optString("category", "General"),
                    isSpam = json.optBoolean("isSpam", false),
                    spamScore = json.optInt("spamScore", 10),
                    keyDetails = keyDetails,
                    recommendedActions = actions
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error generating call summary via server: ${e.message}", e)
        }

        // Resilient fallback summary generator
        generateLocalSummaryFallback(callerInfo, conversation, durationSeconds)
    }

    private fun generateLocalScreenTurnFallback(
        callerStatement: String,
        callerInfo: CallerProfile
    ): ScreenTurnResult {
        val lower = callerStatement.lowercase()
        return when {
            lower.contains("delivery") || lower.contains("courier") || lower.contains("parcel") || lower.contains("amazon") || lower.contains("swiggy") || lower.contains("zomato") -> {
                ScreenTurnResult(
                    replyText = "Hello! I am Dinthi AI screening for the recipient. Could you please leave the package with the security guard or outside the door?",
                    intent = "Delivery",
                    spamScore = 5,
                    isSpam = false,
                    suggestedQuickReplies = listOf("Leave at door", "Give gate security code", "I'll come down now")
                )
            }
            lower.contains("appointment") || lower.contains("doctor") || lower.contains("clinic") || lower.contains("hospital") || lower.contains("reschedule") -> {
                ScreenTurnResult(
                    replyText = "Thank you for calling. Dinthi AI is taking note of your appointment details. Could you please specify the date and time?",
                    intent = "Healthcare",
                    spamScore = 2,
                    isSpam = false,
                    suggestedQuickReplies = listOf("Confirm Thursday 3 PM", "Request morning slot", "Take a message")
                )
            }
            lower.contains("interview") || lower.contains("resume") || lower.contains("recruiter") || lower.contains("job") || lower.contains("hiring") -> {
                ScreenTurnResult(
                    replyText = "Hello, this is Dinthi AI screening the call. The recipient is interested in career opportunities. Which role and organization are you calling from?",
                    intent = "Recruiter",
                    spamScore = 8,
                    isSpam = false,
                    suggestedQuickReplies = listOf("Share email for JD", "Ask for callback at 5 PM", "Take over call")
                )
            }
            lower.contains("loan") || lower.contains("credit card") || lower.contains("pre-approved") || lower.contains("investment") || lower.contains("insurance offer") -> {
                ScreenTurnResult(
                    replyText = "Dinthi AI Call Screening: The recipient is on the Do-Not-Disturb registry and not interested in unsolicited financial offers. Please remove this number.",
                    intent = "Telemarketing/Spam",
                    spamScore = 95,
                    isSpam = true,
                    suggestedQuickReplies = listOf("Block caller", "Decline immediately", "Report spam")
                )
            }
            else -> {
                ScreenTurnResult(
                    replyText = "Hello, I am Dinthi AI screening this call. The recipient is currently unavailable. Could you please state your name and purpose?",
                    intent = "General",
                    spamScore = 15,
                    isSpam = false,
                    suggestedQuickReplies = listOf("Ask who is calling", "Ask for text message", "Decline call")
                )
            }
        }
    }

    private fun generateLocalSummaryFallback(
        callerInfo: CallerProfile,
        conversation: List<TranscriptLineEntity>,
        durationSeconds: Int
    ): ScreenSummaryResult {
        val fullText = conversation.joinToString(" ") { it.text }.lowercase()
        return when {
            fullText.contains("delivery") || fullText.contains("courier") || fullText.contains("parcel") -> {
                ScreenSummaryResult(
                    summary = "Courier driver ${callerInfo.name} called regarding parcel delivery for the apartment. Dinthi AI instructed the courier regarding drop-off.",
                    reasonForCall = "Package delivery confirmation",
                    urgency = "High",
                    category = "Delivery",
                    isSpam = false,
                    spamScore = 4,
                    keyDetails = listOf("Package delivery in progress", "Caller: ${callerInfo.name} (${callerInfo.phoneNumber})", "Duration: ${durationSeconds}s"),
                    recommendedActions = listOf(
                        RecommendedAction("CALLBACK", "Call Courier", "Contact ${callerInfo.phoneNumber} if package is not located"),
                        RecommendedAction("NOTE", "Check Front Desk", "Verify drop-off with security")
                    )
                )
            }
            fullText.contains("doctor") || fullText.contains("clinic") || fullText.contains("appointment") -> {
                ScreenSummaryResult(
                    summary = "Clinic staff called to confirm upcoming dental/health checkup. Dinthi AI gathered time details.",
                    reasonForCall = "Appointment scheduling and confirmation",
                    urgency = "Medium",
                    category = "Healthcare",
                    isSpam = false,
                    spamScore = 2,
                    keyDetails = listOf("Clinic confirmation call", "Recommended follow-up scheduled", "Duration: ${durationSeconds}s"),
                    recommendedActions = listOf(
                        RecommendedAction("CALENDAR_REMINDER", "Add to Calendar", "Save appointment slot to Google Calendar"),
                        RecommendedAction("CALLBACK", "Confirm Slot", "Call ${callerInfo.phoneNumber} to re-confirm")
                    )
                )
            }
            fullText.contains("loan") || fullText.contains("credit card") || fullText.contains("offer") -> {
                ScreenSummaryResult(
                    summary = "Telemarketing robocall attempting to offer unsolicited pre-approved financial loans. Dinthi AI classified call as high-probability spam.",
                    reasonForCall = "Unsolicited loan telemarketing",
                    urgency = "Low",
                    category = "Spam/Robocall",
                    isSpam = true,
                    spamScore = 96,
                    keyDetails = listOf("Telemarketing pitch detected", "High spam confidence (96%)", "No callback requested"),
                    recommendedActions = listOf(
                        RecommendedAction("BLOCK_SPAM", "Block & Report", "Prevent further calls from ${callerInfo.phoneNumber}")
                    )
                )
            }
            else -> {
                ScreenSummaryResult(
                    summary = "Call from ${callerInfo.name} screened by Dinthi AI for $durationSeconds seconds. Caller inquired about general communication.",
                    reasonForCall = "Inquiry from ${callerInfo.name}",
                    urgency = "Medium",
                    category = "General",
                    isSpam = false,
                    spamScore = 12,
                    keyDetails = listOf("Handled by Dinthi AI Assistant", "Caller number: ${callerInfo.phoneNumber}", "Duration: ${durationSeconds}s"),
                    recommendedActions = listOf(
                        RecommendedAction("CALLBACK", "Call Back", "Return call to ${callerInfo.phoneNumber}"),
                        RecommendedAction("SAVE_CONTACT", "Save Contact", "Add ${callerInfo.name} to phonebook")
                    )
                )
            }
        }
    }

    override suspend fun seedSampleCallsIfEmpty() = withContext(Dispatchers.IO) {
        // STRICT REAL-DATA RULE: Never automatically seed fake personal data or fake statistics.
        // The app starts clean with 0 calls, 0 spam blocked, 0 time saved until real calls arrive.
    }

    suspend fun seedDemoDataExplicitly() = withContext(Dispatchers.IO) {
        // Explicit Demo Mode only: Clearly labeled demo calls for user feature testing
        val now = System.currentTimeMillis()
        val call1Id = UUID.randomUUID().toString()
        val call1 = CallEntity(
            id = call1Id,
            callerName = "Demo: Express Delivery (Demo Courier)",
            phoneNumber = "Demo #1",
            timestamp = now - (25 * 60 * 1000),
            durationSeconds = 48,
            status = CallStatus.DEMO_SIMULATOR.name,
            isSpam = false,
            spamScore = 4,
            category = "Demo/Simulator",
            urgency = "High",
            summary = "[DEMO MODE] Courier driver called to confirm drop-off. Dinthi AI instructed driver to leave package at reception.",
            reasonForCall = "Simulated delivery drop-off",
            keyDetailsJson = """["[DEMO] Package drop-off", "[DEMO] Verified at reception"]""",
            actionsJson = """[{"type":"CALLBACK","title":"Call Back (Demo)","detail":"Reach courier"},{"type":"NOTE","title":"Collect Package","detail":"Reception desk"}]""",
            isContact = false,
            isDemo = true
        )
        callDao.insertCall(call1)
        callDao.insertTranscriptLines(
            listOf(
                TranscriptLineEntity(callId = call1Id, speaker = TranscriptSpeaker.CALLER.name, text = "[DEMO] Hello, I have a delivery package for this address.", timestamp = now - (25 * 60 * 1000)),
                TranscriptLineEntity(callId = call1Id, speaker = TranscriptSpeaker.AI.name, text = "Hello! Dinthi AI is screening this call. Please leave it at reception.", timestamp = now - (25 * 60 * 1000) + 10000)
            )
        )
    }

    override fun getAllScheduledCalls(): Flow<List<com.example.data.local.entity.ScheduledCallEntity>> =
        callDao.getAllScheduledCalls()

    override fun getUpcomingScheduledCalls(): Flow<List<com.example.data.local.entity.ScheduledCallEntity>> =
        callDao.getUpcomingScheduledCalls()

    override suspend fun saveScheduledCall(call: com.example.data.local.entity.ScheduledCallEntity) = withContext(Dispatchers.IO) {
        callDao.insertScheduledCall(call)
    }

    override suspend fun updateScheduledCall(call: com.example.data.local.entity.ScheduledCallEntity) = withContext(Dispatchers.IO) {
        callDao.updateScheduledCall(call)
    }

    override suspend fun deleteScheduledCall(id: String) = withContext(Dispatchers.IO) {
        callDao.deleteScheduledCall(id)
    }

    override fun getAllVoiceNotes(): Flow<List<com.example.data.local.entity.VoiceNoteEntity>> =
        callDao.getAllVoiceNotes()

    override suspend fun saveVoiceNote(note: com.example.data.local.entity.VoiceNoteEntity) = withContext(Dispatchers.IO) {
        callDao.insertVoiceNote(note)
    }

    override suspend fun deleteVoiceNote(id: String) = withContext(Dispatchers.IO) {
        callDao.deleteVoiceNote(id)
    }

    override suspend fun clearAllVoiceNotes() = withContext(Dispatchers.IO) {
        callDao.clearAllVoiceNotes()
    }

    override suspend fun planOutboundCall(
        recipientName: String,
        phoneNumber: String,
        goal: String,
        language: String
    ): com.example.data.model.OutboundCallPlan = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().trim().trimEnd('/')
        val endpoint = "$serverUrl/api/assistant/outbound_plan"

        try {
            val root = JSONObject().apply {
                put("recipientName", recipientName)
                put("phoneNumber", phoneNumber)
                put("goal", goal)
                put("language", language)
            }

            val response = executeAuthorized(serverUrl) {
                Request.Builder()
                    .url(endpoint)
                    .post(root.toString().toRequestBody("application/json".toMediaType()))
            }
            if (response != null && response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                val points = mutableListOf<String>()
                val pointsArr = json.optJSONArray("talkingPoints")
                if (pointsArr != null) {
                    for (i in 0 until pointsArr.length()) points.add(pointsArr.getString(i))
                }
                return@withContext com.example.data.model.OutboundCallPlan(
                    openingScript = json.optString("openingScript", "Hello $recipientName, calling regarding $goal."),
                    talkingPoints = if (points.isEmpty()) listOf("Discuss $goal", "Confirm next steps") else points,
                    fallbackScript = json.optString("fallbackScript", "Please call back regarding $goal."),
                    suggestedDurationMinutes = json.optInt("suggestedDurationMinutes", 3)
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error planning outbound call: ${e.message}")
        }

        com.example.data.model.OutboundCallPlan(
            openingScript = "Hello $recipientName, this is regarding $goal.",
            talkingPoints = listOf("State your purpose: $goal", "Ask for availability or questions", "Agree on next steps"),
            fallbackScript = "Hi, please call me back regarding $goal whenever convenient.",
            suggestedDurationMinutes = 3
        )
    }

    override suspend fun summarizeVoiceNote(
        transcription: String
    ): com.example.data.model.VoiceNoteSummaryResult = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().trim().trimEnd('/')
        val endpoint = "$serverUrl/api/voicenote/summarize"

        try {
            val root = JSONObject().apply {
                put("transcription", transcription)
            }

            val response = executeAuthorized(serverUrl) {
                Request.Builder()
                    .url(endpoint)
                    .post(root.toString().toRequestBody("application/json".toMediaType()))
            }
            if (response != null && response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                val actions = mutableListOf<String>()
                val actionArr = json.optJSONArray("actionItems")
                if (actionArr != null) {
                    for (i in 0 until actionArr.length()) actions.add(actionArr.getString(i))
                }
                val tags = mutableListOf<String>()
                val tagsArr = json.optJSONArray("tags")
                if (tagsArr != null) {
                    for (i in 0 until tagsArr.length()) tags.add(tagsArr.getString(i))
                }
                return@withContext com.example.data.model.VoiceNoteSummaryResult(
                    title = json.optString("title", "Voice Memo"),
                    summary = json.optString("summary", transcription.take(80)),
                    actionItems = actions,
                    tags = tags
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error summarizing voice note: ${e.message}")
        }

        com.example.data.model.VoiceNoteSummaryResult(
            title = "Voice Memo",
            summary = transcription.take(100),
            actionItems = listOf("Review voice memo details"),
            tags = listOf("VoiceMemo")
        )
    }

    // ==========================================================
    // OUTBOUND TELEPHONY & AI CALLING LAYER
    // ==========================================================

    override suspend fun initiateOutboundCall(
        recipientName: String,
        phoneNumber: String,
        userName: String,
        goal: String,
        notes: String,
        mode: String,
        language: String,
        persona: String
    ): OutboundCallSession? = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().trim().trimEnd('/')
        val endpoint = "$serverUrl/api/telephony/call"
        try {
            val root = JSONObject().apply {
                put("recipientName", recipientName)
                put("phoneNumber", phoneNumber)
                put("userName", userName.ifBlank { "the user" })
                put("goal", goal)
                put("notes", notes)
                put("mode", mode)
                put("language", language)
                put("persona", persona)
            }
            val response = executeAuthorized(serverUrl) {
                Request.Builder()
                    .url(endpoint)
                    .post(root.toString().toRequestBody("application/json".toMediaType()))
            }
            if (response != null && response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                val sessionJson = json.optJSONObject("session")
                if (sessionJson != null) {
                    val parsed = parseSessionJson(sessionJson)
                    if (parsed.status == OutboundCallStatus.FAILED.name || parsed.status == OutboundCallStatus.COMPLETED.name) {
                        saveOutboundCallToDatabase(parsed)
                    }
                    return@withContext parsed
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error initiating outbound call: ${e.message}")
        }
        null
    }

    override suspend fun getOutboundSession(sessionId: String): OutboundCallSession? = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().trim().trimEnd('/')
        val endpoint = "$serverUrl/api/telephony/session/$sessionId"
        try {
            val response = executeAuthorized(serverUrl) {
                Request.Builder()
                    .url(endpoint)
                    .get()
            }
            if (response != null && response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                val sessionJson = json.optJSONObject("session")
                if (sessionJson != null) {
                    val parsed = parseSessionJson(sessionJson)
                    if (parsed.status == OutboundCallStatus.COMPLETED.name || parsed.status == OutboundCallStatus.FAILED.name) {
                        saveOutboundCallToDatabase(parsed)
                    }
                    return@withContext parsed
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching session: ${e.message}")
        }
        null
    }

    override suspend fun sendOutboundTurn(sessionId: String, userText: String): OutboundTurnResult? = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().trim().trimEnd('/')
        val endpoint = "$serverUrl/api/telephony/session/$sessionId/turn"
        try {
            val root = JSONObject().apply {
                put("text", userText)
            }
            val response = executeAuthorized(serverUrl) {
                Request.Builder()
                    .url(endpoint)
                    .post(root.toString().toRequestBody("application/json".toMediaType()))
            }
            if (response != null && response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                val turnJson = json.optJSONObject("turn")
                if (turnJson != null) {
                    return@withContext OutboundTurnResult(
                        replyText = turnJson.optString("replyText", ""),
                        intent = turnJson.optString("intent", "General"),
                        actionItem = turnJson.optString("actionItem").takeIf { it.isNotBlank() && it != "null" },
                        shouldEndCall = turnJson.optBoolean("shouldEndCall", false)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error sending turn: ${e.message}")
        }
        null
    }

    override suspend fun endOutboundCall(sessionId: String, reason: String): OutboundCallSession? = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().trim().trimEnd('/')
        val endpoint = "$serverUrl/api/telephony/session/$sessionId/end"
        try {
            val root = JSONObject().apply {
                put("reason", reason)
            }
            val response = executeAuthorized(serverUrl) {
                Request.Builder()
                    .url(endpoint)
                    .post(root.toString().toRequestBody("application/json".toMediaType()))
            }
            if (response != null && response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                val sessionJson = json.optJSONObject("session")
                if (sessionJson != null) {
                    val parsedSession = parseSessionJson(sessionJson)
                    saveOutboundCallToDatabase(parsedSession)
                    return@withContext parsedSession
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error ending outbound call: ${e.message}")
        }
        null
    }

    override suspend fun getTelephonyStatus(): TelephonyStatusResponse? = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().trim().trimEnd('/')
        val endpoint = "$serverUrl/api/telephony/status"
        try {
            val response = executeAuthorized(serverUrl) {
                Request.Builder()
                    .url(endpoint)
                    .get()
            }
            if (response != null && response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                val regulations = json.optJSONObject("regulations")
                val capJson = json.optJSONObject("capabilities")
                val capabilities = if (capJson != null) {
                    TelephonyCapabilities(
                        bargeIn = capJson.optBoolean("bargeIn", false),
                        hindi = capJson.optBoolean("hindi", true),
                        english = capJson.optBoolean("english", true),
                        hinglish = capJson.optBoolean("hinglish", true),
                        silenceHandling = capJson.optBoolean("silenceHandling", false),
                        liveCallTakeover = capJson.optBoolean("liveCallTakeover", false)
                    )
                } else {
                    TelephonyCapabilities()
                }
                val isConfigured = json.optBoolean("isConfigured", false)
                return@withContext TelephonyStatusResponse(
                    provider = json.optString("provider", "Not configured"),
                    isConfigured = isConfigured,
                    statusLabel = json.optString("statusLabel", if (isConfigured) "CONFIGURED" else "NOT CONFIGURED"),
                    accountSid = json.optString("accountSid").takeIf { it.isNotBlank() && it != "null" },
                    fromNumber = json.optString("fromNumber").takeIf { it.isNotBlank() && it != "null" },
                    aiReady = json.optBoolean("aiReady", false),
                    voiceReady = json.optBoolean("voiceReady", false),
                    backendStatus = json.optString("backendStatus", "ONLINE"),
                    traiCompliance = regulations?.optString("traiCompliance") ?: "TRAI compliance required.",
                    aiTransparency = regulations?.optString("aiTransparency") ?: "AI disclosure required.",
                    capabilities = capabilities
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching telephony status: ${e.message}")
        }
        null
    }

    override suspend fun saveOutboundSessionRecord(session: OutboundCallSession) = withContext(Dispatchers.IO) {
        saveOutboundCallToDatabase(session)
    }

    private suspend fun saveOutboundCallToDatabase(session: OutboundCallSession) {
        val callStatus = when {
            session.status == OutboundCallStatus.FAILED.name -> CallStatus.OUTGOING_FAILED.name
            session.mode == "AI" -> CallStatus.OUTGOING_AI.name
            else -> CallStatus.OUTGOING_HUMAN.name
        }
        val summary = session.summary?.summary ?: if (session.status == OutboundCallStatus.FAILED.name) {
            session.failureReason ?: "Outbound AI call could not be placed."
        } else {
            "Outbound call to ${session.recipientName}"
        }
        val reason = session.summary?.reasonForCall ?: session.goal

        val combinedDetails = buildList {
            session.summary?.importantDetails?.forEach { add(it) }
            session.summary?.decisions?.forEach { add("Decision: $it") }
        }
        val detailsJson = JSONArray(combinedDetails).toString()

        val actionsJson = session.summary?.actionItems?.map {
            JSONObject().apply {
                put("type", "NOTE")
                put("title", "Outbound Follow-up")
                put("detail", it)
            }
        }?.let { JSONArray(it).toString() } ?: "[]"

        val callEntity = CallEntity(
            id = session.id,
            callerName = session.recipientName,
            phoneNumber = session.phoneNumber,
            timestamp = session.startTime,
            durationSeconds = session.summary?.durationSeconds?.takeIf { it > 0 } ?: session.durationSeconds,
            status = callStatus,
            isSpam = false,
            spamScore = 0,
            category = "Outbound",
            urgency = if (session.status == OutboundCallStatus.FAILED.name) "High" else "Normal",
            summary = summary,
            reasonForCall = reason,
            keyDetailsJson = detailsJson,
            actionsJson = actionsJson,
            isContact = true,
            isDemo = false
        )
        callDao.insertCall(callEntity)

        callDao.deleteTranscriptForCall(session.id)
        session.transcript.forEach { event ->
            val transcriptLine = TranscriptLineEntity(
                id = 0,
                callId = session.id,
                speaker = event.speaker,
                text = event.text,
                timestamp = event.timestamp
            )
            callDao.insertTranscriptLine(transcriptLine)
        }
    }

    private fun parseSessionJson(json: JSONObject): OutboundCallSession {
        val transcriptList = mutableListOf<TranscriptEvent>()
        val tArr = json.optJSONArray("transcript")
        if (tArr != null) {
            for (i in 0 until tArr.length()) {
                val tObj = tArr.getJSONObject(i)
                transcriptList.add(
                    TranscriptEvent(
                        id = tObj.optString("id", UUID.randomUUID().toString()),
                        speaker = tObj.optString("speaker", "system"),
                        text = tObj.optString("text", ""),
                        timestamp = tObj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }

        var summaryObj: OutboundCallSummary? = null
        val sJson = json.optJSONObject("summary")
        if (sJson != null) {
            val detailsList = mutableListOf<String>()
            val detailsArr = sJson.optJSONArray("importantDetails")
            if (detailsArr != null) {
                for (i in 0 until detailsArr.length()) detailsList.add(detailsArr.getString(i))
            }
            val decList = mutableListOf<String>()
            val decArr = sJson.optJSONArray("decisions")
            if (decArr != null) {
                for (i in 0 until decArr.length()) decList.add(decArr.getString(i))
            }
            val actList = mutableListOf<String>()
            val actArr = sJson.optJSONArray("actionItems")
            if (actArr != null) {
                for (i in 0 until actArr.length()) actList.add(actArr.getString(i))
            }
            summaryObj = OutboundCallSummary(
                summary = sJson.optString("summary", ""),
                reasonForCall = sJson.optString("reasonForCall", ""),
                outcome = sJson.optString("outcome", "Completed"),
                importantDetails = detailsList,
                decisions = decList,
                actionItems = actList,
                sentiment = sJson.optString("sentiment", "Neutral"),
                durationSeconds = sJson.optInt("durationSeconds", 0)
            )
        }

        return OutboundCallSession(
            id = json.optString("id", UUID.randomUUID().toString()),
            recipientName = json.optString("recipientName", "Recipient"),
            phoneNumber = json.optString("phoneNumber", ""),
            userName = json.optString("userName", "the user"),
            goal = json.optString("goal", "General Call"),
            notes = json.optString("notes", ""),
            mode = json.optString("mode", "AI"),
            language = json.optString("language", "English (India)"),
            persona = json.optString("persona", "Professional"),
            status = json.optString("status", "PREPARING"),
            failureReason = json.optString("failureReason").takeIf { it.isNotBlank() && it != "null" },
            startTime = json.optLong("startTime", System.currentTimeMillis()),
            durationSeconds = json.optInt("durationSeconds", 0),
            transcript = transcriptList,
            summary = summaryObj
        )
    }
}

