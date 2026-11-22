package com.mitra.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = dagger.hilt.android.testing.HiltTestApplication::class)
class Phase4UiNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun `home hub navigates across all screens and exercises in-call dtmf and edge probe`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_telemetry_card").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Mitra AI Hub").onFirst().assertIsDisplayed()

        // Navigate to In-Call Console via FAB and send DTMF tone + utterance
        composeTestRule.onNodeWithTag("fab_new_call").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dtmf_key_2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dtmf_history_badge").assertIsDisplayed()

        composeTestRule.onNodeWithTag("call_utterance_input").performTextInput("Oprima 3 para soporte")
        composeTestRule.onNodeWithTag("send_utterance_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate to SMS Threads
        composeTestRule.onNodeWithTag("nav_bar_sms").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sms_test_phishing_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate to History
        composeTestRule.onNodeWithTag("nav_bar_history").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("export_csv_button").assertIsDisplayed()

        // Navigate to Voice Studio, scroll LazyColumn so Edge Diagnostic button is centered, and run edge probe
        composeTestRule.onNodeWithTag("nav_bar_voice").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("voice_training_list")
            .performScrollToNode(hasTestTag("run_local_diag_button"))
        composeTestRule.onNodeWithTag("voice_training_list")
            .performTouchInput { swipeUp(startY = centerY, endY = top) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("run_local_diag_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            ShadowLooper.idleMainLooper()
            composeTestRule.onAllNodesWithTag("local_diag_output").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("voice_training_list")
            .performScrollToNode(hasTestTag("local_diag_output"))
        composeTestRule.onNodeWithTag("local_diag_output").assertIsDisplayed()

        // Navigate back to Hub and open Settings screen
        composeTestRule.onNodeWithTag("nav_bar_hub").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen_list")
            .performScrollToNode(hasTestTag("card_capabilities_settings"))
        composeTestRule.onNodeWithTag("card_capabilities_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("provider_chip_Groq").assertIsDisplayed()
    }
}
