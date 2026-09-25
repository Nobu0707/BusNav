package net.nobu0707.busnav.domain.navigation

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.RouteGeometry
import net.nobu0707.busnav.domain.route.RoutePoint
import net.nobu0707.busnav.domain.route.RoutePointType
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.location.LocationState
import net.nobu0707.busnav.domain.prescribed.NavigationMode
import net.nobu0707.busnav.ui.navigation.NavigationUiState
import net.nobu0707.busnav.ui.navigation.keepScreenOn
import net.nobu0707.busnav.map.DeviceHeadingMath
import net.nobu0707.busnav.map.DisplayAxes
import net.nobu0707.busnav.map.DisplayAxis
import org.junit.Assert.*
import org.junit.Test

class NavigationRuntimeReliabilityTest {
    private val raw = GeoPoint(35.68, 139.76)
    private val end = GeoPoint(35.69, 139.77)

    private fun route(start: GeoPoint) = ScheduledRoute("route", "route",
        RouteGeometry(listOf(start, end)), listOf(
            RoutePoint("start", RoutePointType.START, raw),
            RoutePoint("end", RoutePointType.DESTINATION, end)))

    @Test fun freeRouteFirstGeometryPointIsSnappedStartWithinSafetyBound() {
        val onRoad = freeNavigationStartPosition(raw, route(raw))!!
        assertEquals(NavigationStartSource.RAW_GPS, onRoad.source)
        assertEquals(raw, onRoad.navigationStartPoint)
        for (meters in listOf(20.0, 80.0, 150.0, 300.0)) {
            val point = GeoPoint(raw.latitude + meters / 111_195.1, raw.longitude)
            val result = freeNavigationStartPosition(raw, route(point))!!
            assertEquals(NavigationStartSource.ROUTING_SNAPPED, result.source)
            assertEquals(raw, result.rawLocation)
            assertEquals(point, result.navigationStartPoint)
            assertEquals(meters, result.snapDistanceMeters, 0.2)
        }
        assertNull(freeNavigationStartPosition(raw,
            route(GeoPoint(raw.latitude + 301.0 / 111_195.1, raw.longitude))))
    }

    @Test fun headingUpAnchorUsesVisibleViewportAndSameFixForMarkerAndCamera() {
        val fix = LocationState(raw, 5f, 90f, 8f, 1000, 1000)
        val up = NavigationCameraState(true, true, NavigationMapOrientation.HEADING_UP, 90.0)
        val portrait = navigationMapFrame(fix, up, 0.0, 800, 160)
        assertSame(fix, portrait.location)
        assertEquals(raw, portrait.cameraTarget)
        assertEquals(90.0, portrait.cameraBearing, 0.0)
        assertEquals(0.0, portrait.markerScreenRotation, 0.0)
        assertEquals(0.85, (portrait.topPaddingPx + 800 - portrait.bottomPaddingPx) / 2 / 640, 0.001)
        val landscape = navigationMapFrame(fix, up, 0.0, 360, 80)
        assertEquals(0.85, (landscape.topPaddingPx + 360 - landscape.bottomPaddingPx) / 2 / 280, 0.001)
        val north = navigationMapFrame(fix, up.copy(orientation = NavigationMapOrientation.NORTH_UP), 90.0, 800, 160)
        assertEquals(0.0, north.topPaddingPx, 0.0)
        assertEquals(0.0, north.bottomPaddingPx, 0.0)
        assertEquals(90.0, north.markerScreenRotation, 0.0)
        assertFalse(shouldApplyMapFrame(1001, 1000))
        assertTrue(shouldApplyMapFrame(1000, 1000))
    }

    @Test fun screenOnOnlyForActiveNavigationSession() {
        val preview = NavigationUiState(activeRoute = route(raw))
        assertFalse(preview.keepScreenOn)
        assertFalse(NavigationUiState().keepScreenOn)
        for (mode in listOf(NavigationMode.FREE, NavigationMode.PRESCRIBED)) {
            val active = preview.copy(isNavigationStarted = true, navigationMode = mode)
            assertTrue(active.keepScreenOn)
            assertFalse(active.copy(isNavigationStarted = false).keepScreenOn)
            assertFalse(active.copy(activeRoute = null).keepScreenOn)
        }
        // Active detour retains PRESCRIBED navigationActive, so the flag stays set.
    }

    @Test fun deviceCompassCorrectsDisplayTrueNorthAndCameraRotation() {
        assertEquals(DisplayAxes(DisplayAxis.X, DisplayAxis.Y), DeviceHeadingMath.axes(0))
        assertEquals(DisplayAxes(DisplayAxis.Y, DisplayAxis.MINUS_X), DeviceHeadingMath.axes(1))
        assertEquals(DisplayAxes(DisplayAxis.MINUS_X, DisplayAxis.MINUS_Y), DeviceHeadingMath.axes(2))
        assertEquals(DisplayAxes(DisplayAxis.MINUS_Y, DisplayAxis.X), DeviceHeadingMath.axes(3))
        assertEquals(8.0, DeviceHeadingMath.trueHeading(2.0, 6.0), 0.0)
        assertEquals(0.0, DeviceHeadingMath.smooth(359.0, 1.0, 0.5), 0.0)
        assertEquals(345.0, DeviceHeadingMath.screenRotation(15.0, 30.0), 0.0)
        val nav = NavigationCameraState(true, true, NavigationMapOrientation.NORTH_UP, 90.0)
        assertEquals(90.0, nav.vehicleScreenRotation(0.0, 210.0), 0.0)
    }
}
