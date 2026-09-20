package net.nobu0707.busnav.map

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import net.nobu0707.busnav.map.basemap.BasemapAttribution
import net.nobu0707.busnav.map.basemap.BasemapState
import net.nobu0707.busnav.ui.theme.BusNavTheme
import org.junit.Rule
import org.junit.Test

class BasemapOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unavailableStateIsVisibleWithoutReplacingMapContent() {
        composeRule.setContent {
            BusNavTheme {
                Box {
                    BasemapStatusOverlay(BasemapState.UNAVAILABLE)
                    BasemapAttributionOverlay()
                }
            }
        }

        composeRule.onNodeWithTag(BasemapTestTags.UNAVAILABLE).assertIsDisplayed()
        composeRule.onNodeWithText("\u8a73\u7d30\u5730\u56f3\u30b5\u30fc\u30d0\u30fc\u672a\u63a5\u7d9a").assertIsDisplayed()
        composeRule.onNodeWithText(BasemapAttribution.VISIBLE_TEXT).assertIsDisplayed()
    }
}
