package net.nobu0707.busnav.ui.navigation

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import net.nobu0707.busnav.data.route.InMemoryScheduledRouteRepository
import net.nobu0707.busnav.data.routing.valhalla.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.location.*
import net.nobu0707.busnav.test.LocalValhallaAssumptions
import net.nobu0707.busnav.ui.routeplan.*
import net.nobu0707.busnav.ui.theme.BusNavTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real routing + application UI, with position injection confined to androidTest. */
class GuidanceRuntimeSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun calculateApplyGuideAndRecalculateWithoutAutomaticReroute() {
        LocalValhallaAssumptions.assumeAvailable()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(rule.activity.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(rule.activity.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        rule.waitUntil(5000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        rule.runOnUiThread { org.maplibre.android.MapLibre.getInstance(rule.activity) }
        val positions = MutableStateFlow<LocationUpdate>(LocationUpdate.Disabled)
        val provider = object : LocationProvider {
            override fun updates() = net.nobu0707.busnav.test.repeatingSyntheticLocations(positions)
            override fun isLocationEnabled() = true
        }
        val delegate = ValhallaRoutingEngine(RoutingConfig(LocalValhallaAssumptions.BASE_URL))
        var calls = 0
        val engine = RoutingEngine { request ->
            calls++
            delegate.calculateRoute(request).also { result ->
                if (result is RoutingResult.Success) positions.value = LocationUpdate.Position(
                    LocationState(result.route.geometry.first,5f,null,null,System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime()))
            }
        }
        rule.setContent { BusNavTheme { NavigationRoute(provider, InMemoryScheduledRouteRepository(), engine) } }
        run {
                rule.onNodeWithContentDescription("ルートメニューを開く").performClick()
                rule.onNodeWithText("経路編集").performClick()
                if (rule.onAllNodesWithTag("session_switch_confirm").fetchSemanticsNodes().isNotEmpty())
                    rule.onNodeWithTag("session_switch_confirm").performClick()
            }
        rule.runOnUiThread {
            val holder = ViewModelProvider(rule.activity)[RoutePlanEditorViewModel::class.java].stateHolder
            holder.selectAddMode(RoutePlanPointType.START); holder.addPoint(GeoPoint(35.161,136.882))
            holder.selectAddMode(RoutePlanPointType.DESTINATION); holder.addPoint(GeoPoint(35.170,136.910))
        }
        repeat(2) { attempt ->
            rule.onNodeWithTag(RoutePlanEditorTestTags.CALCULATE).performClick()
            rule.waitUntil(120_000) { rule.onAllNodesWithTag(RoutePlanEditorTestTags.APPLY).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag(RoutePlanEditorTestTags.APPLY).performClick()
            rule.waitUntil(10_000) {
                val state = ViewModelProvider(rule.activity)[NavigationViewModel::class.java].stateHolder.uiState.value
                state.activeRoute != null && state.location?.point == state.activeRoute?.geometry?.first && state.startLocationAllowed
            }
            rule.onNodeWithContentDescription("ルートメニューを開く").performClick()
            rule.onNodeWithText("案内開始").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("guidance_distance").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("出発").assertIsDisplayed()
            rule.onNodeWithTag("guidance_distance").assertIsDisplayed()
            rule.onNodeWithTag("guidance_next_next").assertIsDisplayed()
            rule.onNodeWithText("経路探索結果を読み取れませんでした").assertDoesNotExist()
            positions.value = LocationUpdate.Position(LocationState(GeoPoint(36.0,137.0),5f,null,null,System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime()))
            rule.waitUntil(10_000) { rule.onAllNodesWithText("経路付近の位置を確認中").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("guidance_distance").assertDoesNotExist()
            rule.runOnIdle { assertEquals(attempt + 1, calls) }
            if (attempt == 0) run {
                rule.onNodeWithContentDescription("ルートメニューを開く").performClick()
                rule.onNodeWithText("経路編集").performClick()
                if (rule.onAllNodesWithTag("session_switch_confirm").fetchSemanticsNodes().isNotEmpty())
                    rule.onNodeWithTag("session_switch_confirm").performClick()
            }
        }
    }
}
