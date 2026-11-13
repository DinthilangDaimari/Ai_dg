package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CallEntity
import com.example.data.local.entity.CallStatus
import com.example.data.model.CallSession
import com.example.data.model.CallSummary
import com.example.data.model.OutboundCallStatus
import com.example.data.model.TranscriptEvent
import com.example.ui.screens.CallSomeoneScreen
import com.example.ui.theme.DinthiTheme
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OutboundCallingRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun demo_calls_never_mix_into_real_call_counts() = runTest {
        val dao = db.callDao()
        val now = System.currentTimeMillis()

        dao.insertCall(
            CallEntity(
                id = "real_ai_1",
                callerName = "Real Contact",
                phoneNumber = "+919876543210",
                timestamp = now,
                durationSeconds = 45,
                status = CallStatus.SCREENED.name,
                isDemo = false
            )
        )
        dao.insertCall(
            CallEntity(
                id = "demo_sim_1",
                callerName = "Demo Simulator Caller",
                phoneNumber = "+911400000000",
                timestamp = now - 1000,
                durationSeconds = 30,
                status = CallStatus.DEMO_SIMULATOR.name,
                isSpam = true,
                spamScore = 95,
                category = "Demo/Simulator",
                isDemo = true
            )
        )

        val screenedCount = dao.getScreenedCount().first()
        val spamCount = dao.getSpamBlockedCount().first()
        assertEquals(1, screenedCount)
        assertEquals(0, spamCount)
    }

    @Test
    fun call_history_filters_strictly_separate_real_and_demo_calls() {
        val now = System.currentTimeMillis()
        val calls = listOf(
            CallEntity(id = "1", callerName = "AI Out", phoneNumber = "+911", timestamp = now, durationSeconds = 40, status = CallStatus.OUTGOING_AI.name, isDemo = false),
            CallEntity(id = "2", callerName = "Human Out", phoneNumber = "+912", timestamp = now, durationSeconds = 60, status = CallStatus.OUTGOING_HUMAN.name, isDemo = false),
            CallEntity(id = "3", callerName = "Failed Out", phoneNumber = "+913", timestamp = now, durationSeconds = 0, status = CallStatus.OUTGOING_FAILED.name, isDemo = false),
            CallEntity(id = "4", callerName = "Sim Demo", phoneNumber = "+914", timestamp = now, durationSeconds = 25, status = CallStatus.DEMO_SIMULATOR.name, isDemo = true)
        )

        val allReal = calls.filter { !it.isDemo && it.status != CallStatus.DEMO_SIMULATOR.name }
        val realAi = calls.filter { !it.isDemo && it.status == CallStatus.OUTGOING_AI.name }
        val realHuman = calls.filter { !it.isDemo && it.status == CallStatus.OUTGOING_HUMAN.name }
        val failed = calls.filter { !it.isDemo && it.status == CallStatus.OUTGOING_FAILED.name }
        val demo = calls.filter { it.isDemo || it.status == CallStatus.DEMO_SIMULATOR.name }

        assertEquals(3, allReal.size)
        assertFalse(allReal.any { it.id == "4" })
        assertEquals(listOf("1"), realAi.map { it.id })
        assertEquals(listOf("2"), realHuman.map { it.id })
        assertEquals(listOf("3"), failed.map { it.id })
        assertEquals(listOf("4"), demo.map { it.id })
    }

    @Test
    fun call_session_and_call_summary_typealiases_support_all_required_fields() {
        val summary = CallSummary(
            summary = "Confirmed parcel delivery window.",
            reasonForCall = "Coordinate parcel delivery",
            outcome = "Completed",
            importantDetails = listOf("Gate security can hold parcel"),
            decisions = listOf("Leave at Tower 2 reception"),
            actionItems = listOf("Collect parcel after 6 PM"),
            sentiment = "Positive",
            durationSeconds = 52
        )
        val session = CallSession(
            id = "call_101",
            recipientName = "Courier Desk",
            phoneNumber = "+919999988888",
            userName = "Alex",
            goal = "Coordinate parcel delivery",
            notes = "Keep concise",
            mode = "AI",
            language = "English (India)",
            persona = "Professional",
            status = OutboundCallStatus.COMPLETED.name,
            startTime = 1000L,
            durationSeconds = 52,
            transcript = listOf(
                TranscriptEvent(id = "e1", speaker = "dinthi_ai", text = "Hello, I'm Dinthi AI calling on behalf of Alex.", timestamp = 1100L),
                TranscriptEvent(id = "e2", speaker = "recipient", text = "Yes, leave it at reception.", timestamp = 1200L)
            ),
            summary = summary
        )

        assertEquals(52, session.summary?.durationSeconds)
        assertEquals("Coordinate parcel delivery", session.summary?.reasonForCall)
        assertEquals(1, session.summary?.importantDetails?.size)
        assertEquals(1, session.summary?.decisions?.size)
        assertEquals(1, session.summary?.actionItems?.size)
        assertTrue(session.transcript.any { it.speaker == "dinthi_ai" })
        assertTrue(session.transcript.any { it.speaker == "recipient" })
    }

    @Test
    fun callSomeoneScreen_displays_not_configured_banner_and_ai_disclosure() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = MainViewModel(app)

        composeTestRule.setContent {
            DinthiTheme(darkTheme = true) {
                CallSomeoneScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("telephony_not_configured_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_someone_list").performScrollToNode(hasTestTag("pick_device_contact_button"))
        composeTestRule.onNodeWithTag("pick_device_contact_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_someone_list").performScrollToNode(hasText("Dinthi AI will talk to the recipient."))
        composeTestRule.onNodeWithText("Dinthi AI will talk to the recipient.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_someone_list").performScrollToNode(hasTestTag("dial_call_action_button"))
        composeTestRule.onNodeWithTag("dial_call_action_button").assertIsDisplayed()
    }
}
