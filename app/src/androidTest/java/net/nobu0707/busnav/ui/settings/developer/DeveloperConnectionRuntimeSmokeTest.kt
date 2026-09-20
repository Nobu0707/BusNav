package net.nobu0707.busnav.ui.settings.developer

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.nobu0707.busnav.developer.createConnectionRepository
import net.nobu0707.busnav.map.basemap.BasemapRegion
import net.nobu0707.busnav.test.LocalBasemapAssumptions
import net.nobu0707.busnav.test.LocalValhallaAssumptions
import net.nobu0707.busnav.ui.theme.BusNavTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DeveloperConnectionRuntimeSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun liveRegionChecksSaveAndRecreateLeaveKantoReady() {
        LocalValhallaAssumptions.assumeAvailable()
        LocalBasemapAssumptions.assumeAvailable()
        val repository = createConnectionRepository(rule.activity)
        val original = runBlocking { repository.settings.first() }
        fun content() { rule.activity.setContent { BusNavTheme { DeveloperConnectionScreen(repository, {}) } } }
        rule.runOnUiThread { content() }
        for (region in listOf(BasemapRegion.CHUBU, BasemapRegion.KANTO)) {
            rule.waitUntil(10_000) {
                rule.onAllNodes(hasTestTag("connections_save") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("basemap_region_" + region.id).performScrollTo().performClick().assertIsSelected()
            rule.onNodeWithTag("basemap_check").performScrollTo().performClick()
            rule.waitUntil(10_000) {
                rule.onAllNodes(hasTestTag("basemap_status") and hasText("接続成功")).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("connections_save").performScrollTo().performClick()
            rule.waitUntil(5_000) { runBlocking { repository.settings.first().basemapRegion == region } }
        }
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasTestTag("valhalla_check") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("valhalla_check").performScrollTo().performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasTestTag("valhalla_status") and hasText("接続成功")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.activityRule.scenario.recreate()
        rule.runOnUiThread { content() }
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasTestTag("connections_save") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("basemap_region_kanto").performScrollTo().assertIsSelected()
        val saved = runBlocking { repository.settings.first() }
        assertEquals(original.valhallaBaseUrl, saved.valhallaBaseUrl)
        assertEquals(original.basemapBaseUrl, saved.basemapBaseUrl)
        assertEquals(BasemapRegion.KANTO, saved.basemapRegion)
    }
}
