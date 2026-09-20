package net.nobu0707.busnav.ui.navigation

import net.nobu0707.busnav.domain.navigation.*

enum class SchematicDirection { LEFT, RIGHT, STRAIGHT, MERGE }
data class JunctionSchematicModel(val selectedBranch: SchematicDirection, val isExit: Boolean = false)

data class HighwayGuidanceUiState(
    val primaryText: String,
    val distanceText: String? = null,
    val sign: HighwaySignDisplay = HighwaySignDisplay(),
    val secondaryText: String? = null,
    val nextText: String? = null,
    val schematic: JunctionSchematicModel? = null,
    val emphasized: Boolean = false,
) {
    val contentDescription: String get() = listOfNotNull(distanceText?.let { "${it}先" }, primaryText,
        sign.exitNumber?.let { "出口 $it" }, sign.facilityNames.joinToString("、").ifEmpty { null },
        sign.toward.joinToString("、").ifEmpty { null }, sign.routeRefs.joinToString("、").ifEmpty { null }, secondaryText,
        nextText?.let { "その次: $it" }).joinToString("、")
}

object HighwayInstructionFormatter {
    fun direction(type: HighwayDecisionType): String = when (type) {
        HighwayDecisionType.CONTINUE -> "分岐を直進"
        HighwayDecisionType.KEEP_LEFT -> "分岐を左方向へ"
        HighwayDecisionType.KEEP_RIGHT -> "分岐を右方向へ"
        HighwayDecisionType.EXIT_LEFT -> "左方向の出口へ"
        HighwayDecisionType.EXIT_RIGHT -> "右方向の出口へ"
        HighwayDecisionType.RAMP_LEFT -> "左方向のランプへ"
        HighwayDecisionType.RAMP_RIGHT -> "右方向のランプへ"
        HighwayDecisionType.RAMP_STRAIGHT -> "正面のランプへ"
        HighwayDecisionType.MERGE -> "合流"
    }

    fun schematic(type: HighwayDecisionType): JunctionSchematicModel = JunctionSchematicModel(when (type) {
        HighwayDecisionType.KEEP_LEFT, HighwayDecisionType.EXIT_LEFT, HighwayDecisionType.RAMP_LEFT -> SchematicDirection.LEFT
        HighwayDecisionType.KEEP_RIGHT, HighwayDecisionType.EXIT_RIGHT, HighwayDecisionType.RAMP_RIGHT -> SchematicDirection.RIGHT
        HighwayDecisionType.MERGE -> SchematicDirection.MERGE
        else -> SchematicDirection.STRAIGHT
    }, type == HighwayDecisionType.EXIT_LEFT || type == HighwayDecisionType.EXIT_RIGHT)

    fun format(snapshot: HighwayGuidanceSnapshot): HighwayGuidanceUiState? {
        if (snapshot.phase == HighwayGuidancePhase.NONE || snapshot.phase == HighwayGuidancePhase.HIGHWAY_CRUISE) return null
        if (!snapshot.isReliable) return HighwayGuidanceUiState("経路上の位置を確認中")
        val current = snapshot.currentDecision ?: return null
        return HighwayGuidanceUiState(direction(current.type), snapshot.distanceToDecisionMeters?.let(::formatGuidanceDistance),
            current.sign, current.streetNames.joinToString(" / ").takeIf {
                it.isNotEmpty() && current.sign.routeRefs.isEmpty() && current.sign.toward.isEmpty() && current.sign.facilityNames.isEmpty()
            }, snapshot.nextDecision?.let { next ->
                listOfNotNull(snapshot.distanceToNextDecisionMeters?.let(::formatGuidanceDistance), direction(next.type),
                    (next.sign.facilityNames + next.sign.routeRefs + next.sign.toward).distinct().joinToString(" / ").ifEmpty { null })
                    .joinToString(" · ")
            }, schematic(current.type), snapshot.isApproachEmphasized)
    }
}
