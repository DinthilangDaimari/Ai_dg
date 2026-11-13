package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import com.example.data.repository.MockAiRepository

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun mockAiRepository_generates_streaming_response() = runTest {
        val repo = MockAiRepository()
        val chunks = repo.generateResponseStream(
            prompt = "Write code in Kotlin",
            conversationHistory = emptyList(),
            persona = "Coder"
        ).toList()

        assertTrue("Chunks should not be empty", chunks.isNotEmpty())
        val combined = chunks.joinToString("")
        assertTrue("Response should contain solution", combined.contains("Solution") || combined.contains("Kotlin"))
    }

    @Test
    fun server_config_fields_contain_zero_static_secrets() {
        assertNotNull(BuildConfig.SERVER_BASE_URL)
        assertTrue(BuildConfig.SERVER_BASE_URL.startsWith("https://"))
        val declaredFieldNames = BuildConfig::class.java.declaredFields.map { it.name }
        assertTrue("BuildConfig must not embed APP_CLIENT_TOKEN", !declaredFieldNames.contains("APP_CLIENT_TOKEN"))
        assertTrue("BuildConfig must not embed GEMINI_API_KEY", !declaredFieldNames.contains("GEMINI_API_KEY"))
    }

    @Test
    fun simulator_scenarios_are_properly_configured() {
        val scenarios = com.example.telecom.SimulatorScenarios.scenarios
        assertTrue("Scenarios should contain multiple testing workflows", scenarios.size >= 4)
        val deliveryScenario = scenarios.find { it.id == "courier_delivery" }
        assertNotNull("Delivery scenario should exist", deliveryScenario)
        assertEquals("Amazon Courier Delivery", deliveryScenario?.title)

        val spamScenario = scenarios.find { it.id == "loan_spam" }
        assertNotNull("Spam scenario should exist", spamScenario)
        assertTrue("Spam scenario should flag spam expectation", spamScenario?.isSpamExpectation == true)
    }

    @Test
    fun outbound_call_status_and_models_are_valid() {
        // Verify exact 10 real outbound call statuses
        val statuses = com.example.data.model.OutboundCallStatus.values().map { it.name }
        assertEquals(10, statuses.size)
        assertTrue(statuses.contains("PREPARING"))
        assertTrue(statuses.contains("CALLING"))
        assertTrue(statuses.contains("RINGING"))
        assertTrue(statuses.contains("CONNECTED"))
        assertTrue(statuses.contains("AI_SPEAKING"))
        assertTrue(statuses.contains("LISTENING"))
        assertTrue(statuses.contains("PROCESSING"))
        assertTrue(statuses.contains("ENDING"))
        assertTrue(statuses.contains("COMPLETED"))
        assertTrue(statuses.contains("FAILED"))

        // Verify CallEntity statuses include outgoing & failed records
        val callStatuses = com.example.data.local.entity.CallStatus.values().map { it.name }
        assertTrue(callStatuses.contains("OUTGOING_AI"))
        assertTrue(callStatuses.contains("OUTGOING_HUMAN"))
        assertTrue(callStatuses.contains("OUTGOING_FAILED"))
    }

    @Test
    fun outbound_session_data_structures_work() {
        val event = com.example.data.model.TranscriptEvent(
            id = "evt_1",
            speaker = "dinthi_ai",
            text = "Hello, I'm Dinthi AI calling on behalf of user.",
            timestamp = System.currentTimeMillis()
        )
        val summary = com.example.data.model.OutboundCallSummary(
            summary = "Call completed successfully.",
            reasonForCall = "Confirm booking",
            outcome = "Completed",
            decisions = listOf("Appointment confirmed for 4 PM"),
            actionItems = listOf("Add to calendar"),
            sentiment = "Positive",
            durationSeconds = 65
        )
        val session = com.example.data.model.OutboundCallSession(
            id = "sess_123",
            recipientName = "Dr. Clinic",
            phoneNumber = "+919876543210",
            goal = "Confirm appointment",
            notes = "Patient ID 44",
            mode = "AI",
            language = "English",
            persona = "Professional",
            status = com.example.data.model.OutboundCallStatus.CONNECTED.name,
            startTime = System.currentTimeMillis(),
            durationSeconds = 65,
            transcript = listOf(event),
            summary = summary
        )

        assertEquals("sess_123", session.id)
        assertEquals("AI", session.mode)
        assertEquals(1, session.transcript.size)
        assertEquals("dinthi_ai", session.transcript.first().speaker)
        assertEquals("Completed", session.summary?.outcome)
    }

    @Test
    fun telephony_status_response_includes_trai_regulations() {
        val status = com.example.data.model.TelephonyStatusResponse(
            provider = "Twilio Voice",
            isConfigured = false,
            accountSid = null,
            fromNumber = null,
            aiReady = true,
            voiceReady = true,
            backendStatus = "ONLINE",
            traiCompliance = "TRAI regulations prohibit caller ID spoofing and automated spam to DND registry.",
            aiTransparency = "Dinthi AI always identifies itself as an artificial intelligence assistant."
        )

        assertEquals("Twilio Voice", status.provider)
        assertTrue(status.traiCompliance.contains("TRAI"))
        assertTrue(status.aiTransparency.contains("Dinthi AI"))
    }
}

