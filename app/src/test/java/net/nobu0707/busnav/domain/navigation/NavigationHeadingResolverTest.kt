package net.nobu0707.busnav.domain.navigation

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.location.LocationState
import net.nobu0707.busnav.map.VehicleMarkerGeometry
import org.junit.Assert.*
import org.junit.Test

class NavigationHeadingResolverTest {
    private val resolver = NavigationHeadingResolver()
    private fun fix(bearing: Float? = 90f, speed: Float? = 8f, time: Long = 1000) =
        LocationState(GeoPoint(35.0, 139.0), 5f, bearing, speed, time, time)
    private fun resolve(bearing: Float?, previous: NavigationHeading = NavigationHeading(), time: Long = 1000, speed: Float? = 8f) =
        resolver.resolve(fix(bearing, speed, time), previous, time)

    @Test fun defaultAndInvalidPreference() {
        assertEquals(NavigationMapOrientation.HEADING_UP, NavigationMapOrientation.fromStored(null))
        assertEquals(NavigationMapOrientation.HEADING_UP, NavigationMapOrientation.fromStored("invalid"))
        assertEquals(NavigationMapOrientation.NORTH_UP, NavigationMapOrientation.fromStored("north_up"))
    }
    @Test fun reliableGpsIsPrimary() {
        val result = resolver.resolve(fix(), NavigationHeading(), 1000, ReliableRouteHeading(180.0, RouteMatchQuality.MATCHED, true))
        assertEquals(90.0, result.degrees!!, 0.0)
        assertEquals(HeadingSource.GPS, result.source)
    }
    @Test fun stoppedAndMissingBearingHoldLastHeading() {
        val first = resolve(90f)
        for (bearing in listOf(270f, 0f, null, Float.NaN)) {
            assertEquals(90.0, resolve(bearing, first, 2000, 0f).degrees!!, 0.0)
        }
        assertEquals(90.0, resolve(null, first, 2000).degrees!!, 0.0)
    }
    @Test fun reliableRouteFallbackOnlyWithoutHistory() {
        val route = ReliableRouteHeading(180.0, RouteMatchQuality.MATCHED, true)
        assertEquals(180.0, resolver.resolve(fix(speed = 0f), NavigationHeading(), 1000, route).degrees!!, 0.0)
        assertEquals(90.0, resolver.resolve(fix(speed = 0f, time = 2000), resolve(90f), 2000, route).degrees!!, 0.0)
    }
    @Test fun uncertainProjectionAndAmbiguousRouteNeverSupplyHeading() {
        for (quality in listOf(RouteMatchQuality.AMBIGUOUS, RouteMatchQuality.UNRELIABLE)) {
            assertNull(resolver.resolve(fix(speed = 0f), NavigationHeading(), 1000, ReliableRouteHeading(180.0, quality, true)).degrees)
        }
        assertNull(resolver.resolve(fix(speed = 0f), NavigationHeading(), 1000,
            ReliableRouteHeading(180.0, RouteMatchQuality.MATCHED, false)).degrees)
    }
    @Test fun matchingMaySupplyFallbackForSameFixButCannotConfirmSpikeTwice() {
        val initial = resolve(null, speed = 0f)
        assertEquals(180.0, resolver.resolve(fix(speed = 0f), initial, 1000,
            ReliableRouteHeading(180.0, RouteMatchQuality.MATCHED, true)).degrees!!, 0.0)
        val spike = resolve(220f, resolve(10f), 2000)
        assertEquals(spike, resolve(220f, spike, 2000))
    }
    @Test fun circularMathBothDirections() {
        assertEquals(2.0, shortestHeadingDelta(359.0, 1.0), 0.0)
        assertEquals(-2.0, shortestHeadingDelta(1.0, 359.0), 0.0)
        assertEquals(1.0, resolve(1f, resolve(359f), 2000).degrees!!, 0.0)
        assertEquals(359.0, resolve(359f, resolve(1f), 2000).degrees!!, 0.0)
    }
    @Test fun isolatedSpikeIsSuppressed() {
        var value = resolve(10f)
        value = resolve(220f, value, 2000)
        assertEquals(10.0, value.degrees!!, 0.0)
        value = resolve(12f, value, 3000)
        assertEquals(12.0, value.degrees!!, 0.0)
    }
    @Test fun sustainedSharpTurnIsConfirmedAndNormalTurnsFollowImmediately() {
        var value = NavigationHeading()
        listOf(0, 20, 45, 80, 90, 120, 180).forEachIndexed { i, degrees ->
            value = resolve(degrees.toFloat(), value, (i + 1) * 1000L)
            assertEquals(degrees.toDouble(), value.degrees!!, 0.0)
        }
        val pending = resolve(220f, resolve(10f), 2000)
        assertEquals(225.0, resolve(225f, pending, 3000).degrees!!, 0.0)
    }
    @Test fun smallNoiseIsHeldAndMovementResumes() {
        val first = resolve(90f)
        assertEquals(90.0, resolve(91f, first, 2000).degrees!!, 0.0)
        assertEquals(100.0, resolve(100f, first, 3000).degrees!!, 0.0)
    }
    @Test fun staleFutureMissingAndOutOfOrderFixesAreIgnored() {
        val first = resolve(90f)
        listOf(null, fix(time = 0), fix(time = 30_000), fix().copy(elapsedRealtimeMillis = null)).forEach {
            assertEquals(90.0, resolver.resolve(it, first, 20_000).degrees!!, 0.0)
        }
        assertEquals(first, resolve(180f, first, 500))
    }
    @Test fun inactiveAndManualCameraRemainUnchanged() {
        assertEquals(37.0, NavigationCameraState(headingDegrees = 90.0).targetBearing(37.0), 0.0)
        assertEquals(37.0, NavigationCameraState(true, false, headingDegrees = 90.0).targetBearing(37.0), 0.0)
        assertEquals(37.0, NavigationCameraState(true).targetBearing(37.0), 0.0)
    }
    @Test fun recenterRestoresSelectedOrientationAndMarkerSemantics() {
        for (heading in listOf(0.0, 90.0, 180.0, 270.0)) {
            val up = NavigationCameraState(true, true, headingDegrees = heading)
            assertEquals(heading, up.targetBearing(35.0), 0.0)
            assertEquals(0.0, up.vehicleScreenRotation(heading, heading), 0.0)
            val north = up.copy(orientation = NavigationMapOrientation.NORTH_UP)
            assertEquals(0.0, north.targetBearing(35.0), 0.0)
            assertEquals(heading, north.vehicleScreenRotation(0.0, heading), 0.0)
        }
    }
    @Test fun compassCardinals() {
        listOf(0.0, 90.0, 180.0, 270.0).zip(listOf(0.0, 270.0, 180.0, 90.0)).forEach { (bearing, expected) ->
            assertEquals(expected, northScreenRotation(bearing), 0.0)
        }
    }
    @Test fun geometryAnchorsTipAtCenterWithSymmetricTailAndBounds() {
        val g = VehicleMarkerGeometry()
        assertEquals(g.center, g.arrow.first())
        assertEquals(g.size / 2, g.center.x)
        assertEquals(g.size / 2, g.center.y)
        assertTrue(g.arrow.drop(1).all { it.y > g.center.y })
        assertEquals(g.size, g.arrow[1].x + g.arrow[3].x, 0.001f)
        assertTrue(g.arrow.all { it.x in 0f..g.size && it.y in 0f..g.size })
        assertTrue(g.radius + 3.5f < g.size / 2)
        listOf(0f, -1f, Float.NaN).forEach { assertTrue(runCatching { VehicleMarkerGeometry(it) }.isFailure) }
    }
}
