package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CallEntity
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.TranscriptLineEntity
import com.example.data.model.CallerProfile
import com.example.data.model.OutboundCallSession
import com.example.data.model.OutboundCallStatus
import com.example.data.model.TelephonyStatusResponse
import com.example.data.model.TranscriptEvent
import com.example.data.repository.CallRepository
import com.example.data.repository.ConversationRepository
import com.example.data.repository.GeminiAiRepository
import com.example.data.repository.LocalCallRepository
import com.example.data.repository.LocalConversationRepository
import com.example.preferences.ThemeMode
import com.example.preferences.UserPreferences
import com.example.telecom.ActiveScreeningSession
import com.example.telecom.CallScreeningEngine
import com.example.telecom.ScreeningState
import com.example.telecom.SimulationScenario
import com.example.telecom.SimulatorScenarios
import com.example.ui.navigation.Screen
import com.example.voice.SpeechRecognizerManager
import com.example.voice.TextToSpeechManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "MainViewModel"

    val preferences = UserPreferences(application)
    val speechRecognizer = SpeechRecognizerManager(application)
    val textToSpeech = TextToSpeechManager(application)

    private val database = AppDatabase.getInstance(application)
    val callRepository: CallRepository = LocalCallRepository(
        context = application,
        callDao = database.callDao(),
        getServerUrl = { preferences.serverUrl.value }
    )

    val conversationRepository: ConversationRepository = LocalConversationRepository(database)
    val aiRepository = GeminiAiRepository(
        context = application,
        getServerUrl = { preferences.serverUrl.value }
    )

    // Call Screening Engine
    val screeningEngine = CallScreeningEngine(
        context = application,
        callRepository = callRepository,
        ttsManager = textToSpeech,
        scope = viewModelScope
    )

    val activeSession: StateFlow<ActiveScreeningSession> = screeningEngine.sessionState

    // Navigation Stack (default starting screen is Dashboard)
    private val screenStack = mutableListOf<Screen>(Screen.Dashboard)
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Dashboard)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Call Logs & Stats
    val allCalls: StateFlow<List<CallEntity>> = callRepository.getAllCalls()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val screenedCount: StateFlow<Int> = callRepository.getScreenedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val spamBlockedCount: StateFlow<Int> = callRepository.getSpamBlockedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Search and Filtering (Default shows only Real Calls; Demo/Simulator calls stay isolated)
    val searchQuery = MutableStateFlow("")
    val selectedCategoryFilter = MutableStateFlow("ALL_REAL")

    val filteredCalls: StateFlow<List<CallEntity>> = combine(
        allCalls,
        searchQuery,
        selectedCategoryFilter
    ) { calls, query, category ->
        calls.filter { call ->
            val matchesQuery = query.isBlank() ||
                call.callerName.contains(query, ignoreCase = true) ||
                call.phoneNumber.contains(query, ignoreCase = true) ||
                call.summary.contains(query, ignoreCase = true) ||
                call.category.contains(query, ignoreCase = true)

            val isDemoCall = call.isDemo || call.status == "DEMO_SIMULATOR"

            val matchesCategory = when (category) {
                "ALL_REAL", "ALL" -> !isDemoCall
                "REAL_AI" -> !isDemoCall && (call.status == "OUTGOING_AI" || call.status == "SCREENED" || call.status == "SPAM_BLOCKED")
                "REAL_HUMAN" -> !isDemoCall && (call.status == "OUTGOING_HUMAN" || call.status == "ANSWERED")
                "FAILED" -> !isDemoCall && call.status == "OUTGOING_FAILED"
                "DEMO" -> isDemoCall
                "SPAM" -> !isDemoCall && call.isSpam
                else -> !isDemoCall && call.category.equals(category, ignoreCase = true)
            }

            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Call Detail ID
    private val _viewingCallId = MutableStateFlow<String?>(null)
    val viewingCallId: StateFlow<String?> = _viewingCallId.asStateFlow()

    val viewingCall: StateFlow<CallEntity?> = _viewingCallId.flatMapLatest { id ->
        if (id != null) callRepository.getCallById(id) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val viewingTranscript: StateFlow<List<TranscriptLineEntity>> = _viewingCallId.flatMapLatest { id ->
        if (id != null) callRepository.getTranscriptForCall(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Scheduled Calls
    val allScheduledCalls: StateFlow<List<com.example.data.local.entity.ScheduledCallEntity>> =
        callRepository.getAllScheduledCalls().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcomingScheduledCalls: StateFlow<List<com.example.data.local.entity.ScheduledCallEntity>> =
        callRepository.getUpcomingScheduledCalls().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Voice Notes
    val allVoiceNotes: StateFlow<List<com.example.data.local.entity.VoiceNoteEntity>> =
        callRepository.getAllVoiceNotes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Outbound Call Planning
    private val _outboundPlan = MutableStateFlow<com.example.data.model.OutboundCallPlan?>(null)
    val outboundPlan: StateFlow<com.example.data.model.OutboundCallPlan?> = _outboundPlan.asStateFlow()

    private val _isPlanningOutbound = MutableStateFlow(false)
    val isPlanningOutbound: StateFlow<Boolean> = _isPlanningOutbound.asStateFlow()

    // Server Diagnostics
    private val _serverHealthStatus = MutableStateFlow<String?>(null)
    val serverHealthStatus: StateFlow<String?> = _serverHealthStatus.asStateFlow()

    // Outbound Telephony & AI Calling State
    private val _activeOutboundSession = MutableStateFlow<OutboundCallSession?>(null)
    val activeOutboundSession: StateFlow<OutboundCallSession?> = _activeOutboundSession.asStateFlow()

    private val _telephonyStatus = MutableStateFlow<TelephonyStatusResponse?>(null)
    val telephonyStatus: StateFlow<TelephonyStatusResponse?> = _telephonyStatus.asStateFlow()

    private val _isOutboundConnecting = MutableStateFlow(false)
    val isOutboundConnecting: StateFlow<Boolean> = _isOutboundConnecting.asStateFlow()

    private val _isOutboundMuted = MutableStateFlow(false)
    val isOutboundMuted: StateFlow<Boolean> = _isOutboundMuted.asStateFlow()

    private val _isOutboundSpeakerOn = MutableStateFlow(true)
    val isOutboundSpeakerOn: StateFlow<Boolean> = _isOutboundSpeakerOn.asStateFlow()

    private val _isUserTakenOver = MutableStateFlow(false)
    val isUserTakenOver: StateFlow<Boolean> = _isUserTakenOver.asStateFlow()

    private val _outboundErrorMessage = MutableStateFlow<String?>(null)
    val outboundErrorMessage: StateFlow<String?> = _outboundErrorMessage.asStateFlow()

    private var outboundPollingJob: Job? = null

    // Chat / Assistant Flow (retained for auxiliary assistance)
    val conversations: StateFlow<List<ConversationEntity>> = conversationRepository.getConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    val activeMessages: StateFlow<List<MessageEntity>> = _activeConversationId.flatMapLatest { id ->
        if (id != null) conversationRepository.getMessages(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private var generationJob: Job? = null
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _attachedImageUri = MutableStateFlow<String?>(null)
    val attachedImageUri: StateFlow<String?> = _attachedImageUri.asStateFlow()

    init {
        // Sync preferences with TTS engine
        viewModelScope.launch {
            preferences.speechRate.collect { rate ->
                textToSpeech.speechRate = rate
            }
        }
        viewModelScope.launch {
            preferences.speechPitch.collect { pitch ->
                textToSpeech.speechPitch = pitch
            }
        }

        // Seed sample calls on first launch so the dashboard is immediately populated
        viewModelScope.launch {
            callRepository.seedSampleCallsIfEmpty()
        }

        // Listen for incoming call events in screening engine to navigate to LiveScreening
        viewModelScope.launch {
            screeningEngine.sessionState.collect { session ->
                if (session.state == ScreeningState.INCOMING_ALERT || session.state == ScreeningState.SCREENING_ACTIVE) {
                    if (_currentScreen.value !is Screen.LiveScreening) {
                        navigateTo(Screen.LiveScreening(session.callId))
                    }
                }
            }
        }
    }

    // Navigation Methods
    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            screenStack.add(screen)
            _currentScreen.value = screen
            if (screen is Screen.CallDetail) {
                _viewingCallId.value = screen.callId
            } else if (screen is Screen.Chat) {
                _activeConversationId.value = screen.conversationId
            }
        }
    }

    fun navigateBack(): Boolean {
        textToSpeech.stop()
        speechRecognizer.stopListening()
        if (screenStack.size > 1) {
            screenStack.removeAt(screenStack.size - 1)
            val previous = screenStack.last()
            _currentScreen.value = previous
            if (previous is Screen.CallDetail) {
                _viewingCallId.value = previous.callId
            } else if (previous is Screen.Chat) {
                _activeConversationId.value = previous.conversationId
            }
            return true
        }
        return false
    }

    // Call Screening Simulator Triggers (strictly Demo/Simulator mode)
    fun launchSimulation(scenarioId: String) {
        val scenario = SimulatorScenarios.scenarios.find { it.id == scenarioId }
            ?: SimulatorScenarios.scenarios.first()

        screeningEngine.initiateIncomingCall(scenario.callerProfile, isDemoSimulation = true)
        navigateTo(Screen.LiveScreening(screeningEngine.sessionState.value.callId))

        // Auto screen with Dinthi AI after short ring
        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            screeningEngine.acceptScreenWithAi(
                language = preferences.selectedLanguage.value,
                persona = preferences.voicePersona.value,
                scenario = scenario
            )
        }
    }

    fun launchCustomSimulation(callerName: String, callerNumber: String, statement: String) {
        val customProfile = CallerProfile(
            name = callerName.ifBlank { "Custom Caller" },
            phoneNumber = callerNumber.ifBlank { "+91 99000 11223" },
            isContact = false,
            initialSpamRisk = false
        )
        val customScenario = SimulationScenario(
            id = "custom",
            title = customProfile.name,
            description = "Custom simulated incoming call",
            callerProfile = customProfile,
            initialStatement = statement.ifBlank { "Hello, I am calling regarding your account." },
            dialogueTurns = listOf("Could you please confirm your availability for a follow-up?"),
            defaultCategory = "General"
        )
        screeningEngine.initiateIncomingCall(customProfile, isDemoSimulation = true)
        navigateTo(Screen.LiveScreening(screeningEngine.sessionState.value.callId))

        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            screeningEngine.acceptScreenWithAi(
                language = preferences.selectedLanguage.value,
                persona = preferences.voicePersona.value,
                scenario = customScenario
            )
        }
    }

    fun acceptIncomingCallWithAi() {
        screeningEngine.acceptScreenWithAi(
            language = preferences.selectedLanguage.value,
            persona = preferences.voicePersona.value
        )
    }

    fun sendUserIntervention(text: String) {
        screeningEngine.injectUserIntervention(
            promptText = text,
            language = preferences.selectedLanguage.value,
            persona = preferences.voicePersona.value
        )
    }

    fun takeOverCall() {
        screeningEngine.takeOverCall()
    }

    fun endActiveCall() {
        screeningEngine.endCall()
    }

    fun deleteCall(callId: String) {
        viewModelScope.launch {
            callRepository.deleteCall(callId)
            if (_viewingCallId.value == callId) {
                navigateBack()
            }
        }
    }

    fun clearCallHistory() {
        viewModelScope.launch {
            callRepository.clearHistory()
        }
    }

    // Scheduled Calls Management
    fun scheduleCall(
        recipientName: String,
        phoneNumber: String,
        timestamp: Long,
        agenda: String,
        reminderMinutes: Int = 10
    ) {
        val entity = com.example.data.local.entity.ScheduledCallEntity(
            id = UUID.randomUUID().toString(),
            recipientName = recipientName.ifBlank { "Scheduled Recipient" },
            phoneNumber = phoneNumber.ifBlank { "+91 99000 00000" },
            scheduledTimestamp = timestamp,
            agenda = agenda.ifBlank { "Scheduled follow-up call" },
            reminderMinutesBefore = reminderMinutes
        )
        viewModelScope.launch {
            callRepository.saveScheduledCall(entity)
        }
    }

    fun modifyScheduledCall(call: com.example.data.local.entity.ScheduledCallEntity) {
        viewModelScope.launch {
            callRepository.updateScheduledCall(call)
        }
    }

    fun cancelScheduledCall(callId: String) {
        viewModelScope.launch {
            val list = allScheduledCalls.value
            val target = list.find { it.id == callId }
            if (target != null) {
                callRepository.updateScheduledCall(target.copy(isCancelled = true))
            }
        }
    }

    fun deleteScheduledCall(callId: String) {
        viewModelScope.launch {
            callRepository.deleteScheduledCall(callId)
        }
    }

    // Voice Notes Management
    fun createVoiceNote(transcription: String, durationSeconds: Int = 10) {
        if (transcription.isBlank()) return
        viewModelScope.launch {
            val summaryResult = callRepository.summarizeVoiceNote(transcription)
            val entity = com.example.data.local.entity.VoiceNoteEntity(
                id = UUID.randomUUID().toString(),
                title = summaryResult.title,
                transcription = transcription,
                summary = summaryResult.summary,
                durationSeconds = durationSeconds,
                tagsJson = org.json.JSONArray(summaryResult.tags).toString()
            )
            callRepository.saveVoiceNote(entity)
        }
    }

    fun deleteVoiceNote(noteId: String) {
        viewModelScope.launch {
            callRepository.deleteVoiceNote(noteId)
        }
    }

    fun clearAllVoiceNotes() {
        viewModelScope.launch {
            callRepository.clearAllVoiceNotes()
        }
    }

    // Outbound Call Planning
    fun planOutboundCall(
        recipientName: String,
        phoneNumber: String,
        goal: String
    ) {
        viewModelScope.launch {
            _isPlanningOutbound.value = true
            val plan = callRepository.planOutboundCall(
                recipientName = recipientName,
                phoneNumber = phoneNumber,
                goal = goal,
                language = preferences.selectedLanguage.value
            )
            _outboundPlan.value = plan
            _isPlanningOutbound.value = false
        }
    }

    fun clearOutboundPlan() {
        _outboundPlan.value = null
    }

    // ==========================================================
    // REAL OUTBOUND AI TELEPHONY METHODS
    // ==========================================================

    fun fetchTelephonyStatus() {
        viewModelScope.launch {
            val status = callRepository.getTelephonyStatus()
            _telephonyStatus.value = status
        }
    }

    fun clearOutboundError() {
        _outboundErrorMessage.value = null
    }

    fun startOutboundCall(
        recipientName: String,
        phoneNumber: String,
        goal: String,
        notes: String,
        mode: String
    ) {
        viewModelScope.launch {
            outboundPollingJob?.cancel()
            _isOutboundConnecting.value = true
            _outboundErrorMessage.value = null
            _isUserTakenOver.value = false
            _isOutboundMuted.value = false

            val displayUser = preferences.userDisplayName.value.ifBlank { "the user" }
            val session = callRepository.initiateOutboundCall(
                recipientName = recipientName.ifBlank { phoneNumber },
                phoneNumber = phoneNumber.trim(),
                userName = displayUser,
                goal = goal.ifBlank { "General Inquiry" },
                notes = notes.trim(),
                mode = mode,
                language = preferences.selectedLanguage.value,
                persona = preferences.voicePersona.value
            )

            _isOutboundConnecting.value = false
            if (session != null) {
                _activeOutboundSession.value = session
                navigateTo(Screen.OutboundCall(session.id))

                val isTerminal = session.status == OutboundCallStatus.COMPLETED.name ||
                    session.status == OutboundCallStatus.FAILED.name

                if (mode == "AI" && !isTerminal) {
                    startOutboundSessionPolling(session.id)
                }
            } else if (mode == "HUMAN") {
                val now = System.currentTimeMillis()
                val localHumanSession = OutboundCallSession(
                    id = "human_${now}",
                    recipientName = recipientName.ifBlank { phoneNumber },
                    phoneNumber = phoneNumber.trim(),
                    userName = displayUser,
                    goal = goal.ifBlank { "General Inquiry" },
                    notes = notes.trim(),
                    mode = "HUMAN",
                    language = preferences.selectedLanguage.value,
                    persona = preferences.voicePersona.value,
                    status = OutboundCallStatus.CONNECTED.name,
                    startTime = now,
                    durationSeconds = 0,
                    transcript = listOf(
                        TranscriptEvent(
                            id = "evt_human_$now",
                            speaker = "system",
                            text = "Direct call handed off to your device phone dialer so you can speak with ${recipientName.ifBlank { phoneNumber }}.",
                            timestamp = now
                        )
                    ),
                    summary = null
                )
                _activeOutboundSession.value = localHumanSession
                navigateTo(Screen.OutboundCall(localHumanSession.id))
            } else {
                _outboundErrorMessage.value = "Could not reach Dinthi Secure Backend. Outbound AI calling requires a live backend and configured telephony provider."
            }
        }
    }

    private fun startOutboundSessionPolling(sessionId: String) {
        outboundPollingJob?.cancel()
        outboundPollingJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1500)
                val updated = callRepository.getOutboundSession(sessionId) ?: continue
                _activeOutboundSession.value = updated
                if (updated.status == OutboundCallStatus.COMPLETED.name ||
                    updated.status == OutboundCallStatus.FAILED.name
                ) {
                    break
                }
            }
        }
    }

    fun refreshOutboundSession(sessionId: String) {
        viewModelScope.launch {
            val updated = callRepository.getOutboundSession(sessionId)
            if (updated != null) {
                _activeOutboundSession.value = updated
            }
        }
    }

    fun toggleOutboundMute() {
        _isOutboundMuted.value = !_isOutboundMuted.value
        if (_isOutboundMuted.value) {
            textToSpeech.stop()
        }
    }

    fun toggleOutboundSpeaker() {
        _isOutboundSpeakerOn.value = !_isOutboundSpeakerOn.value
    }

    fun endOutboundCall(reason: String = "user_ended") {
        val session = _activeOutboundSession.value ?: return
        outboundPollingJob?.cancel()
        viewModelScope.launch {
            textToSpeech.stop()
            speechRecognizer.stopListening()
            val ended = callRepository.endOutboundCall(session.id, reason)
            if (ended != null) {
                _activeOutboundSession.value = ended
            } else {
                val actualDuration = ((System.currentTimeMillis() - session.startTime) / 1000L).toInt().coerceAtLeast(1)
                val fallbackSummary = com.example.data.model.OutboundCallSummary(
                    summary = if (session.mode == "HUMAN") {
                        "Direct human call via device dialer to ${session.recipientName} regarding \"${session.goal}\"."
                    } else {
                        "Outbound AI call session ended (${actualDuration}s)."
                    },
                    reasonForCall = session.goal,
                    outcome = "Completed",
                    importantDetails = listOf(
                        "Call Mode: ${if (session.mode == "HUMAN") "Human Talk (Device Dialer)" else "AI Talk"}",
                        "Actual Duration: ${actualDuration}s"
                    ),
                    decisions = emptyList(),
                    actionItems = if (session.notes.isNotBlank()) listOf("Note: ${session.notes}") else emptyList(),
                    sentiment = "Neutral",
                    durationSeconds = actualDuration
                )
                val finalized = session.copy(
                    status = OutboundCallStatus.COMPLETED.name,
                    durationSeconds = actualDuration,
                    summary = fallbackSummary
                )
                _activeOutboundSession.value = finalized
                callRepository.saveOutboundSessionRecord(finalized)
            }
        }
    }


    fun enableDemoMode() {
        viewModelScope.launch {
            val localRepo = callRepository as? LocalCallRepository
            localRepo?.seedDemoDataExplicitly()
        }
    }

    fun checkServerHealth() {
        testServerConnection()
    }

    fun testServerConnection() {
        viewModelScope.launch {
            _serverHealthStatus.value = "Testing server connection..."
            val result = aiRepository.checkServerHealth(preferences.serverUrl.value)
            _serverHealthStatus.value = result.getOrElse { "Connection Failed: ${it.message}" }
        }
    }

    // Auxiliary Chat Methods
    fun setInputText(text: String) { _inputText.value = text }
    fun setAttachedImage(uri: String?) { _attachedImageUri.value = uri }

    fun startNewChat(initialPrompt: String? = null, initialImageUri: String? = null) {
        val newId = UUID.randomUUID().toString()
        val conversation = ConversationEntity(
            id = newId,
            title = initialPrompt?.take(30)?.trim()?.plus("...") ?: "New Chat",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            previewMessage = initialPrompt.orEmpty()
        )
        viewModelScope.launch {
            conversationRepository.createOrUpdateConversation(conversation)
            _activeConversationId.value = newId
            navigateTo(Screen.Chat(newId))
            if (!initialPrompt.isNullOrBlank() || initialImageUri != null) {
                sendMessage(newId, initialPrompt.orEmpty(), initialImageUri)
            }
        }
    }

    fun sendMessage(conversationId: String, prompt: String, imageUri: String? = null) {
        if (prompt.isBlank() && imageUri == null) return
        val userMsg = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = "user",
            content = prompt.trim(),
            timestamp = System.currentTimeMillis(),
            imageUri = imageUri
        )
        _inputText.value = ""
        _attachedImageUri.value = null
        viewModelScope.launch {
            conversationRepository.saveMessage(userMsg)
            streamAiResponse(conversationId, prompt, imageUri)
        }
    }

    private fun streamAiResponse(conversationId: String, prompt: String, imageUri: String?) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _streamingText.value = ""
            try {
                val history = conversationRepository.getMessagesList(conversationId)
                val persona = preferences.aiPersona.value
                val accumulated = StringBuilder()
                aiRepository.generateResponseStream(prompt, history, persona, imageUri).collect { chunk ->
                    accumulated.append(chunk)
                    _streamingText.value = accumulated.toString()
                }
                if (accumulated.isNotEmpty()) {
                    val aiMessage = MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        role = "assistant",
                        content = accumulated.toString(),
                        timestamp = System.currentTimeMillis()
                    )
                    conversationRepository.saveMessage(aiMessage)
                }
            } catch (e: Exception) {
                Log.e(tag, "Streaming error", e)
            } finally {
                _isGenerating.value = false
                _streamingText.value = ""
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        _isGenerating.value = false
    }

    fun speakText(text: String, id: String = UUID.randomUUID().toString()) {
        textToSpeech.speak(text, id)
    }

    fun toggleVoiceInput() {
        if (speechRecognizer.isListening.value) {
            speechRecognizer.stopListening()
        } else {
            speechRecognizer.startListening()
        }
    }

    fun stopVoiceInput() {
        speechRecognizer.stopListening()
    }

    fun speakMessage(message: MessageEntity) {
        textToSpeech.speak(message.content, message.id)
    }

    fun stopSpeaking() {
        textToSpeech.stop()
    }

    fun regenerateLastResponse(conversationId: String) {
        if (_isGenerating.value) return
        viewModelScope.launch {
            val messages = conversationRepository.getMessagesList(conversationId)
            if (messages.isEmpty()) return@launch
            val lastUserMessage = messages.lastOrNull { it.role == "user" } ?: return@launch
            conversationRepository.deleteMessagesFrom(conversationId, lastUserMessage.timestamp + 1)
            streamAiResponse(
                conversationId = conversationId,
                prompt = lastUserMessage.content,
                imageUri = lastUserMessage.imageUri
            )
        }
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        if (newTitle.isNotBlank()) {
            viewModelScope.launch {
                conversationRepository.renameConversation(conversationId, newTitle.trim())
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            textToSpeech.stop()
            conversationRepository.deleteConversation(conversationId)
            if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = null
                navigateBack()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        outboundPollingJob?.cancel()
        speechRecognizer.cancel()
        textToSpeech.shutdown()
    }
}
