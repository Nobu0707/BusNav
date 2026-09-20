package net.nobu0707.busnav.ui.navigation

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import net.nobu0707.busnav.ui.theme.BusNavTheme
import org.junit.Rule
import org.junit.Test

class GuidanceCardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun portraitShowsInstructionDistanceRoadAndNextNext() = exercise(false, false)
    @Test fun landscapeShowsInstructionDistanceRoadAndNextNext() = exercise(true, false)
    @Test fun portraitUncertainSuppressesTurnAndDistance() = exercise(false, true)
    @Test fun landscapeUncertainSuppressesTurnAndDistance() = exercise(true, true)

    private fun exercise(landscape: Boolean, uncertain: Boolean) {
        rule.runOnUiThread { rule.activity.requestedOrientation = if (landscape)
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        rule.waitUntil(5000) { rule.activity.resources.configuration.orientation == if (landscape)
            Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }
        rule.waitForIdle()
        val guidance = if (uncertain) GuidanceUiState(GuidanceStatus.UNCERTAIN, "経路付近の位置を確認中")
            else GuidanceUiState(GuidanceStatus.RELIABLE, "右折", "国道1号", "350 m", symbol = "→", nextNextInstruction = "左折")
        rule.setContent {
            BusNavTheme {
                NavigationScreen(NavigationUiState(guidance = guidance, locationPermissionState = LocationPermissionState.Granted),
                    {}, {}, {}, {}, mapContent = { Box(it) })
            }
        }
        rule.onNodeWithTag(NavigationTestTags.MAP).assertIsDisplayed()
        if (uncertain) {
            rule.onNodeWithText("経路付近の位置を確認中").assertIsDisplayed()
            rule.onNodeWithTag("guidance_distance").assertDoesNotExist()
            rule.onNodeWithTag("guidance_next_next").assertDoesNotExist()
            rule.onNodeWithText("右折").assertDoesNotExist()
        } else {
            rule.onNodeWithText("右折").assertIsDisplayed()
            rule.onNodeWithText("350 m").assertIsDisplayed()
            rule.onNodeWithText("国道1号").assertIsDisplayed()
            rule.onNodeWithText("その次: 左折").assertIsDisplayed()
        }
    }
}
