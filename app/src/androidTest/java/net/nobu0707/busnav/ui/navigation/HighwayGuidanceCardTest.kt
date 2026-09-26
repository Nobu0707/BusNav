package net.nobu0707.busnav.ui.navigation

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import net.nobu0707.busnav.domain.navigation.HighwaySignDisplay
import net.nobu0707.busnav.ui.theme.BusNavTheme
import org.junit.Rule
import org.junit.Test

class HighwayGuidanceCardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun portraitSignsDirectionAndMap() = exercise(false, false)
    @Test fun landscapeSignsDirectionAndMap() = exercise(true, false)
    @Test fun portraitUncertainSuppressesSchematic() = exercise(false, true)
    @Test fun landscapeUncertainSuppressesSchematic() = exercise(true, true)
    private fun exercise(landscape: Boolean, uncertain: Boolean) {
        rule.runOnUiThread { rule.activity.requestedOrientation = if (landscape)
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        rule.waitUntil(5000) { rule.activity.resources.configuration.orientation == if (landscape)
            Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }
        val highway = if (uncertain) HighwayGuidanceUiState("経路上の位置を確認中") else HighwayGuidanceUiState(
            "分岐を左方向へ", "700 m", HighwaySignDisplay("12", listOf("E20"), listOf("甲府方面"), listOf("八王子JCT")),
            nextText = "出口 · 1.6 km", schematic = JunctionSchematicModel(SchematicDirection.LEFT))
        rule.setContent { BusNavTheme { NavigationScreen(NavigationUiState(highwayGuidance = highway,
            locationPermissionState = LocationPermissionState.Granted), {}, {}, {}, {}, mapContent = { Box(it) }) } }
        rule.onNodeWithTag(NavigationTestTags.MAP).assertIsDisplayed()
        rule.onNodeWithContentDescription(highway.contentDescription).assertIsDisplayed()
        if (uncertain) {
            rule.onNodeWithTag("highway_schematic").assertDoesNotExist()
            rule.onNodeWithTag("highway_distance").assertDoesNotExist()
            rule.onNodeWithTag("highway_next").assertDoesNotExist()
        } else {
            rule.onNodeWithTag("highway_schematic", useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithText("700 m", useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithText("E20", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        }
    }
}
