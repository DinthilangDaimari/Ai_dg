package com.example.telecom

import android.content.Context
import android.util.Log
import com.example.data.local.entity.CallEntity
import com.example.data.local.entity.CallStatus
import com.example.data.local.entity.TranscriptLineEntity
import com.example.data.local.entity.TranscriptSpeaker
import com.example.data.model.CallerProfile
import com.example.data.model.ScreenSummaryResult
import com.example.data.model.ScreenTurnResult
import com.example.data.repository.CallRepository
import com.example.voice.TextToSpeechManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.UUID

enum class ScreeningState {
    IDLE,
    INCOMING_ALERT,
    SCREENING_ACTIVE,
    USER_TAKEOVER,
    CALL_ENDED
}

data class ActiveScreeningSession(
    val callId: String = "",
    val state: ScreeningState = ScreeningState.IDLE,
    val callerProfile: CallerProfile = CallerProfile("Unknown", ""),
    val transcript: List<TranscriptLineEntity> = emptyList(),
    val isAiSpeaking: Boolean = false,
    val currentAiSpokenText: String = "",
    val detectedIntent: String = "Detecting...",
    val spamScore: Int = 0,
    val isSpamDetected: Boolean = false,
    val quickReplies: List<String> = listOf("Who is calling?", "I'll call back", "Decline call"),
    val durationSeconds: Int = 0,
    val finalSummary: ScreenSummaryResult? = null,
    val isDemoSimulation: Boolean = false
)

class CallScreeningEngine(
    private val context: Context,
    private val callRepository: CallRepository,
    private val ttsManager: TextToSpeechManager,
    private val scope: CoroutineScope
) {
    private val tag = "CallScreeningEngine"

    private val _sessionState = MutableStateFlow(ActiveScreeningSession())
    val sessionState: StateFlow<ActiveScreeningSession> = _sessionState.asStateFlow()

    private var durationJob: Job? = null
    private var dialogueSequenceJob: Job? = null

    init {
        // Wire incoming call interceptor
        DinthiCallScreeningService.onCallInterceptedListener = { number, isIncoming ->
            if (isIncoming && _sessionState.value.state == ScreeningState.IDLE) {
                val resolvedContact = resolveContact(number)
                initiateIncomingCall(
                    CallerProfile(
                        name = resolvedContact?.first ?: number,
                        phoneNumber = number,
                        isContact = resolvedContact != null,
                        initialSpamRisk = number.startsWith("+91140") || number.startsWith("140")
                    )
                )
            }
        }
    }

    private fun resolveContact(phoneNumber: String): Pair<String, Boolean>? {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        val name = c.getString(nameIdx)
                        if (!name.isNullOrBlank()) {
                            return Pair(name, true)
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun initiateIncomingCall(callerProfile: CallerProfile, isDemoSimulation: Boolean = false) {
        val newCallId = UUID.randomUUID().toString()
        _sessionState.value = ActiveScreeningSession(
            callId = newCallId,
            state = ScreeningState.INCOMING_ALERT,
            callerProfile = callerProfile,
            transcript = emptyList(),
            spamScore = if (callerProfile.initialSpamRisk) 85 else 5,
            isSpamDetected = callerProfile.initialSpamRisk,
            isDemoSimulation = isDemoSimulation
        )
    }

    fun acceptScreenWithAi(language: String, persona: String, scenario: SimulationScenario? = null) {
        val current = _sessionState.value
        val callId = current.callId.ifEmpty { UUID.randomUUID().toString() }

        _sessionState.value = current.copy(
            callId = callId,
            state = ScreeningState.SCREENING_ACTIVE,
            durationSeconds = 0
        )

        // Start call duration ticker
        startTimer()

        // Initial Dinthi AI greeting
        val initialAiGreeting = when (language) {
            "Hindi (हिन्दी)" -> "नमस्ते, मैं दिनथी एआई हूँ। मैं प्राप्तकर्ता की ओर से यह कॉल स्क्रीन कर रही हूँ। कृपया अपना नाम और कॉल का कारण बताएं।"
            else -> "Hello, I am Dinthi AI screening this call on behalf of the recipient. Please state your name and reason for calling."
        }

        scope.launch(Dispatchers.Main) {
            addTranscriptLine(TranscriptSpeaker.AI, initialAiGreeting)
            speakAi(initialAiGreeting)

            // If running a simulated scenario, drive dialogue progression smoothly
            if (scenario != null) {
                runScenarioDialogue(scenario, language, persona)
            }
        }
    }

    private fun runScenarioDialogue(scenario: SimulationScenario, language: String, persona: String) {
        dialogueSequenceJob?.cancel()
        dialogueSequenceJob = scope.launch(Dispatchers.Main) {
            // Wait for AI greeting to finish
            delay(3500)
            if (!isActive || _sessionState.value.state != ScreeningState.SCREENING_ACTIVE) return@launch

            // Caller speaks initial statement
            addTranscriptLine(TranscriptSpeaker.CALLER, scenario.initialStatement)

            // Dinthi AI processes caller statement via Gemini backend
            val result = callRepository.processScreenTurn(
                callerStatement = scenario.initialStatement,
                conversationHistory = _sessionState.value.transcript,
                callerInfo = scenario.callerProfile,
                language = language,
                persona = persona
            )

            updateSessionAfterTurn(result)
            delay(1000)
            if (!isActive || _sessionState.value.state != ScreeningState.SCREENING_ACTIVE) return@launch

            addTranscriptLine(TranscriptSpeaker.AI, result.replyText)
            speakAi(result.replyText)

            // Next turns in scenario
            for (turnText in scenario.dialogueTurns) {
                delay(4000)
                if (!isActive || _sessionState.value.state != ScreeningState.SCREENING_ACTIVE) return@launch

                addTranscriptLine(TranscriptSpeaker.CALLER, turnText)

                val nextResult = callRepository.processScreenTurn(
                    callerStatement = turnText,
                    conversationHistory = _sessionState.value.transcript,
                    callerInfo = scenario.callerProfile,
                    language = language,
                    persona = persona
                )

                updateSessionAfterTurn(nextResult)
                delay(800)
                if (!isActive || _sessionState.value.state != ScreeningState.SCREENING_ACTIVE) return@launch

                addTranscriptLine(TranscriptSpeaker.AI, nextResult.replyText)
                speakAi(nextResult.replyText)
            }
        }
    }

    fun submitCallerStatement(statement: String, language: String, persona: String) {
        if (statement.isBlank()) return
        scope.launch(Dispatchers.Main) {
            addTranscriptLine(TranscriptSpeaker.CALLER, statement)

            val result = callRepository.processScreenTurn(
                callerStatement = statement,
                conversationHistory = _sessionState.value.transcript,
                callerInfo = _sessionState.value.callerProfile,
                language = language,
                persona = persona
            )

            updateSessionAfterTurn(result)
            addTranscriptLine(TranscriptSpeaker.AI, result.replyText)
            speakAi(result.replyText)
        }
    }

    fun injectUserIntervention(promptText: String, language: String, persona: String) {
        scope.launch(Dispatchers.Main) {
            // Log user instruction
            addTranscriptLine(TranscriptSpeaker.USER, promptText)

            val statement = "The recipient instructs: $promptText"
            val result = callRepository.processScreenTurn(
                callerStatement = statement,
                conversationHistory = _sessionState.value.transcript,
                callerInfo = _sessionState.value.callerProfile,
                language = language,
                persona = persona
            )

            updateSessionAfterTurn(result)
            addTranscriptLine(TranscriptSpeaker.AI, result.replyText)
            speakAi(result.replyText)
        }
    }

    fun takeOverCall() {
        dialogueSequenceJob?.cancel()
        ttsManager.stop()
        val current = _sessionState.value
        _sessionState.value = current.copy(
            state = ScreeningState.USER_TAKEOVER,
            isAiSpeaking = false,
            currentAiSpokenText = ""
        )
        scope.launch(Dispatchers.Main) {
            addTranscriptLine(TranscriptSpeaker.USER, "[User took over the call]")
        }
    }

    fun endCall() {
        dialogueSequenceJob?.cancel()
        durationJob?.cancel()
        ttsManager.stop()

        val current = _sessionState.value
        if (current.state == ScreeningState.IDLE) return

        _sessionState.value = current.copy(
            state = ScreeningState.CALL_ENDED,
            isAiSpeaking = false
        )

        scope.launch(Dispatchers.IO) {
            val transcriptSnapshot = current.transcript
            val duration = current.durationSeconds

            // Generate full intelligence summary from Gemini
            val summaryResult = callRepository.generateCallSummary(
                callId = current.callId,
                conversation = transcriptSnapshot,
                callerInfo = current.callerProfile,
                durationSeconds = duration
            )

            // Save call record to Room
            val actionsJsonArray = JSONArray()
            for (act in summaryResult.recommendedActions) {
                val obj = org.json.JSONObject()
                obj.put("type", act.type)
                obj.put("title", act.title)
                obj.put("detail", act.detail)
                actionsJsonArray.put(obj)
            }

            val keyDetailsJsonArray = JSONArray()
            for (kd in summaryResult.keyDetails) {
                keyDetailsJsonArray.put(kd)
            }

            val callStatus = when {
                current.isDemoSimulation -> CallStatus.DEMO_SIMULATOR.name
                summaryResult.isSpam -> CallStatus.SPAM_BLOCKED.name
                current.state == ScreeningState.USER_TAKEOVER -> CallStatus.ANSWERED.name
                else -> CallStatus.SCREENED.name
            }

            val summaryPrefix = if (current.isDemoSimulation && !summaryResult.summary.startsWith("[DEMO")) "[DEMO / SIMULATOR] " else ""

            val callEntity = CallEntity(
                id = current.callId,
                callerName = current.callerProfile.name,
                phoneNumber = current.callerProfile.phoneNumber,
                timestamp = System.currentTimeMillis(),
                durationSeconds = duration,
                status = callStatus,
                isSpam = summaryResult.isSpam,
                spamScore = summaryResult.spamScore,
                category = if (current.isDemoSimulation) "Demo/Simulator" else summaryResult.category,
                urgency = summaryResult.urgency,
                summary = "$summaryPrefix${summaryResult.summary}",
                reasonForCall = summaryResult.reasonForCall,
                keyDetailsJson = keyDetailsJsonArray.toString(),
                actionsJson = actionsJsonArray.toString(),
                isContact = current.callerProfile.isContact,
                isDemo = current.isDemoSimulation
            )

            callRepository.saveCall(callEntity)

            // Notify user for important / non-spam calls
            if (summaryResult.urgency.equals("High", ignoreCase = true) || !summaryResult.isSpam) {
                DinthiNotificationHelper.showUrgentCallNotification(
                    context = context,
                    callId = current.callId,
                    callerName = current.callerProfile.name,
                    reason = summaryResult.reasonForCall,
                    phoneNumber = current.callerProfile.phoneNumber
                )
            }

            _sessionState.value = _sessionState.value.copy(
                finalSummary = summaryResult
            )
        }
    }

    fun resetSession() {
        dialogueSequenceJob?.cancel()
        durationJob?.cancel()
        ttsManager.stop()
        _sessionState.value = ActiveScreeningSession()
    }

    private suspend fun addTranscriptLine(speaker: TranscriptSpeaker, text: String) {
        val line = TranscriptLineEntity(
            callId = _sessionState.value.callId,
            speaker = speaker.name,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        // Save to DB
        callRepository.addTranscriptLine(line)

        val updated = _sessionState.value.transcript + line
        _sessionState.value = _sessionState.value.copy(transcript = updated)
    }

    private fun updateSessionAfterTurn(result: ScreenTurnResult) {
        _sessionState.value = _sessionState.value.copy(
            detectedIntent = result.intent,
            spamScore = result.spamScore,
            isSpamDetected = result.isSpam,
            quickReplies = result.suggestedQuickReplies
        )
    }

    private fun speakAi(text: String) {
        _sessionState.value = _sessionState.value.copy(
            isAiSpeaking = true,
            currentAiSpokenText = text
        )
        ttsManager.speak(text) {
            _sessionState.value = _sessionState.value.copy(
                isAiSpeaking = false,
                currentAiSpokenText = ""
            )
        }
    }

    private fun startTimer() {
        durationJob?.cancel()
        durationJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                val curr = _sessionState.value
                if (curr.state == ScreeningState.SCREENING_ACTIVE || curr.state == ScreeningState.USER_TAKEOVER) {
                    _sessionState.value = curr.copy(durationSeconds = curr.durationSeconds + 1)
                } else {
                    break
                }
            }
        }
    }
}
