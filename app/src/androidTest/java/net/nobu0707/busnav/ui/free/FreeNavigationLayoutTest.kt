package net.nobu0707.busnav.ui.free

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import net.nobu0707.busnav.ui.theme.BusNavTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FreeNavigationLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test fun landscapeSelectionKeepsMapAtLeastThreeQuartersOfUsableWidth() {
        rule.setContent {
            val landscape = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            }
            CompositionLocalProvider(LocalConfiguration provides landscape) { BusNavTheme {
                Box(Modifier.requiredSize(900.dp, 400.dp)) {
                    FreeNavigationScreen(FreeNavigationUiState(stage = FreeNavigationStage.SELECTING),
                        {}, {}, {}, {}, {}, {}, false, false, false, null, false, true,
                        mapContent = { Box(it.testTag("free_test_map")) })
                }
            } }
        }
        val map = rule.onNodeWithTag("free_test_map").fetchSemanticsNode().boundsInRoot
        val screen = rule.onNodeWithTag("free_screen").fetchSemanticsNode().boundsInRoot
        assertTrue(map.width >= screen.width * 0.74f)
        assertTrue(map.width <= screen.width * 0.78f)
        rule.onNodeWithTag("free_set_destination").fetchSemanticsNode()
    }
}
