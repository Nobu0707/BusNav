package net.nobu0707.busnav.ui.navigation

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.test.espresso.Espresso.closeSoftKeyboard
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import net.nobu0707.busnav.developer.createConnectionRepository
import net.nobu0707.busnav.ui.settings.developer.DeveloperConnectionScreen
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import net.nobu0707.busnav.data.route.InMemoryScheduledRouteRepository
import net.nobu0707.busnav.data.routing.valhalla.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.location.*
import net.nobu0707.busnav.map.basemap.BasemapConfig
import net.nobu0707.busnav.test.LocalBasemapAssumptions
import net.nobu0707.busnav.test.LocalValhallaAssumptions
import net.nobu0707.busnav.ui.routeplan.*
import net.nobu0707.busnav.ui.theme.BusNavTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Developer-selected route and injected positions only; never reads or records a real GPS track. */
class HighwayRuntimeSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun highwayCalculateApplySignsRolloverRecreationAndRecalculate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val connectionRepository = createConnectionRepository(instrumentation.targetContext)
        val valhallaOverride = arguments.getString("highwayValhallaBaseUrl")
        val basemapOverride = arguments.getString("highwayBasemapBaseUrl")
        if (valhallaOverride != null && basemapOverride != null) {
            rule.runOnUiThread { rule.activity.setContent { BusNavTheme { DeveloperConnectionScreen(connectionRepository, {}) } } }
            rule.waitUntil(5000) { rule.onAllNodes(hasTestTag("valhalla_url") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("valhalla_url").performScrollTo().performTextReplacement(valhallaOverride)
            rule.onNodeWithTag("basemap_url").performScrollTo().performTextReplacement(basemapOverride)
            closeSoftKeyboard()
            rule.onNodeWithTag("connections_save").performScrollTo().performClick()
            rule.waitUntil(5000) { runBlocking { connectionRepository.settings.first().valhallaBaseUrl == valhallaOverride } }
            assertEquals(basemapOverride, runBlocking { connectionRepository.settings.first().basemapBaseUrl })
        }
        LocalValhallaAssumptions.assumeAvailable()
        LocalBasemapAssumptions.assumeAvailable()
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).forEach {
            instrumentation.uiAutomation.grantRuntimePermission(rule.activity.packageName, it)
        }
        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        rule.waitUntil(5000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        rule.runOnUiThread { org.maplibre.android.MapLibre.getInstance(rule.activity) }
        val positions = MutableStateFlow<LocationUpdate>(LocationUpdate.Disabled)
        val provider = object : LocationProvider { override fun updates() = positions; override fun isLocationEnabled() = true }
        val delegate = ValhallaRoutingEngine(RoutingConfig(LocalValhallaAssumptions.BASE_URL))
        val repository = InMemoryScheduledRouteRepository()
        lateinit var route: ScheduledRoute
        var calls = 0
        val engine = RoutingEngine { request ->
            calls++
            delegate.calculateRoute(request).also { result -> if (result is RoutingResult.Success) route = result.route }
        }
        fun content() {
            rule.activity.setContent { BusNavTheme { NavigationRoute(provider, repository, engine,
                basemapConfig = BasemapConfig.fromBuildValue(LocalBasemapAssumptions.STYLE_URL, true)) } }
        }
        fun holder() = ViewModelProvider(rule.activity)[NavigationViewModel::class.java].stateHolder
        fun position(point: GeoPoint, accuracy: Float = 5f) {
            positions.value = LocationUpdate.Position(LocationState(point, accuracy, null, null, System.currentTimeMillis()))
        }
        fun awaitHighway() = rule.waitUntil(15000) {
            rule.onAllNodesWithTag("highway_distance", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        fun focusDecision() {
            fun find(view: android.view.View): org.maplibre.android.maps.MapView? {
                if (view is org.maplibre.android.maps.MapView) return view
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                return null
            }
            rule.waitUntil(15000) {
                var ready = false
                rule.runOnUiThread { find(rule.activity.window.decorView)?.getMapAsync { map -> ready = map.style?.isFullyLoaded == true } }
                ready
            }
            rule.runOnUiThread {
                requireNotNull(find(rule.activity.window.decorView)).getMapAsync { map ->
                    val point = requireNotNull(holder().uiState.value.location).point
                    map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                        org.maplibre.android.geometry.LatLng(point.latitude, point.longitude), 15.0))
                }
            }
        }
        fun screenshot(name: String) {
            // Allow the platform rotation/camera animations to finish before visual QA capture.
            instrumentation.waitForIdleSync()
            Thread.sleep(1000)
            val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
            val file = File(instrumentation.targetContext.getExternalFilesDir(null), "highway-$name.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            // Keep only developer-generated smoke images outside app storage so test uninstall cannot erase QA evidence.
            instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/busnav-highway-$name.png").close()
        }
        rule.runOnUiThread { content() }
        rule.onNodeWithContentDescription("ルート編集画面を開く").performClick()
        rule.runOnUiThread {
            val editor = ViewModelProvider(rule.activity)[RoutePlanEditorViewModel::class.java].stateHolder
            editor.selectAddMode(RoutePlanPointType.START); editor.addPoint(GeoPoint(35.161, 136.882))
            editor.selectAddMode(RoutePlanPointType.DESTINATION); editor.addPoint(GeoPoint(35.171, 138.675))
        }
        rule.onNodeWithTag(RoutePlanEditorTestTags.CALCULATE).performClick()
        rule.waitUntil(120000) { rule.onAllNodesWithTag(RoutePlanEditorTestTags.APPLY).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(RoutePlanEditorTestTags.APPLY).performClick()
        val calculator = NavigationProgressCalculator(route)
        val decisions = calculator.highwayCalculator.decisions
        assertTrue(decisions.isNotEmpty())
        assertTrue(route.guidance!!.maneuvers.all { it.beginGeometryIndex in route.geometry.points.indices })
        assertTrue(decisions.all { it.distanceAlongRouteMeters.isFinite() })
        val decision = decisions.firstOrNull { it.sign.facilityNames.isNotEmpty() } ?: decisions.firstOrNull { it.sign.toward.isNotEmpty() } ?: decisions.first()
        val maneuver = requireNotNull(route.guidance).maneuvers[decision.maneuverIndex]
        position(route.geometry.points[(maneuver.beginGeometryIndex - 1).coerceAtLeast(0)])
        awaitHighway()
        rule.onNodeWithTag("highway_schematic", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(NavigationTestTags.MAP).assertIsDisplayed()
        rule.waitUntil(20000) { holder().uiState.value.isMapReady }
        rule.runOnIdle {
            assertSame(route, holder().uiState.value.activeRoute)
            assertEquals(decision.sign, holder().uiState.value.highwayGuidance!!.sign)
        }
        rule.onNodeWithTag(NavigationTestTags.CURRENT_LOCATION).performClick()
        focusDecision(); screenshot("portrait")
        val retained = holder().uiState.value.highwayGuidance
        rule.activityRule.scenario.recreate()
        rule.runOnUiThread { content() }
        awaitHighway()
        rule.runOnIdle {
            assertSame(route, holder().uiState.value.activeRoute)
            assertEquals(retained, holder().uiState.value.highwayGuidance)
            assertEquals(LocalValhallaAssumptions.BASE_URL, runBlocking { connectionRepository.settings.first().valhallaBaseUrl })
        }
        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(10000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        // ComponentActivity has no application onCreate content; restore the same production composition.
        rule.runOnUiThread { content() }
        awaitHighway()
        rule.onNodeWithTag(NavigationTestTags.MAP).assertIsDisplayed()
        rule.onNodeWithTag(NavigationTestTags.CURRENT_LOCATION).performClick()
        focusDecision(); screenshot("landscape")
        position(route.geometry.points[maneuver.beginGeometryIndex], 150f)
        rule.waitUntil(10000) { rule.onAllNodesWithText("経路上の位置を確認中", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("highway_distance", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("highway_schematic", useUnmergedTree = true).assertDoesNotExist()
        position(GeoPoint(36.0, 137.0))
        rule.runOnIdle { assertEquals(1, calls); assertSame(route, holder().uiState.value.activeRoute) }
        val following = decisions.getOrNull(decisions.indexOf(decision) + 1)
        if (following != null) {
            val pointIndex = route.geometry.points.indices.firstOrNull {
                calculator.distanceIndex.distanceAtGeometryIndex(it) > decision.distanceAlongRouteMeters + 31 &&
                    calculator.distanceIndex.distanceAtGeometryIndex(it) < following.distanceAlongRouteMeters
            }
            if (pointIndex != null) {
                position(route.geometry.points[pointIndex])
                rule.waitUntil(10000) { holder().uiState.value.guidance.status == GuidanceStatus.RELIABLE }
                rule.runOnIdle { assertSame(route, holder().uiState.value.activeRoute) }
            }
        }
        rule.onNodeWithContentDescription("ルート編集画面を開く").performClick()
        rule.onNodeWithTag(RoutePlanEditorTestTags.CALCULATE).performClick()
        rule.waitUntil(120000) { rule.onAllNodesWithTag(RoutePlanEditorTestTags.APPLY).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(RoutePlanEditorTestTags.APPLY).performClick()
        rule.runOnIdle { assertEquals(2, calls) }
        rule.onNodeWithText("経路探索結果を読み取れませんでした").assertDoesNotExist()
    }
}
