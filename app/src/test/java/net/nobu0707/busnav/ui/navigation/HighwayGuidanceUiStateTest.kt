package net.nobu0707.busnav.ui.navigation

import net.nobu0707.busnav.domain.navigation.*
import org.junit.Assert.*
import org.junit.Test

class HighwayGuidanceUiStateTest {
    private fun snapshot(type: HighwayDecisionType) = HighwayGuidanceSnapshot(HighwayGuidancePhase.IMMINENT_DECISION,
        HighwayDecision(0, type, HighwayFacilityType.UNKNOWN, 1000.0, HighwaySignDisplay(), emptyList()),
        distanceToDecisionMeters = 500.0, isReliable = true)
    @Test fun missingSignsHaveSafeDirectionFallbacks() {
        val expected = mapOf(HighwayDecisionType.EXIT_LEFT to "左方向の出口へ", HighwayDecisionType.EXIT_RIGHT to "右方向の出口へ",
            HighwayDecisionType.KEEP_LEFT to "分岐を左方向へ", HighwayDecisionType.KEEP_RIGHT to "分岐を右方向へ",
            HighwayDecisionType.RAMP_LEFT to "左方向のランプへ", HighwayDecisionType.RAMP_RIGHT to "右方向のランプへ",
            HighwayDecisionType.RAMP_STRAIGHT to "正面のランプへ", HighwayDecisionType.MERGE to "合流")
        expected.forEach { (type, text) ->
            val result = HighwayInstructionFormatter.format(snapshot(type))!!
            assertEquals(text, result.primaryText)
            assertFalse(result.contentDescription.contains("車線"))
            assertFalse(result.contentDescription.contains("JCT"))
        }
    }
    @Test fun schematicBranchesMatchManeuvers() {
        assertEquals(SchematicDirection.LEFT, HighwayInstructionFormatter.schematic(HighwayDecisionType.KEEP_LEFT).selectedBranch)
        assertEquals(SchematicDirection.RIGHT, HighwayInstructionFormatter.schematic(HighwayDecisionType.KEEP_RIGHT).selectedBranch)
        assertEquals(JunctionSchematicModel(SchematicDirection.LEFT, true), HighwayInstructionFormatter.schematic(HighwayDecisionType.EXIT_LEFT))
        assertEquals(JunctionSchematicModel(SchematicDirection.RIGHT, true), HighwayInstructionFormatter.schematic(HighwayDecisionType.EXIT_RIGHT))
        assertEquals(SchematicDirection.STRAIGHT, HighwayInstructionFormatter.schematic(HighwayDecisionType.RAMP_STRAIGHT).selectedBranch)
        assertEquals(SchematicDirection.MERGE, HighwayInstructionFormatter.schematic(HighwayDecisionType.MERGE).selectedBranch)
    }
    @Test fun uncertainSuppressesSchematicSignsDistanceAndNext() {
        val result = HighwayInstructionFormatter.format(snapshot(HighwayDecisionType.KEEP_LEFT).copy(isReliable = false))!!
        assertEquals("経路上の位置を確認中", result.primaryText)
        assertNull(result.schematic); assertNull(result.distanceText); assertNull(result.nextText)
        assertEquals(HighwaySignDisplay(), result.sign)
    }
    @Test fun distantDecisionUsesGeneralPresentation() {
        assertNull(HighwayInstructionFormatter.format(snapshot(HighwayDecisionType.KEEP_LEFT).copy(phase = HighwayGuidancePhase.HIGHWAY_CRUISE)))
    }
    @Test fun routeBadgesRetainUnknownFormatsAndAccessibleNext() {
        val base = snapshot(HighwayDecisionType.EXIT_LEFT)
        val current = base.currentDecision!!.copy(sign = HighwaySignDisplay("12", listOf("E1", "C4", "国道1号", "未知路線"), listOf("甲府方面"), listOf("八王子JCT")))
        val result = HighwayInstructionFormatter.format(base.copy(currentDecision = current, nextDecision = current.copy(maneuverIndex = 1), distanceToNextDecisionMeters = 1600.0))!!
        assertEquals(current.sign.routeRefs, result.sign.routeRefs)
        assertTrue(result.contentDescription.contains("出口 12"))
        assertTrue(result.contentDescription.contains("甲府方面"))
        assertTrue(result.nextText!!.contains("1.6 km"))
    }
}
