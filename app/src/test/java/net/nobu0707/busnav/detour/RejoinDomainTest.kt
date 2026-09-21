package net.nobu0707.busnav.detour

import net.nobu0707.busnav.domain.detour.*
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.location.LocationState
import org.junit.Assert.*
import org.junit.Test

class RejoinDomainTest {
    private val route = detourFixture().route
    private val index = RouteDistanceIndex(route.geometry)
    private val config = DetourConfig()
    private fun generator(anchor: Double = 100.0, c: DetourConfig = config) = RejoinCandidateGenerator(route, anchor, c)
    private fun fix(time: Long, progress: Double = 900.0, accuracy: Float? = 5f) =
        LocationState(point(progress), accuracy, 90f, null, 0, time)
    private fun match(progress: Double = 900.0, quality: RouteMatchQuality = RouteMatchQuality.MATCHED,
        heading: Double? = null, distance: Double = 0.0) = RouteMatch(
        RouteProjector.project(point(progress), route.geometry, index).copy(distanceFromRouteMeters = distance,
            distanceAlongRouteMeters = progress), quality, 1.0, heading, null, 1)
    private fun confirm(progress: Double = 900.0): RejoinSnapshot {
        val detector = RejoinDetector()
        var state = RejoinSnapshot()
        for (time in listOf(1000L, 2000L, 3000L)) state = detector.update(state, match(progress), fix(time, progress), 600.0, time)
        return state
    }
    @Test fun futureOnlyOrderedSpacedAndCapped() {
        val candidates = generator().generate()
        assertEquals(3, candidates.size)
        assertTrue(candidates.all { it.progressMeters > 600.0 && it.progressMeters <= 10100.0 })
        assertTrue(candidates.zipWithNext().all { (a,b) -> b.progressMeters - a.progressMeters >= 500.0 })
        assertEquals(1, generator(c = config.copy(maximumCandidates = 1)).generate().size)
    }
    @Test fun minimumForwardIsStrict() {
        assertNotNull(generator().problem(600.0)); assertNull(generator().problem(600.001))
    }
    @Test fun spacingSuppressesNearbyPreferredOffsets() {
        assertEquals(2, generator(c = config.copy(preferredOffsetsMeters = listOf(1000.0,1100.0,2000.0))).generate().size)
    }
    @Test fun destinationAndMaximumForwardAreExcluded() {
        assertNotNull(generator().problem(index.totalMeters - 300))
        assertNotNull(generator().problem(10100.001))
    }
    @Test fun zeroCandidatesNearDestinationIsValid() { assertTrue(generator(index.totalMeters - 600).generate().isEmpty()) }
    @Test fun everyUnsafeManeuverAndHighwayDecisionIsBuffered() {
        for (type in ManeuverType.entries.filter { it.name.startsWith("EXIT") || it.name.startsWith("RAMP") ||
            it.name.startsWith("KEEP") || it.name.startsWith("U_TURN") || it == ManeuverType.MERGE || it == ManeuverType.DESTINATION }) {
            val hazard = index.distanceAtGeometryIndex(100)
            val unsafe = route.copy(guidance = RouteGuidance(listOf(RouteManeuver(0, type, "", 100, 100))))
            val g = RejoinCandidateGenerator(unsafe, 100.0, config.copy(preferredOffsetsMeters = listOf(hazard - 100)))
            assertTrue(type.name, g.generate().isEmpty())
            assertNotNull(g.problem(hazard + 200))
            assertNull(g.problem(hazard + 201))
            assertNull(g.manual(route.geometry.points[100]).target)
        }
        val signed = route.copy(guidance = RouteGuidance(listOf(RouteManeuver(0, ManeuverType.CONTINUE, "", 100, 100,
            signs = listOf(HighwaySign(HighwaySignType.EXIT_NAME, "公開JCT"))))))
        assertNotNull(RejoinCandidateGenerator(signed, 100.0).manual(route.geometry.points[100]).error)
    }
    @Test fun manualProjectsToOriginalRoute() {
        val selected = generator().manual(point(1500.0, 20.0)).target!!
        assertEquals(RejoinTargetSource.MANUAL, selected.source)
        assertEquals(35.68, selected.point.latitude, 0.000001)
        assertTrue(selected.geometryIndex in route.geometry.points.indices)
        assertNotEquals(point(1500.0,20.0), selected.point)
    }
    @Test fun manualTooFarRejected() { assertNotNull(generator().manual(point(1500.0,100.0)).error) }
    @Test fun manualBehindRejected() { assertNotNull(generator().manual(point(50.0)).error) }
    @Test fun manualDestinationRejected() { assertNotNull(generator().manual(route.geometry.last).error) }
    @Test fun floorFiltersPastCrossingBeforeScoring() {
        val loop = RouteGeometry(listOf(point(0.0), point(1000.0), point(1000.0,500.0), point(0.0,500.0), point(0.0), point(1000.0)))
        val matcher = RouteMatcher(RouteMatchIndex(RouteDistanceIndex(loop), RouteMatcherConfig()))
        val result = matcher.match(fix(1000, 100.0), RouteMatcherState(), 1000, RouteMatchConstraint(2500.0))
        assertTrue(result.match!!.projection.distanceAlongRouteMeters > 2500)
        val pastOnly = RouteMatcher(RouteMatchIndex(index, RouteMatcherConfig())).match(fix(1000,100.0), RouteMatcherState(),1000,RouteMatchConstraint(600.0))
        assertTrue(pastOnly.match == null || pastOnly.match!!.projection.distanceFromRouteMeters > 25.0)
    }
    @Test fun noEligibleProjectionDoesNotCrashOrInventProgress() {
        val matcher = RouteMatcher(RouteMatchIndex(index, RouteMatcherConfig()))
        assertNull(matcher.match(fix(1000), RouteMatcherState(),1000,RouteMatchConstraint(index.totalMeters+1)).match)
    }
    @Test fun singleFixCannotConfirm() {
        assertEquals(RejoinState.CANDIDATE, RejoinDetector().update(RejoinSnapshot(),match(),fix(1000),600.0,1000).state)
    }
    @Test fun requiredFixesAndDurationBothApply() {
        val d = RejoinDetector(); var s = RejoinSnapshot()
        for (t in listOf(1000L,1100L,1200L)) s = d.update(s,match(),fix(t),600.0,t)
        assertEquals(RejoinState.CANDIDATE,s.state)
        s = d.update(s,match(),fix(3000),600.0,3000)
        assertEquals(RejoinState.CONFIRMED,s.state)
    }
    @Test fun exactEarlyAndBeyondPlannedTargetAllConfirm() {
        for (progress in listOf(600.0,900.0,1100.0,4000.0)) {
            assertEquals(RejoinState.CONFIRMED, confirm(progress).state)
            assertEquals(progress,confirm(progress).actualProgressMeters!!,0.0)
        }
    }
    @Test fun behindFloorCannotConfirm() { assertEquals(RejoinState.SEARCHING, confirm(599.0).state) }
    @Test fun unreliableAmbiguousPoorAccuracyAndHeadingBreakEvidence() {
        val d = RejoinDetector()
        val first = d.update(RejoinSnapshot(),match(),fix(1000),600.0,1000)
        for (m in listOf(match(quality=RouteMatchQuality.AMBIGUOUS),match(quality=RouteMatchQuality.UNRELIABLE),
            match(heading=45.1),match(distance=25.1))) {
            assertEquals(0,d.update(first,m,fix(2000),600.0,2000).consecutiveFixes)
        }
        for (accuracy in listOf(null, Float.NaN, -1f, 30.1f))
            assertEquals(0,d.update(first,match(),fix(2000,accuracy=accuracy),600.0,2000).consecutiveFixes)
    }
    @Test fun headingAbsentAndThresholdBoundaryAreAccepted() {
        for (heading in listOf(null,45.0)) assertEquals(RejoinState.CANDIDATE,
            RejoinDetector().update(RejoinSnapshot(),match(heading=heading,distance=25.0),fix(1000,accuracy=30f),600.0,1000).state)
    }
    @Test fun duplicateStaleAndSeparatedFixesDoNotAccumulate() {
        val d = RejoinDetector(); val first=d.update(RejoinSnapshot(),match(),fix(1000),600.0,1000)
        assertEquals(first,d.update(first,match(),fix(1000),600.0,1000))
        assertEquals(0,d.update(first,match(),fix(2000),600.0,20000).consecutiveFixes)
        assertEquals(1,d.update(first,match(),fix(12000),600.0,12000).consecutiveFixes)
    }
    @Test fun speedPolicyDefinesNullAndBoundary() {
        assertFalse(config.editingLocked(null)); assertFalse(config.editingLocked(2f)); assertTrue(config.editingLocked(2.1f))
    }
    @Test fun dualMatcherPerformance4001Points1000Fixes() {
        val original = detourFixture(4001).route
        val detour = original.copy(geometry = RouteGeometry(original.geometry.points.map { it.copy(latitude = it.latitude + 0.001) }))
        val a = RouteMatcher(RouteMatchIndex(RouteDistanceIndex(original.geometry),RouteMatcherConfig()))
        val b = RouteMatcher(RouteMatchIndex(RouteDistanceIndex(detour.geometry),RouteMatcherConfig()))
        var sa=RouteMatcherState(); var sb=RouteMatcherState()
        val started = System.nanoTime()
        repeat(1000) { i ->
            val time=1000L+i*1000
            val f=fix(time,1000.0+i*2).copy(speedMetersPerSecond=2f)
            sa=a.match(f,sa,time,RouteMatchConstraint(600.0)).state
            sb=b.match(f,sb,time).state
        }
        println("DETOUR_DUAL_MATCHER_MS_PER_UPDATE=" + (System.nanoTime()-started)/1_000_000.0/1000)
        assertEquals(1000000L,sa.lastTimestampMillis)
    }
}
