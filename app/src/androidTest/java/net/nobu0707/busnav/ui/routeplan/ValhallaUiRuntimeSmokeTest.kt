package net.nobu0707.busnav.ui.routeplan

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.activity.ComponentActivity
import net.nobu0707.busnav.ui.theme.BusNavTheme
import net.nobu0707.busnav.ui.navigation.NavigationRoute
import net.nobu0707.busnav.location.AndroidLocationProvider
import net.nobu0707.busnav.data.route.InMemoryScheduledRouteRepository
import net.nobu0707.busnav.data.routing.valhalla.RoutingConfig
import net.nobu0707.busnav.data.routing.valhalla.ValhallaRoutingEngine
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.test.LocalValhallaAssumptions
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ValhallaUiRuntimeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var planHolder: RoutePlanEditorStateHolder

    @Before
    fun openRouteEditor() {
        LocalValhallaAssumptions.assumeAvailable()
        composeRule.runOnUiThread { org.maplibre.android.MapLibre.getInstance(composeRule.activity) }
        val provider = AndroidLocationProvider(composeRule.activity.applicationContext)
        val repository = InMemoryScheduledRouteRepository()
        val engine = ValhallaRoutingEngine(RoutingConfig(LocalValhallaAssumptions.BASE_URL))
        composeRule.setContent {
            BusNavTheme { NavigationRoute(provider, repository, engine,
                basemapConfig = net.nobu0707.busnav.map.basemap.BasemapConfig.fromBuildValue(
                    net.nobu0707.busnav.test.LocalBasemapAssumptions.STYLE_URL, true)) }
        }
        composeRule.runOnUiThread {
            planHolder = ViewModelProvider(composeRule.activity)[RoutePlanEditorViewModel::class.java]
                .stateHolder
        }
        run {
                composeRule.onNodeWithContentDescription("ルートメニューを開く").performClick()
                composeRule.onNodeWithText("経路編集").performClick()
                if (composeRule.onAllNodesWithTag("session_switch_confirm").fetchSemanticsNodes().isNotEmpty())
                    composeRule.onNodeWithTag("session_switch_confirm").performClick()
            }
        composeRule.onNodeWithText("ルート編集").assertIsDisplayed()
    }

    @Test
    fun shortAndLongRoutesSucceedSevenTimesThroughActivityUi() {
        setEndpoints(SHORT_START, SHORT_DESTINATION)
        val shortDistance = calculateAndAssert(SHORT_ROUTE)
        calculateAndAssert(SHORT_ROUTE)

        setEndpoints(LONG_START, LONG_DESTINATION)
        repeat(3) {
            calculateAndAssert(LONG_ROUTE)
        }

        setEndpoints(SHORT_START, SHORT_DESTINATION)
        calculateAndAssert(SHORT_ROUTE)

        setEndpoints(LONG_START, LONG_DESTINATION)
        val longDistance = calculateAndAssert(LONG_ROUTE)

        assertTrue("short route should be shorter than long route", shortDistance < longDistance)
    }

    @Test
    fun kantoLocalSaitamaAndCrossRegionCandidates() {
        setEndpointsOnMap(GeoPoint(35.6812, 139.7671), GeoPoint(35.7138, 139.7773))
        calculateAndAssert(RouteExpectation("Tokyo local", 1.0, 30.0))
        setEndpoints(GeoPoint(35.8617, 139.6455), GeoPoint(35.9062, 139.6237))
        calculateAndAssert(RouteExpectation("Saitama local", 1.0, 30.0))
        setEndpoints(GeoPoint(35.6812, 139.7671), GeoPoint(35.9062, 139.6237))
        calculateAndAssert(RouteExpectation("Tokyo-Saitama", 20.0, 100.0))
        setEndpoints(GeoPoint(35.6812, 139.7671), GeoPoint(34.9717, 138.3888))
        calculateAndAssert(RouteExpectation("Kanto-Chubu", 100.0, 300.0))
    }

    private fun setEndpointsOnMap(start: GeoPoint, destination: GeoPoint) {
        net.nobu0707.busnav.test.LocalBasemapAssumptions.assumeAvailable()
        fun find(view: android.view.View): org.maplibre.android.maps.MapView? {
            if (view is org.maplibre.android.maps.MapView) return view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        lateinit var nativeMap: org.maplibre.android.maps.MapLibreMap
        composeRule.waitUntil(30_000) {
            var loaded = false
            composeRule.runOnUiThread {
                find(composeRule.activity.window.decorView)?.getMapAsync { nativeMap = it; loaded = it.style?.isFullyLoaded == true }
            }
            loaded
        }
        for ((type, point) in listOf(RoutePlanPointType.START to start, RoutePlanPointType.DESTINATION to destination)) {
            composeRule.runOnUiThread {
                planHolder.selectAddMode(type)
                nativeMap.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                    org.maplibre.android.geometry.LatLng(point.latitude, point.longitude), 16.0))
            }
            composeRule.waitUntil(5000) { planHolder.camera?.center?.let {
                kotlin.math.abs(it.latitude - point.latitude) < .000001 && kotlin.math.abs(it.longitude - point.longitude) < .000001
            } == true }
            composeRule.onNodeWithTag(RoutePlanEditorTestTags.REGISTER).performClick()
            composeRule.runOnIdle {
                val added = planHolder.uiState.value.currentPlan.points.single { it.type == type }.position
                org.junit.Assert.assertEquals("camera=" + nativeMap.cameraPosition + " view=" + find(composeRule.activity.window.decorView)?.height, point.latitude, added.latitude, .00001)
                org.junit.Assert.assertEquals(point.longitude, added.longitude, .00001)
            }
            composeRule.waitForIdle()
        }
        composeRule.runOnIdle { assertTrue(planHolder.uiState.value.validation.isRoutingReady) }
    }

    private fun setEndpoints(start: GeoPoint, destination: GeoPoint) {
        composeRule.runOnUiThread {
            planHolder.selectAddMode(RoutePlanPointType.START)
            planHolder.addPoint(start)
            planHolder.selectAddMode(RoutePlanPointType.DESTINATION)
            planHolder.addPoint(destination)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertTrue(planHolder.uiState.value.validation.isRoutingReady) }
    }

    private fun calculateAndAssert(expected: RouteExpectation): Double {
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.CALCULATE)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.waitUntil(timeoutMillis = ROUTE_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(RoutePlanEditorTestTags.RESULT)
                .fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.RESULT)
            .assertIsDisplayed()
        assertTrue(
            "invalid-response message must not be displayed",
            composeRule.onAllNodesWithText(INVALID_RESPONSE_MESSAGE).fetchSemanticsNodes().isEmpty(),
        )
        composeRule.onNodeWithText("探索結果（道路沿いルート）").assertIsDisplayed()

        val summary = composeRule.onAllNodes(hasText(SUMMARY_PREFIX, substring = true))
            .fetchSemanticsNodes()
            .single()
            .config[SemanticsProperties.Text]
            .single()
            .text
        val match = requireNotNull(SUMMARY_PATTERN.matchEntire(summary)) {
            "Unexpected route summary: $summary"
        }
        val distanceKm = match.groupValues[1].toDouble()
        val durationMinutes = match.groupValues[2].takeIf(String::isNotEmpty)?.toInt()
            ?: (match.groupValues[3].toInt() * 60 + match.groupValues[4].toInt())
        assertTrue(
            "${expected.name} distance $distanceKm km was outside ${expected.minDistanceKm}..${expected.maxDistanceKm}",
            distanceKm in expected.minDistanceKm..expected.maxDistanceKm,
        )
        assertTrue("${expected.name} duration must be positive", durationMinutes > 0)
        return distanceKm
    }

    private companion object {
        const val ROUTE_TIMEOUT_MILLIS = 120_000L
        const val INVALID_RESPONSE_MESSAGE = "経路探索結果を読み取れませんでした"
        const val SUMMARY_PREFIX = "探索距離"
        val SUMMARY_PATTERN = Regex(
            "探索距離 ([0-9]+(?:\\.[0-9]+)?) km・推定所要時間 (?:(\\d+)分|(\\d+)時間(\\d+)分)",
        )
        val SHORT_ROUTE = RouteExpectation("short route", 10.0, 60.0)
        val LONG_ROUTE = RouteExpectation("long route", 50.0, 150.0)
        val SHORT_START = GeoPoint(35.24010, 138.61081)
        val SHORT_DESTINATION = GeoPoint(35.25416, 138.83650)
        val LONG_START = GeoPoint(35.52755965924169, 138.79653353327427)
        val LONG_DESTINATION = GeoPoint(35.609542457517534, 138.29084069799353)
    }

    private data class RouteExpectation(
        val name: String,
        val minDistanceKm: Double,
        val maxDistanceKm: Double,
    )
}
