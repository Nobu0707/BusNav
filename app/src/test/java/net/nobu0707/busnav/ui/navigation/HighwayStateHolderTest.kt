package net.nobu0707.busnav.ui.navigation

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.location.*
import org.junit.Assert.*
import org.junit.Test

class HighwayStateHolderTest {
    @Test fun accuracyDisabledLayoutAndRouteReplacementKeepSafeState() {
        val geometry = RouteGeometry(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, .01), GeoPoint(0.0, .02)))
        val route = ScheduledRoute("h", "highway", geometry, listOf(RoutePoint("s", RoutePointType.START, geometry.first),
            RoutePoint("d", RoutePointType.DESTINATION, geometry.last)), guidance = RouteGuidance(listOf(
            RouteManeuver(0, ManeuverType.KEEP_LEFT, "", 1, 2))))
        var now = 0L
        val flow = MutableStateFlow<LocationUpdate>(LocationUpdate.Disabled)
        val provider = object : LocationProvider { override fun updates() = flow; override fun isLocationEnabled() = true }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val holder = NavigationStateHolder(provider, object : ScheduledRouteRepository { override suspend fun getActiveRoute() = route }, scope, Dispatchers.Unconfined, elapsedMillis = { now })
            holder.applyCalculatedRoute(route)
            holder.setPermission(LocationPermissionState.Granted)
            fun position(accuracy: Float, lon: Double = .009) { now += 5000; flow.value = LocationUpdate.Position(LocationState(GeoPoint(0.0, lon), accuracy, null, null, 1, now)) }
            position(5f)
            assertTrue(holder.startFreeNavigation())
            val initial = holder.uiState.value.highwayGuidance!!
            assertNotNull(initial.schematic)
            holder.setLayoutMode(NavigationLayoutMode.LandscapeThreeColumn)
            assertEquals(initial, holder.uiState.value.highwayGuidance)
            position(100f, .019)
            assertNull(holder.uiState.value.highwayGuidance!!.schematic)
            assertNull(holder.uiState.value.highwayGuidance!!.distanceText)
            assertSame(route, holder.uiState.value.activeRoute)
            flow.value = LocationUpdate.Disabled
            assertNull(holder.uiState.value.highwayGuidance)
            repeat(3) { position(5f) }
            assertEquals(initial, holder.uiState.value.highwayGuidance)
            val general = ScheduledRoute("g", "general", geometry, route.points, guidance = RouteGuidance(listOf(RouteManeuver(0, ManeuverType.LEFT, "", 1, 2))))
            holder.clearRoute()
            holder.applyCalculatedRoute(general)
            assertNull(holder.uiState.value.highwayGuidance)
            assertEquals("左折", holder.uiState.value.guidance.primaryText)
        } finally { scope.cancel() }
    }
}
