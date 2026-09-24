package net.nobu0707.busnav.ui.settings.developer

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import kotlinx.coroutines.flow.MutableStateFlow
import net.nobu0707.busnav.developer.*
import net.nobu0707.busnav.ui.theme.BusNavTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DeveloperConnectionScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val defaults = DeveloperConnectionSettings("http://10.0.2.2:8002", "http://10.0.2.2:8080")
    private val repository = object : DeveloperConnectionRepository {
        override val settings = MutableStateFlow(defaults)
        override suspend fun update(settings: DeveloperConnectionSettings) { this.settings.value = settings.normalized() }
        override suspend fun reset() { settings.value = defaults }
    }
    @Test fun portraitValidationSaveResetAndConnectionResults() { exercise(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) }
    @Test fun landscapeValidationSaveResetAndConnectionResults() { exercise(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) }

    private fun exercise(orientation: Int) {
        rule.runOnUiThread { rule.activity.requestedOrientation = orientation }
        val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        rule.waitUntil(5_000) { rule.activity.resources.configuration.orientation == expected }
        rule.waitForIdle()
        rule.setContent {
            BusNavTheme {
                DeveloperConnectionScreen(repository, onBack = {}, checkConnection = { _, service ->
                    if (service == ConnectionService.VALHALLA) ConnectionResult(ConnectionStatus.SUCCESS)
                    else ConnectionResult(ConnectionStatus.HTTP_ERROR, 404)
                })
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodes(hasText(defaults.valhallaBaseUrl)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("basemap_region_kanto").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("basemap_region_chubu").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("basemap_region_chubu").fetchSemanticsNodes().any { it.config[androidx.compose.ui.semantics.SemanticsProperties.Selected] == true } }
        rule.onNodeWithTag("valhalla_url").performScrollTo().performTextReplacement("host:8002")
        rule.onNodeWithTag("valhalla_url").assertTextContains("host:8002")
        closeSoftKeyboard()
        rule.waitForIdle()
        rule.onNodeWithTag("connections_save").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(defaults, repository.settings.value) }
        rule.onNodeWithTag("valhalla_url").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("http(s)://ホスト:ポートを入力してください（認証情報・query・fragment・path は不可）").assertExists()
        rule.onNodeWithTag("valhalla_url").performTextReplacement(" http://192.168.1.100:8002/ ")
        rule.onNodeWithTag("basemap_url").performScrollTo().performTextReplacement("http://192.168.1.100:8080")
        closeSoftKeyboard()
        rule.waitForIdle()
        rule.onNodeWithTag("connections_save").performScrollTo().performClick()
        rule.waitUntil(5_000) { repository.settings.value.valhallaBaseUrl == "http://192.168.1.100:8002" }
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("connections_save") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.runOnIdle { assertEquals(net.nobu0707.busnav.map.basemap.BasemapRegion.CHUBU, repository.settings.value.basemapRegion) }
        rule.onNodeWithTag("valhalla_check").performScrollTo().performClick()
        rule.onNodeWithTag("valhalla_status").performScrollTo().assertTextEquals("接続成功")
        rule.onNodeWithTag("basemap_check").performScrollTo().performClick()
        rule.onNodeWithTag("basemap_status").performScrollTo().assertTextEquals("HTTPエラー (404)：選択した地域の地図データがありません")
        rule.onNodeWithTag("connections_reset").performScrollTo().performClick()
        rule.waitUntil(5_000) { repository.settings.value == defaults }
        rule.onNodeWithTag("basemap_region_kanto").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("valhalla_url").performScrollTo().assertTextContains(defaults.valhallaBaseUrl)
    }
}
