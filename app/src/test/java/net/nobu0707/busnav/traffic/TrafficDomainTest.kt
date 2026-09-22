package net.nobu0707.busnav.traffic

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.nobu0707.busnav.detour.*
import net.nobu0707.busnav.domain.detour.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.domain.traffic.*
import net.nobu0707.busnav.ui.traffic.*
import org.junit.Assert.*
import org.junit.Test

class TrafficDomainTest {
    private val source = TrafficSourceInfo("test", "合成データ", false)
    private val route = detourFixture(13).route.copy(guidance = RouteGuidance(listOf(
        RouteManeuver(0, ManeuverType.CONTINUE, "", 0, 12, streetNames = listOf("合成高速道路", "E1")))))
    private fun event(geometry: TrafficGeometry = TrafficGeometry.Polyline(listOf(point(1500.0), point(2400.0))),
        kind: TrafficEventKind = TrafficEventKind.ROAD_CLOSURE) = TrafficEvent("e", kind, TrafficSeverity.CRITICAL, "合成規制", geometry, source,
        roadReference = "E1", direction = TrafficDirection.FORWARD, updatedAtEpochMillis = 1000)
    private fun snapshot(vararg events: TrafficEvent) = TrafficSnapshot(source, TrafficProviderStatus.AVAILABLE, events.toList(), 1000)
    private fun impact(event: TrafficEvent = event(), progress: Double? = 100.0) = TrafficRouteImpactAnalyzer(route).analyze(snapshot(event), 2000, progress).single()
    private fun line(offset: Double) = TrafficGeometry.Polyline(listOf(point(1500.0, offset), point(2400.0, offset)))

    @Test fun noOpIsNotConfigured() = runTest {
        val provider = NoOpTrafficInformationProvider()
        assertEquals(TrafficProviderStatus.NOT_CONFIGURED, provider.observeTraffic().first().status)
        assertEquals(TrafficProviderStatus.NOT_CONFIGURED, provider.refresh().status)
    }
    @Test fun emptyAvailableDiffersFromUnavailable() {
        assertEquals(TrafficProviderStatus.AVAILABLE, snapshot().effectiveStatus(2000))
        assertNotEquals(TrafficUiState(snapshot(), TrafficProviderStatus.AVAILABLE).statusText,
            TrafficUiState(snapshot().copy(status = TrafficProviderStatus.UNAVAILABLE), TrafficProviderStatus.UNAVAILABLE).statusText)
    }
    @Test fun staleThresholdAndMissingTimestamp() {
        assertEquals(TrafficProviderStatus.AVAILABLE, snapshot().effectiveStatus(300999))
        assertEquals(TrafficProviderStatus.STALE, snapshot().effectiveStatus(301000))
        assertEquals(TrafficProviderStatus.STALE, snapshot().copy(receivedAtEpochMillis = null).effectiveStatus(2000))
    }
    @Test fun dataTimestampControlsAge() {
        val s = snapshot().copy(receivedAtEpochMillis = 10000, dataUpdatedAtEpochMillis = 1000)
        assertEquals(9000L, s.ageMillis(10000))
    }
    @Test fun unavailableAndErrorKeepLastKnownEventsNotClearance() {
        for (status in listOf(TrafficProviderStatus.ERROR, TrafficProviderStatus.UNAVAILABLE, TrafficProviderStatus.STALE)) {
            val old = snapshot(event())
            val next = snapshot().copy(status = status, receivedAtEpochMillis = 5000).reconcile(old)
            assertEquals(old.events, next.events); assertEquals(1000L, next.receivedAtEpochMillis)
            assertEquals(status, next.effectiveStatus(6000))
        }
    }
    @Test fun replacementDeletionAndProviderSwitch() {
        val old = snapshot(event())
        assertTrue(snapshot().reconcile(old).events.isEmpty())
        assertEquals("new", snapshot(event(), event().copy(title = "new")).reconcile(old).events.single().title)
        assertTrue(snapshot().copy(source = source.copy(providerId = "other"), status = TrafficProviderStatus.ERROR).reconcile(old).events.isEmpty())
    }
    @Test fun futureAndExpiryAreExcludedAtBoundaries() {
        val analyzer = TrafficRouteImpactAnalyzer(route)
        val e = event().copy(validFromEpochMillis = 3000, validUntilEpochMillis = 4000)
        assertTrue(analyzer.analyze(snapshot(e), 2999, 0.0).isEmpty())
        assertEquals(1, analyzer.analyze(snapshot(e), 3000, 0.0).size)
        assertTrue(analyzer.analyze(snapshot(e), 4000, 0.0).isEmpty())
    }
    @Test fun updateTimeFallsBackToSnapshot() { assertEquals(1000L, snapshot().updatedAt(event().copy(updatedAtEpochMillis = null))) }
    @Test fun pointOnRouteRequiresIdentityAndDirectionEvidence() {
        val e = event(TrafficGeometry.Point(point(1800.0))).copy(bearingDegrees = 90.0)
        assertEquals(TrafficImpactLevel.BLOCKING, impact(e).level)
        assertFalse(impact(e).endKnown)
        assertNull(impact(e).detourContext().minimumSafeRejoinProgress)
    }
    @Test fun distanceAloneCannotMakePointBlocking() {
        val e = event(TrafficGeometry.Point(point(1800.0))).copy(roadReference = null, direction = TrafficDirection.UNKNOWN)
        assertEquals(TrafficMatchConfidence.AMBIGUOUS, impact(e).matchingConfidence)
        assertNotEquals(TrafficImpactLevel.BLOCKING, impact(e).level)
    }
    @Test fun pointNearWithUnknownRoadStaysAmbiguous() {
        val e = event(TrafficGeometry.Point(point(1800.0, 22.0))).copy(roadReference = null, direction = TrafficDirection.BOTH)
        assertEquals(TrafficImpactPosition.AMBIGUOUS, impact(e).position)
    }
    @Test fun polylineOverlapHasKnownProgressExtent() {
        val actual = impact()
        assertEquals(TrafficImpactLevel.BLOCKING, actual.level)
        assertTrue(actual.endKnown)
        assertEquals(1500.0, actual.startProgressMeters!!, 5.0)
        assertEquals(2400.0, actual.endProgressMeters!!, 5.0)
    }
    @Test fun longSparseSegmentsMatchOverlap() {
        val sparse = route.copy(geometry = RouteGeometry(listOf(point(0.0), point(12000.0))), guidance = null)
        val actual = TrafficRouteImpactAnalyzer(sparse).match(event().copy(roadReference = null))
        assertEquals(TrafficMatchConfidence.HIGH, actual.matchingConfidence)
        assertTrue(actual.endKnown)
    }
    @Test fun polygonCrossingWithoutContainedRouteVertices() {
        val polygon = TrafficGeometry.Polygon(listOf(point(1400.0, -100.0), point(1600.0, -100.0), point(1600.0, 100.0), point(1400.0, 100.0)))
        val actual = impact(event(polygon, TrafficEventKind.WEATHER_HAZARD).copy(direction = TrafficDirection.BOTH))
        assertEquals(TrafficImpactPosition.AHEAD, actual.position)
        assertEquals(TrafficImpactLevel.INFORMATION, actual.level)
        assertEquals(1400.0, actual.startProgressMeters!!, 5.0)
    }
    @Test fun behindEventHasNoAheadDistance() {
        val actual = impact(progress = 3000.0)
        assertEquals(TrafficImpactPosition.BEHIND, actual.position); assertNull(actual.distanceAheadMeters)
    }
    @Test fun aheadEventDistanceUsesMatchedProgress() {
        val actual = impact(progress = 500.0)
        assertEquals(TrafficImpactPosition.AHEAD, actual.position); assertEquals(1000.0, actual.distanceAheadMeters!!, 5.0)
    }
    @Test fun currentEventSpansProgress() { assertEquals(TrafficImpactPosition.CURRENT, impact(progress = 2000.0).position) }
    @Test fun forwardDirectionMatches() { assertEquals(TrafficMatchConfidence.HIGH, impact().matchingConfidence) }
    @Test fun reverseDirectionIsOffRoute() { assertEquals(TrafficImpactPosition.OFF_ROUTE, impact(event().copy(direction = TrafficDirection.REVERSE)).position) }
    @Test fun reversedGeometryWithReverseDirectionMatches() {
        val e = event(TrafficGeometry.Polyline(listOf(point(2400.0), point(1500.0)))).copy(direction = TrafficDirection.REVERSE)
        assertEquals(TrafficMatchConfidence.HIGH, impact(e).matchingConfidence)
    }
    @Test fun unknownDirectionCannotBeBlocking() {
        assertNotEquals(TrafficImpactLevel.BLOCKING, impact(event().copy(direction = TrafficDirection.UNKNOWN)).level)
    }
    @Test fun roadNameEvidenceIsSegmentLocal() {
        assertEquals(TrafficMatchConfidence.HIGH, impact(event().copy(roadReference = null, roadName = "合成高速道路")).matchingConfidence)
        val other = route.copy(guidance = RouteGuidance(listOf(RouteManeuver(0, ManeuverType.CONTINUE, "", 8, 12, streetNames = listOf("E1")))))
        assertNotEquals(TrafficMatchConfidence.HIGH, TrafficRouteImpactAnalyzer(other).match(event(line(22.0))).matchingConfidence)
    }
    @Test fun parallelRoadPointAndLineNeverBlockMotorway() {
        for (offset in listOf(15.0, 22.0, 30.0)) for (geometry in listOf(line(offset), TrafficGeometry.Point(point(1800.0, offset)))) {
            val actual = impact(event(geometry).copy(roadReference = null, roadName = "一般道", bearingDegrees = 90.0))
            assertEquals(TrafficMatchConfidence.AMBIGUOUS, actual.matchingConfidence)
            assertNotEquals(TrafficImpactLevel.BLOCKING, actual.level)
        }
    }
    @Test fun parallelWithoutIdentityAlsoStaysAmbiguous() {
        assertEquals(TrafficMatchConfidence.AMBIGUOUS, impact(event(line(22.0)).copy(roadReference = null)).matchingConfidence)
    }
    @Test fun offRouteBeyondCorridor() { assertEquals(TrafficImpactPosition.OFF_ROUTE, impact(event(line(200.0))).position) }
    @Test fun perpendicularCrossingCannotBlock() {
        assertNotEquals(TrafficImpactLevel.BLOCKING, impact(event(TrafficGeometry.Polyline(listOf(point(1800.0,-100.0),point(1800.0,100.0))))).level)
    }
    @Test fun repeatedRouteOccurrencesAreAmbiguous() {
        val loop = route.copy(geometry = RouteGeometry(listOf(point(0.0),point(3000.0),point(6000.0,300.0),point(0.0),point(3000.0))), guidance = null)
        val actual = TrafficRouteImpactAnalyzer(loop).match(event().copy(direction = TrafficDirection.BOTH))
        assertEquals(TrafficMatchConfidence.AMBIGUOUS, actual.matchingConfidence)
        assertFalse(actual.endKnown)
    }
    @Test fun unreliableProgressSuppressesDistanceEvenWithStrongGeometry() {
        val actual = impact(progress = null)
        assertEquals(TrafficMatchConfidence.HIGH, actual.matchingConfidence)
        assertNull(actual.distanceAheadMeters); assertEquals(TrafficImpactPosition.AMBIGUOUS, actual.position)
    }
    @Test fun closureKindsBlockOnlyWithStrongEvidence() {
        for (kind in listOf(TrafficEventKind.ROAD_CLOSURE,TrafficEventKind.ENTRY_CLOSURE,TrafficEventKind.EXIT_CLOSURE,TrafficEventKind.WINTER_CLOSURE))
            assertEquals(TrafficImpactLevel.BLOCKING,impact(event(kind = kind)).level)
    }
    @Test fun laneSpeedAccidentWorkAreRestrictions() {
        for (kind in listOf(TrafficEventKind.LANE_RESTRICTION,TrafficEventKind.SPEED_RESTRICTION,TrafficEventKind.ACCIDENT,TrafficEventKind.ROADWORK))
            assertEquals(TrafficImpactLevel.RESTRICTION, impact(event(kind = kind)).level)
    }
    @Test fun congestionIsDelayAndWeatherIsInformationRegardlessOfSeverity() {
        assertEquals(TrafficImpactLevel.DELAY, impact(event(kind = TrafficEventKind.CONGESTION)).level)
        assertEquals(TrafficImpactLevel.INFORMATION, impact(event(kind = TrafficEventKind.WEATHER_HAZARD)).level)
    }
    @Test fun severityThenDistanceSorting() {
        val near = event().copy(id = "near")
        val far = event(TrafficGeometry.Polyline(listOf(point(3500.0),point(4000.0)))).copy(id = "far")
        val delay = near.copy(id = "delay", kind = TrafficEventKind.CONGESTION)
        assertEquals(listOf("near","far","delay"),TrafficRouteImpactAnalyzer(route).analyze(snapshot(delay,far,near),2000,0.0).map { it.event.id })
    }
    @Test fun nearerClosurePrecedesFartherHighwayClosureAtSameLevel() {
        val highway = route.copy(guidance = RouteGuidance(listOf(
            RouteManeuver(0, ManeuverType.CONTINUE, "", 0, 3, streetNames = listOf("E1")),
            RouteManeuver(1, ManeuverType.RAMP_RIGHT, "", 3, 12, streetNames = listOf("E1")))))
        val far = event(TrafficGeometry.Polyline(listOf(point(3000.0), point(3400.0))), TrafficEventKind.ENTRY_CLOSURE).copy(id = "far")
        val impacts = TrafficRouteImpactAnalyzer(highway).analyze(snapshot(far, event()), 2000, 0.0)
        assertNotNull(impacts.single { it.event.id == "far" }.highwayDecisionLabel)
        assertEquals(listOf("e", "far"), impacts.map { it.event.id })
    }
    @Test fun rejoinFloorAffectsAutomaticAndManualTargets() {
        val floor = impact().detourContext().minimumSafeRejoinProgress!!
        val generator = RejoinCandidateGenerator(route, 100.0, minimumSafeRejoinProgress = floor)
        assertTrue(generator.generate().isNotEmpty())
        assertTrue(generator.generate().all { it.progressMeters >= floor })
        assertNotNull(generator.manual(point(2000.0)).error)
    }
    @Test fun unknownEndDoesNotInventFloor() {
        assertNull(impact(event(TrafficGeometry.Point(point(1800.0))).copy(direction = TrafficDirection.BOTH)).detourContext().minimumSafeRejoinProgress)
    }
    @Test fun validatorDeniesHighConfidenceClosure() {
        val result = TrafficDetourValidator().validate(route, snapshot(event()), 2000)
        assertEquals(TrafficDetourConflict.BLOCKING_CONFLICT, result.conflict); assertFalse(result.activationAllowed)
    }
    @Test fun ambiguousConflictWarnsWithoutProhibition() {
        val result = TrafficDetourValidator().validate(route, snapshot(event(line(22.0)).copy(roadReference = null, roadName = "一般道")),2000)
        assertEquals(TrafficDetourConflict.POTENTIAL_CONFLICT,result.conflict); assertTrue(result.activationAllowed); assertNotNull(result.message)
    }
    @Test fun manualViaBypassesClosureAndExpiredClosureDoesNotBlock() {
        val bypass = route.copy(geometry = RouteGeometry(listOf(point(100.0), point(1000.0,200.0),point(3000.0,200.0),point(3500.0))),guidance = null)
        assertEquals(TrafficDetourConflict.NONE,TrafficDetourValidator().validate(bypass,snapshot(event()),2000).conflict)
        assertEquals(TrafficDetourConflict.NONE,TrafficDetourValidator().validate(route,snapshot(event().copy(validUntilEpochMillis = 1500)),2000).conflict)
    }
    @Test fun unavailableDoesNotPromiseAvoidance() {
        assertEquals(TrafficDetourConflict.UNKNOWN,TrafficDetourValidator().validate(route,snapshot().copy(status = TrafficProviderStatus.NOT_CONFIGURED),2000).conflict)
    }
    @Test fun highwayClosureNeedsIdentityAndDecisionProgress() {
        val highway = route.copy(guidance = RouteGuidance(listOf(RouteManeuver(0,ManeuverType.RAMP_RIGHT,"",2,3,streetNames = listOf("E1")))))
        assertNotNull(TrafficRouteImpactAnalyzer(highway).match(event(kind = TrafficEventKind.ENTRY_CLOSURE)).highwayDecisionLabel)
        assertNull(TrafficRouteImpactAnalyzer(highway).match(event(kind = TrafficEventKind.ENTRY_CLOSURE).copy(roadReference = "別道路")).highwayDecisionLabel)
    }
}
