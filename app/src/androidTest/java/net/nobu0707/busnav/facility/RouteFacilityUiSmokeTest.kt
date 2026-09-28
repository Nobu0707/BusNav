package net.nobu0707.busnav.facility

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.nobu0707.busnav.BuildConfig
import net.nobu0707.busnav.data.route.InMemoryScheduledRouteRepository
import net.nobu0707.busnav.data.routing.valhalla.RoutingConfig
import net.nobu0707.busnav.data.routing.valhalla.ValhallaRoutingEngine
import net.nobu0707.busnav.ui.facility.RouteFacilityLoadState
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.routeplan.*
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.location.*
import net.nobu0707.busnav.map.basemap.BasemapConfig
import net.nobu0707.busnav.ui.navigation.NavigationRoute
import net.nobu0707.busnav.ui.navigation.NavigationTestTags
import net.nobu0707.busnav.ui.navigation.NavigationViewModel
import net.nobu0707.busnav.ui.theme.BusNavTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre

/** Public route and production facility service; no device position is sent. */
class RouteFacilityUiSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun physicalPanelSelectionAndPlannedStopSurviveRotation() {
        val plan = RoutePlan("facility-ui-smoke", "Tokyo to Nagoya", listOf(
            RoutePlanPoint("start", RoutePlanPointType.START, GeoPoint(35.681236, 139.767125)),
            RoutePlanPoint("destination", RoutePlanPointType.DESTINATION, GeoPoint(35.170915, 136.881537)),
        ))
        val request = (plan.toRoutingRequest() as RoutingRequestResult.Ready).request
        val result = runBlocking { withTimeout(120_000) {
            ValhallaRoutingEngine(RoutingConfig(BuildConfig.VALHALLA_BASE_URL)).calculateRoute(request)
        } }
        assertTrue("Remote routing failed", result is RoutingResult.Success)
        val route = (result as RoutingResult.Success).route
        val provider = object : LocationProvider {
            override fun updates() = flowOf(LocationUpdate.Disabled)
            override fun isLocationEnabled() = false
        }
        fun attach() {
            MapLibre.getInstance(rule.activity)
            rule.activity.setContent { BusNavTheme {
                NavigationRoute(provider, InMemoryScheduledRouteRepository(),
                    RoutingEngine { RoutingResult.Success(route, RoutingSummary(1000.0, 100.0)) },
                    basemapConfig = BasemapConfig.fromBuildValue("", true))
            } }
        }
        rule.runOnUiThread { attach() }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag(NavigationTestTags.ROUTE_EDIT).fetchSemanticsNodes().isNotEmpty() }
        val vm = ViewModelProvider(rule.activity)[NavigationViewModel::class.java]
        rule.runOnIdle { vm.stateHolder.applyCalculatedRoute(route) }
        rule.waitUntil(45_000) { vm.facilities.state.value.loadState == RouteFacilityLoadState.READY }
        val first = vm.facilities.state.value.candidates.firstOrNull()
        assertNotNull("Expected public route facility candidates", first)
        val id = first!!.id
        vm.facilities.updateProgress(0.0, true)
        assertTrue(vm.facilities.state.value.distancesReliable)
        assertTrue(vm.facilities.state.value.distances[id]!!.distanceAheadMeters!! >= 0.0)

        rule.onNodeWithTag(NavigationTestTags.ROUTE_EDIT).performClick()
        rule.onNodeWithText("この先のSA/PA").performClick()
        rule.onNodeWithTag("route_facility_sheet").assertIsDisplayed()
        rule.onNodeWithTag("facility_${id.osmType}${id.osmId}").performClick()
        rule.waitUntil(5_000) { vm.facilities.state.value.selectedId == id }
        rule.onNodeWithText("休憩予定に設定").performClick()
        rule.waitUntil(5_000) { vm.facilities.state.value.plannedStops.any { it.facilityId == id } }

        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(15_000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        rule.runOnUiThread { attach() }
        rule.onNodeWithTag("route_facility_sheet").assertExists()
        assertTrue(vm.facilities.state.value.plannedStops.any { it.facilityId == id })
        rule.onNodeWithText("休憩予定を解除").performClick()
        rule.waitUntil(5_000) { vm.facilities.state.value.plannedStops.isEmpty() }
        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
}
