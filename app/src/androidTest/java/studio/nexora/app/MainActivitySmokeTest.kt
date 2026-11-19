package studio.nexora.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesAndOpensMotionTrackerPanel() {
        composeRule.onNodeWithText("NEXORA STUDIO").assertIsDisplayed()
        composeRule.onNodeWithText("Workspace").assertIsDisplayed()
        composeRule.onNodeWithText("Timeline").assertIsDisplayed()

        composeRule.onNodeWithText("Tracking").performScrollTo().performClick()
        composeRule.onNodeWithText("Motion Tracker").assertIsDisplayed()
        composeRule.onNodeWithText("Track video automatically").assertIsDisplayed()
    }
}
