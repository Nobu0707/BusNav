package net.nobu0707.busnav.domain.navigation

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.RouteGeometry
import org.junit.Assert.*
import org.junit.Test

class HighwayGuidanceTest {
    private val index = RouteDistanceIndex(RouteGeometry((0..12).map { GeoPoint(0.0, it * .01) }))
    private fun maneuver(i: Int, type: ManeuverType, signs: List<HighwaySign> = emptyList()) =
        RouteManeuver(i, type, "", i, i, distanceMeters = 999999.0, signs = signs)
    private fun decision(distance: Double, i: Int = 0) = HighwayDecision(i, HighwayDecisionType.KEEP_LEFT,
        HighwayFacilityType.UNKNOWN, distance, HighwaySignDisplay(), emptyList())
    private fun phase(distance: Double) = HighwayGuidanceCalculator(listOf(decision(10000.0)))
        .calculate(10000.0 - distance, ProjectionReliability.RELIABLE)

    @Test fun supportedTypesExtractInRouteOrder() {
        val types = listOf(ManeuverType.RAMP_STRAIGHT, ManeuverType.RAMP_LEFT, ManeuverType.RAMP_RIGHT,
            ManeuverType.EXIT_LEFT, ManeuverType.EXIT_RIGHT, ManeuverType.KEEP_LEFT, ManeuverType.KEEP_RIGHT,
            ManeuverType.KEEP_STRAIGHT, ManeuverType.MERGE)
        val results = HighwayDecisionExtractor.extract(RouteGuidance(types.mapIndexed { i, t -> maneuver(i, t) }), index)
        assertEquals(types.size, results.size)
        assertEquals(types.indices.toList(), results.map { it.maneuverIndex })
        assertTrue(results.zipWithNext().all { (a, b) -> a.distanceAlongRouteMeters < b.distanceAlongRouteMeters })
    }
    @Test fun ordinaryTurnsAndUnknownAreExcludedEvenWithSigns() {
        val types = listOf(ManeuverType.RIGHT, ManeuverType.LEFT, ManeuverType.UNKNOWN, ManeuverType.START, ManeuverType.DESTINATION)
        assertTrue(HighwayDecisionExtractor.extract(RouteGuidance(types.mapIndexed { i, t ->
            maneuver(i, t, listOf(HighwaySign(HighwaySignType.EXIT_NAME, "東京JCT"))) }), index).isEmpty())
    }
    @Test fun continueRequiresNonblankSign() {
        assertTrue(HighwayDecisionExtractor.extract(RouteGuidance(listOf(maneuver(0, ManeuverType.CONTINUE))), index).isEmpty())
        assertEquals(1, HighwayDecisionExtractor.extract(RouteGuidance(listOf(maneuver(0, ManeuverType.CONTINUE,
            listOf(HighwaySign(HighwaySignType.EXIT_BRANCH, "E1"))))), index).size)
    }
    @Test fun geometryAxisIgnoresProviderLengths() {
        val result = HighwayDecisionExtractor.extract(RouteGuidance(listOf(maneuver(2, ManeuverType.EXIT_LEFT))), index).single()
        assertEquals(index.distanceAtGeometryIndex(2), result.distanceAlongRouteMeters, 0.0)
        assertTrue(result.distanceAlongRouteMeters < 3000)
    }
    @Test fun nullGuidanceIsEmpty() { assertTrue(HighwayDecisionExtractor.extract(null, index).isEmpty()) }
    @Test fun explicitJunctionNames() {
        listOf("八王子ＪＣＴ", "八王子JCT", "八王子jct", "東京ジャンクション", "Tokyo Junction", "JCT 東京").forEach {
            assertEquals(it, HighwayFacilityType.JUNCTION, HighwayDecisionExtractor.classifyFacility(listOf(it), HighwayDecisionType.KEEP_LEFT))
        }
    }
    @Test fun explicitInterchangeNames() {
        listOf("新富士ＩＣ", "東京IC", "東京ic", "東京インターチェンジ", "Tokyo Interchange").forEach {
            assertEquals(it, HighwayFacilityType.INTERCHANGE, HighwayDecisionExtractor.classifyFacility(listOf(it), HighwayDecisionType.EXIT_LEFT))
        }
    }
    @Test fun ambiguousAndIncidentalTextStayUnknown() {
        listOf("ICカード", "PIC", "JCTools", "ICカード 東京", "東京", "").forEach {
            assertEquals(it, HighwayFacilityType.UNKNOWN, HighwayDecisionExtractor.classifyFacility(listOf(it), HighwayDecisionType.KEEP_LEFT))
        }
        assertEquals(HighwayFacilityType.UNKNOWN, HighwayDecisionExtractor.classifyFacility(listOf("新富士ＩＣ", "東京IC", "東京JCT"), HighwayDecisionType.KEEP_LEFT))
    }
    @Test fun missingTextFallsBackToExitRampOrUnknown() {
        assertEquals(HighwayFacilityType.EXIT, HighwayDecisionExtractor.classifyFacility(emptyList(), HighwayDecisionType.EXIT_RIGHT))
        assertEquals(HighwayFacilityType.RAMP, HighwayDecisionExtractor.classifyFacility(emptyList(), HighwayDecisionType.RAMP_LEFT))
        assertEquals(HighwayFacilityType.UNKNOWN, HighwayDecisionExtractor.classifyFacility(emptyList(), HighwayDecisionType.KEEP_LEFT))
    }
    @Test fun signsTrimDeduplicateAndPreservePriorityAndOrder() {
        val signs = listOf(HighwaySign(HighwaySignType.EXIT_NAME, "名古屋IC"), HighwaySign(HighwaySignType.EXIT_TOWARD, " 名古屋 "),
            HighwaySign(HighwaySignType.EXIT_BRANCH, " E1 ", 2), HighwaySign(HighwaySignType.EXIT_BRANCH, "C4"),
            HighwaySign(HighwaySignType.EXIT_BRANCH, "E1"), HighwaySign(HighwaySignType.EXIT_NUMBER, "12"),
            HighwaySign(HighwaySignType.EXIT_NAME, "名古屋"), HighwaySign(HighwaySignType.EXIT_TOWARD, " "))
        val display = HighwayDecisionExtractor.signDisplay(signs)
        assertEquals("12", display.exitNumber)
        assertEquals(listOf("E1", "C4"), display.routeRefs)
        assertEquals(listOf("名古屋"), display.toward)
        assertEquals(listOf("名古屋IC"), display.facilityNames)
        assertEquals(2, signs[2].consecutiveCount)
    }
    @Test fun previewBoundary() {
        assertEquals(HighwayGuidancePhase.HIGHWAY_CRUISE, phase(5000.001).phase)
        assertEquals(HighwayGuidancePhase.APPROACHING_DECISION, phase(5000.0).phase)
        assertEquals(HighwayGuidancePhase.APPROACHING_DECISION, phase(4999.999).phase)
    }
    @Test fun approachEmphasisBoundary() {
        assertFalse(phase(2000.001).isApproachEmphasized)
        assertTrue(phase(2000.0).isApproachEmphasized)
        assertTrue(phase(1999.999).isApproachEmphasized)
    }
    @Test fun imminentBoundary() {
        assertEquals(HighwayGuidancePhase.APPROACHING_DECISION, phase(700.001).phase)
        assertEquals(HighwayGuidancePhase.IMMINENT_DECISION, phase(700.0).phase)
        assertEquals(HighwayGuidancePhase.IMMINENT_DECISION, phase(699.999).phase)
    }
    @Test fun transitionBoundary() {
        assertEquals(HighwayGuidancePhase.IMMINENT_DECISION, phase(120.001).phase)
        assertEquals(HighwayGuidancePhase.TRANSITION, phase(120.0).phase)
        assertEquals(HighwayGuidancePhase.TRANSITION, phase(119.999).phase)
    }
    @Test fun zeroBoundaryKeepsTransitionWithoutNegativeDistance() {
        listOf(.001, 0.0, -.001).forEach { assertEquals(HighwayGuidancePhase.TRANSITION, phase(it).phase) }
        assertEquals(0.0, phase(-.001).distanceToDecisionMeters!!, 0.0)
    }
    @Test fun passedToleranceBoundary() {
        assertNotNull(phase(-29.999).currentDecision)
        assertNotNull(phase(-30.0).currentDecision)
        assertNull(phase(-30.001).currentDecision)
    }
    @Test fun closeDecisionsRollOverWithoutBlankAndPromoteNext() {
        val calculator = HighwayGuidanceCalculator(listOf(decision(1000.0), decision(1600.0, 1), decision(2000.0, 2)))
        val before = calculator.calculate(900.0, ProjectionReliability.RELIABLE)
        assertEquals(0, before.currentDecision!!.maneuverIndex)
        assertEquals(1, before.nextDecision!!.maneuverIndex)
        val after = calculator.calculate(1030.001, ProjectionReliability.RELIABLE, before)
        assertEquals(1, after.currentDecision!!.maneuverIndex)
        assertEquals(2, after.nextDecision!!.maneuverIndex)
        assertEquals(569.999, after.distanceToDecisionMeters!!, .00001)
        assertEquals(HighwayGuidancePhase.IMMINENT_DECISION, after.phase)
    }
    @Test fun unreliableRetainsDecisionButSuppressesBothDistances() {
        val calculator = HighwayGuidanceCalculator(listOf(decision(1000.0), decision(1600.0, 1)))
        val before = calculator.calculate(900.0, ProjectionReliability.RELIABLE)
        listOf(ProjectionReliability.UNCERTAIN, ProjectionReliability.UNRELIABLE).forEach {
            val result = calculator.calculate(1500.0, it, before)
            assertEquals(before.currentDecision, result.currentDecision)
            assertFalse(result.isReliable)
            assertNull(result.distanceToDecisionMeters)
            assertNull(result.distanceToNextDecisionMeters)
        }
    }
    @Test fun reliableRecoveryAdvancesFromHeldDecision() {
        val calculator = HighwayGuidanceCalculator(listOf(decision(1000.0), decision(1600.0, 1)))
        val held = calculator.calculate(900.0, ProjectionReliability.RELIABLE)
        assertEquals(1, calculator.calculate(1400.0, ProjectionReliability.RELIABLE, held).currentDecision!!.maneuverIndex)
    }
    @Test fun presentationHysteresisPreventsBoundaryFlicker() {
        val calculator = HighwayGuidanceCalculator(listOf(decision(10000.0)))
        val active = calculator.calculate(5000.0, ProjectionReliability.RELIABLE)
        assertEquals(active.phase, calculator.calculate(4990.0, ProjectionReliability.RELIABLE, active).phase)
        assertEquals(HighwayGuidancePhase.HIGHWAY_CRUISE, calculator.calculate(4899.0, ProjectionReliability.RELIABLE, active).phase)
    }
    @Test fun missingOrNonfiniteProgressDoesNotInventDecision() {
        assertNull(HighwayGuidanceCalculator(emptyList()).calculate(0.0, ProjectionReliability.RELIABLE).currentDecision)
        assertFalse(phase(Double.NaN).isReliable)
    }
    @Test fun invalidThresholdsRejected() {
        assertThrows(IllegalArgumentException::class.java) { HighwayGuidanceConfig(previewDistanceMeters = 1.0) }
        assertThrows(IllegalArgumentException::class.java) { HighwayGuidanceConfig(passedToleranceMeters = Double.NaN) }
    }
}
