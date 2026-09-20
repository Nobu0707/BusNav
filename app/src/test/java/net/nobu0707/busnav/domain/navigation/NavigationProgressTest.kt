package net.nobu0707.busnav.domain.navigation

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.*
import org.junit.Assert.*
import org.junit.Test

class NavigationProgressTest {
    private fun point(meters: Double, north: Double = 0.0) = GeoPoint(
        Math.toDegrees(north / EARTH_RADIUS_METERS), Math.toDegrees(meters / EARTH_RADIUS_METERS))
    private val geometry = RouteGeometry(listOf(0.0, 100.0, 250.0, 400.0).map { point(it) })
    private val index = RouteDistanceIndex(geometry)
    private fun maneuver(i: Int, begin: Int, type: ManeuverType = ManeuverType.RIGHT) =
        RouteManeuver(i, type, "", begin, begin, distanceMeters = 50.0)
    private fun route(maneuvers: List<RouteManeuver>) = ScheduledRoute("r", "route", geometry,
        listOf(RoutePoint("s", RoutePointType.START, geometry.first), RoutePoint("d", RoutePointType.DESTINATION, geometry.last)),
        guidance = RouteGuidance(maneuvers))
    private fun calculator() = NavigationProgressCalculator(route(listOf(maneuver(0, 1), maneuver(1, 2, ManeuverType.LEFT),
        maneuver(2, 3, ManeuverType.DESTINATION))))

    @Test fun cumulativeDistanceIsMonotonicAndUsesMeters() {
        assertArrayEquals(doubleArrayOf(0.0, 100.0, 250.0, 400.0), index.cumulativeMeters, 0.00001)
        assertEquals(400.0, index.totalMeters, 0.00001)
        assertEquals(300.0, index.distanceBetweenIndices(1, 3), 0.00001)
        val copy = index.cumulativeMeters; copy[1] = -1.0
        assertEquals(100.0, index.distanceAtGeometryIndex(1), 0.00001)
    }
    @Test fun invalidDistanceIndicesAreRejected() {
        listOf(-1, 4).forEach { invalid -> assertThrows(IllegalArgumentException::class.java) { index.distanceAtGeometryIndex(invalid) } }
        assertThrows(IllegalArgumentException::class.java) { index.distanceBetweenIndices(2, 1) }
    }
    @Test fun geometryAxisRegressionDoesNotSubtractProviderLength() {
        val calculator = NavigationProgressCalculator(route(listOf(maneuver(0, 3))))
        val progress = calculator.calculate(point(175.0))
        assertEquals(175.0, progress.distanceAlongRouteMeters, 0.0001)
        assertEquals(225.0, progress.distanceToNextManeuverMeters!!, 0.0001)
        assertEquals(50.0, progress.nextManeuver!!.distanceMeters!!, 0.0)
    }
    @Test fun projectsExactlyOnRoute() {
        val p = RouteProjector.project(point(175.0), geometry, index)
        assertEquals(1, p.segmentIndex); assertEquals(0.5, p.fraction, 0.00001)
        assertEquals(0.0, p.distanceFromRouteMeters, 0.00001)
    }
    @Test fun projectsOffRouteWithDistanceAndPoint() {
        val p = RouteProjector.project(point(175.0, 50.0), geometry, index)
        assertEquals(50.0, p.distanceFromRouteMeters, 0.001)
        assertEquals(175.0, p.distanceAlongRouteMeters, 0.001)
        assertEquals(0.0, p.projectedPoint.latitude, 0.00001)
    }
    @Test fun clampsBeforeStartAndAfterEnd() {
        assertEquals(0.0, RouteProjector.project(point(-40.0), geometry, index).distanceAlongRouteMeters, 0.001)
        assertEquals(400.0, RouteProjector.project(point(480.0), geometry, index).distanceAlongRouteMeters, 0.001)
    }
    @Test fun longSegmentProjectionIsAccurate() {
        val g = RouteGeometry(listOf(point(0.0), point(5000.0)))
        val p = RouteProjector.project(point(3000.0, 200.0), g, RouteDistanceIndex(g))
        assertEquals(3000.0, p.distanceAlongRouteMeters, 0.01)
        assertEquals(200.0, p.distanceFromRouteMeters, 0.01)
    }
    @Test fun hintAndFallbackFindGlobalNearestSegment() {
        for (hint in listOf(null, 1, 0, 999, -1)) {
            assertEquals(350.0, RouteProjector.project(point(350.0), geometry, index, hint).distanceAlongRouteMeters, 0.001)
        }
    }
    @Test fun duplicatePointsRemainFinite() {
        val g = RouteGeometry(listOf(point(0.0), point(0.0), point(100.0)))
        assertEquals(50.0, RouteProjector.project(point(50.0), g, RouteDistanceIndex(g)).distanceAlongRouteMeters, 0.001)
    }
    @Test fun allDuplicateRouteIsSupported() {
        val g = RouteGeometry(listOf(point(0.0), point(0.0)))
        assertEquals(0.0, RouteProjector.project(point(0.0), g, RouteDistanceIndex(g)).distanceAlongRouteMeters, 0.0)
    }
    @Test fun wrongDistanceIndexIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { RouteProjector.project(point(0.0), geometry, RouteDistanceIndex(RouteGeometry(geometry.points))) }
    }
    @Test fun beforeFirstAndApproachingSelectNextAndNextNext() {
        val calc = calculator()
        assertEquals(ManeuverType.RIGHT, calc.calculate(point(0.0)).nextManeuver?.type)
        val progress = calc.calculate(point(80.0))
        assertEquals(20.0, progress.distanceToNextManeuverMeters!!, 0.001)
        assertEquals(ManeuverType.LEFT, progress.nextNextManeuver?.type)
    }
    @Test fun passedToleranceRetainsTurnThenAdvances() {
        val calc = calculator()
        assertEquals(ManeuverType.RIGHT, calc.calculate(point(105.0)).nextManeuver?.type)
        assertEquals(0.0, calc.calculate(point(105.0)).distanceToNextManeuverMeters!!, 0.0)
        assertEquals(ManeuverType.LEFT, calc.calculate(point(116.0)).nextManeuver?.type)
    }
    @Test fun destinationAndRemainingDistance() {
        val p = calculator().calculate(point(400.0))
        assertEquals(ManeuverType.DESTINATION, p.nextManeuver?.type)
        assertNull(p.nextNextManeuver); assertEquals(0.0, p.remainingRouteMeters, 0.001)
    }
    @Test fun reliabilityHasThreeLevels() {
        val calc = calculator()
        assertEquals(ProjectionReliability.RELIABLE, calc.calculate(point(80.0, 29.0)).reliability)
        assertEquals(ProjectionReliability.UNCERTAIN, calc.calculate(point(80.0, 50.0)).reliability)
        assertEquals(ProjectionReliability.UNRELIABLE, calc.calculate(point(80.0, 81.0)).reliability)
    }
    @Test fun trackerHoldsSmallReverseAndPermitsLargeReverse() {
        val tracker = NavigationProgressTracker(calculator())
        tracker.update(point(120.0))
        assertEquals(120.0, tracker.update(point(112.0)).distanceAlongRouteMeters, 0.001)
        assertEquals(70.0, tracker.update(point(70.0)).distanceAlongRouteMeters, 0.001)
    }
    @Test fun uncertainFixDoesNotPoisonTracker() {
        val tracker = NavigationProgressTracker(calculator())
        tracker.update(point(120.0)); tracker.update(point(300.0, 100.0))
        assertEquals(120.0, tracker.update(point(115.0)).distanceAlongRouteMeters, 0.001)
    }
    @Test fun emptyGuidanceHasNoNext() {
        assertNull(NavigationProgressCalculator(route(emptyList())).calculate(point(10.0)).nextManeuver)
    }
    @Test fun invalidDomainIndicesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { route(listOf(maneuver(0, 4))) }
    }
}
