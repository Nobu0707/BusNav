package net.nobu0707.busnav.ui.navigation

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.nobu0707.busnav.data.navigationMapPreferenceRepository
import net.nobu0707.busnav.data.route.InMemoryScheduledRouteRepository
import net.nobu0707.busnav.detour.detourFixture
import net.nobu0707.busnav.domain.navigation.NavigationMapOrientation
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.location.*
import net.nobu0707.busnav.map.basemap.*
import net.nobu0707.busnav.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView

class NavigationOrientationFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun activeOnlyPersistencePortraitLandscapeTrafficAndEditor() {
        effectiveTestConnections()
        LocalBasemapAssumptions.assumeAvailable()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val preferences = navigationMapPreferenceRepository(rule.activity)
        val previous = runBlocking { preferences.orientation.first() }
        runBlocking { preferences.setOrientation(NavigationMapOrientation.HEADING_UP) }
        for (permission in listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            instrumentation.uiAutomation.grantRuntimePermission(rule.activity.packageName, permission)
        val route = detourFixture(13).route
        val positions = MutableStateFlow<LocationUpdate>(LocationUpdate.Position(
            LocationState(route.geometry.first, 5f, 90f, 8f, 1, android.os.SystemClock.elapsedRealtime())))
        val provider = object : LocationProvider {
            override fun updates() = repeatingSyntheticLocations(positions)
            override fun isLocationEnabled() = true
        }
        val repository = InMemoryScheduledRouteRepository()
        val engine = RoutingEngine { RoutingResult.Success(route, RoutingSummary(1000.0, 100.0)) }
        val config = BasemapConfig.forRegion(LocalBasemapAssumptions.BASE_URL, BasemapRegion.KANTO, true).withTheme(false)
        val content = { rule.activity.setContent { NavigationRoute(provider, repository, engine, basemapConfig = config) } }
        rule.runOnUiThread { MapLibre.getInstance(rule.activity); content() }
        try {
            rule.waitUntil(15_000) { rule.onAllNodesWithTag(NavigationTestTags.ROUTE_EDIT).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("navigation_compass").assertDoesNotExist()
            rule.runOnIdle {
                ViewModelProvider(rule.activity)[NavigationViewModel::class.java].stateHolder.applyCalculatedRoute(route)
            }
            rule.waitUntil(15_000) { rule.onAllNodesWithTag("navigation_compass").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("地図表示：進行方向が上。タップで北を上にする").assertIsDisplayed()
            fun awaitNavigationZoom() = rule.waitUntil(15_000) {
                var centered = false
                rule.runOnUiThread { findMap(rule.activity.window.decorView)?.getMapAsync {
                    centered = kotlin.math.abs(it.cameraPosition.zoom - 16.5) < 0.01
                } }
                centered
            }
            awaitNavigationZoom()
            for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
                rule.runOnUiThread { rule.activity.requestedOrientation = orientation }
                val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
                rule.waitUntil(15_000) { rule.activity.resources.configuration.orientation == expected }
                rule.runOnUiThread { content() }
                rule.waitUntil(15_000) { rule.onAllNodesWithTag("navigation_compass").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("navigation_compass").assertIsDisplayed()
                awaitNavigationZoom()
                val compass = rule.onNodeWithTag("navigation_compass").fetchSemanticsNode().boundsInRoot
                val map = rule.onNodeWithTag(NavigationTestTags.MAP).fetchSemanticsNode().boundsInRoot
                assertTrue(map.contains(compass.center))
                for (tag in listOf(NavigationTestTags.CURRENT_LOCATION, NavigationTestTags.ROUTE_OVERVIEW)) {
                    val other = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                    assertFalse("Compass overlaps $tag", compass.overlaps(other))
                }
                val density = rule.activity.resources.displayMetrics.density
                assertTrue(compass.width >= 48 * density && compass.height >= 48 * density)
                Thread.sleep(700)
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "navigation-camera").apply { mkdirs() }
                File(dir, "screen-$expected.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            rule.onNodeWithTag("navigation_compass").performClick()
            rule.waitUntil(5000) { runBlocking { preferences.orientation.first() } == NavigationMapOrientation.NORTH_UP }
            rule.onNodeWithContentDescription("地図表示：北が上。タップで進行方向を上にする").assertIsDisplayed()
            rule.onNodeWithTag("bottom_規制").performClick()
            rule.onNodeWithTag("traffic_panel").assertIsDisplayed()
            rule.onNodeWithTag("navigation_compass").assertDoesNotExist()
            rule.onNodeWithText("閉じる").performClick()
            rule.onNodeWithContentDescription("地図表示：北が上。タップで進行方向を上にする").assertIsDisplayed()
            rule.activityRule.scenario.recreate()
            rule.runOnUiThread { content() }
            rule.waitUntil(15_000) { rule.onAllNodesWithTag("navigation_compass").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("地図表示：北が上。タップで進行方向を上にする").assertIsDisplayed()
            // Existing screen navigation ends the session explicitly before entering the editor.
            rule.onNodeWithTag(NavigationTestTags.ROUTE_EDIT).performClick()
            rule.onNodeWithText("経路編集").performClick()
            if (rule.onAllNodesWithTag("session_switch_confirm").fetchSemanticsNodes().isNotEmpty())
                rule.onNodeWithTag("session_switch_confirm").performClick()
            rule.waitUntil(15_000) { rule.onAllNodesWithTag(net.nobu0707.busnav.ui.routeplan.RoutePlanEditorTestTags.SCREEN).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("navigation_compass").assertDoesNotExist()
            rule.runOnUiThread { findMap(rule.activity.window.decorView)?.getMapAsync { assertEquals(0.0, it.cameraPosition.bearing, 0.5) } }
        } finally {
            runBlocking { preferences.setOrientation(previous) }
            rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
    }
    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
        return null
    }
}
