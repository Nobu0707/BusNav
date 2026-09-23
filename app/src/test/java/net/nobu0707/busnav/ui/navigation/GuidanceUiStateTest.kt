package net.nobu0707.busnav.ui.navigation

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.location.*
import org.junit.Assert.*
import org.junit.Test

class GuidanceUiStateTest {
    private fun maneuver(type: ManeuverType) = RouteManeuver(0, type, "Provider fallback", 0, 1,
        streetNames = listOf("国道1号"))
    @Test fun japanesePrimaryInstructions() {
        val expectations = mapOf(ManeuverType.RIGHT to "右折", ManeuverType.LEFT to "左折",
            ManeuverType.EXIT_LEFT to "左方向の出口へ", ManeuverType.KEEP_RIGHT to "右方向を維持",
            ManeuverType.DESTINATION to "目的地です", ManeuverType.UNKNOWN to "Provider fallback")
        expectations.forEach { (type, text) -> assertEquals(text, NavigationInstructionFormatter.format(maneuver(type)).primary) }
        assertEquals("案内を確認", NavigationInstructionFormatter.format(maneuver(ManeuverType.UNKNOWN).copy(instruction = "")).primary)
    }
    @Test fun signsArePreferredAndDeduplicatedWithStreetFallback() {
        val m = maneuver(ManeuverType.EXIT_LEFT)
        assertEquals("国道1号", NavigationInstructionFormatter.format(m).secondary)
        assertEquals("E1 / 東京方面", NavigationInstructionFormatter.format(m.copy(signs = listOf(
            HighwaySign(HighwaySignType.EXIT_BRANCH, "E1"), HighwaySign(HighwaySignType.EXIT_BRANCH, "E1"),
            HighwaySign(HighwaySignType.EXIT_TOWARD, "東京方面")))).secondary)
    }
    @Test fun distanceFormattingBoundaries() {
        assertEquals("0 m", formatGuidanceDistance(0.0))
        assertEquals("350 m", formatGuidanceDistance(350.0))
        assertEquals("999 m", formatGuidanceDistance(999.0))
        assertEquals("1.0 km", formatGuidanceDistance(1000.0))
        assertEquals("1.2 km", formatGuidanceDistance(1234.0))
        assertEquals("12 km", formatGuidanceDistance(12345.0))
        assertThrows(IllegalArgumentException::class.java) { formatGuidanceDistance(Double.NaN) }
    }
    private fun route(id: String = "r"): ScheduledRoute {
        val g = RouteGeometry(listOf(GeoPoint(0.0,0.0),GeoPoint(0.0,0.001),GeoPoint(0.0,0.003)))
        return ScheduledRoute(id, "route", g, listOf(RoutePoint("s",RoutePointType.START,g.first),
            RoutePoint("d",RoutePointType.DESTINATION,g.last)), guidance = RouteGuidance(listOf(
            maneuver(ManeuverType.RIGHT).copy(beginGeometryIndex=1),
            maneuver(ManeuverType.DESTINATION).copy(index=1,beginGeometryIndex=2,endGeometryIndex=2))))
    }
    private class TestProvider : LocationProvider {
        var now = 0L
        val flow = MutableSharedFlow<LocationUpdate>(extraBufferCapacity = 10)
        override fun updates(): Flow<LocationUpdate> = flow
        override fun isLocationEnabled() = true
        fun position(lon: Double = 0.0005, lat: Double = 0.0, accuracy: Float = 5f) {
            now += 5000
            flow.tryEmit(LocationUpdate.Position(LocationState(GeoPoint(lat,lon),accuracy,null,null,1,now)))
        }
    }
    private fun holder(route: ScheduledRoute?, provider: TestProvider, scope: CoroutineScope) = NavigationStateHolder(
        provider, object : ScheduledRouteRepository { override suspend fun getActiveRoute() = route }, scope, Dispatchers.Unconfined, elapsedMillis = { provider.now }).also { h -> route?.let(h::applyCalculatedRoute) }
    @Test fun noRouteHasNoGuidance() {
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try { assertEquals(GuidanceStatus.NO_ROUTE, holder(null,TestProvider(),scope).uiState.value.guidance.status) } finally { scope.cancel() }
    }
    @Test fun missingLocationWaitsInsteadOfAssumingStart() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try { val h=holder(route(),TestProvider(),scope); h.setPermission(LocationPermissionState.Granted)
            assertFalse(h.startFreeNavigation())
            assertEquals(GuidanceStatus.NO_ROUTE,h.uiState.value.guidance.status)
            assertNull(h.uiState.value.guidance.distanceText)
        } finally { scope.cancel() }
    }
    @Test fun reliableFixShowsGuidanceAndUncertainFixDoesNotChangeRoute() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val r=route(); val provider=TestProvider(); val h=holder(r,provider,scope)
            h.setPermission(LocationPermissionState.Granted); provider.position(); assertTrue(h.startFreeNavigation())
            assertEquals("右折", h.uiState.value.guidance.primaryText)
            assertNotNull(h.uiState.value.guidance.distanceText)
            assertEquals("目的地へ",h.uiState.value.guidance.nextNextInstruction)
            provider.position(lat=0.01)
            assertEquals(GuidanceStatus.UNCERTAIN,h.uiState.value.guidance.status)
            assertNull(h.uiState.value.guidance.distanceText)
            assertNull(h.uiState.value.guidance.nextNextInstruction)
            assertSame(r,h.uiState.value.activeRoute)
        } finally { scope.cancel() }
    }
    @Test fun inaccurateGpsAndLocationFailureSuppressGuidance() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val p=TestProvider(); val h=holder(route(),p,scope);h.setPermission(LocationPermissionState.Granted)
            p.position(accuracy=100f);assertTrue(h.startFreeNavigation());assertEquals(GuidanceStatus.UNCERTAIN,h.uiState.value.guidance.status)
            p.flow.tryEmit(LocationUpdate.Disabled);assertEquals(GuidanceStatus.WAITING_LOCATION,h.uiState.value.guidance.status)
            p.position();h.setPermission(LocationPermissionState.Denied)
            assertEquals(GuidanceStatus.WAITING_LOCATION,h.uiState.value.guidance.status)
        } finally { scope.cancel() }
    }
    @Test fun routeReplacementRebuildsProgressAndDoesNotReusePreviousDistance() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val p=TestProvider();val h=holder(route(),p,scope);h.setPermission(LocationPermissionState.Granted)
            p.position(lon=0.0029);h.clearRoute();h.applyCalculatedRoute(route("new"));p.position()
            assertEquals("new",h.uiState.value.activeRoute!!.id)
            assertEquals("右折",h.uiState.value.guidance.primaryText)
        } finally { scope.cancel() }
    }
}
