package net.nobu0707.busnav.ui.navigation

import java.util.Locale
import kotlin.math.roundToInt
import net.nobu0707.busnav.domain.navigation.*

enum class GuidanceStatus { NO_ROUTE, NO_GUIDANCE, WAITING_LOCATION, LOCATING, RELIABLE, UNCERTAIN }

data class GuidanceUiState(
    val status: GuidanceStatus = GuidanceStatus.NO_ROUTE,
    val primaryText: String = "経路を選択してください",
    val secondaryText: String? = null,
    val distanceText: String? = null,
    val maneuverType: ManeuverType = ManeuverType.UNKNOWN,
    val symbol: String = "—",
    val nextNextInstruction: String? = null,
)

data class NavigationInstruction(val primary: String, val secondary: String?, val symbol: String)

object NavigationInstructionFormatter {
    fun format(maneuver: RouteManeuver): NavigationInstruction {
        val (text, symbol) = when (maneuver.type) {
            ManeuverType.START -> "出発" to "↑"
            ManeuverType.CONTINUE -> "直進" to "↑"
            ManeuverType.SLIGHT_RIGHT -> "緩やかに右方向へ" to "↗"
            ManeuverType.RIGHT -> "右折" to "→"
            ManeuverType.SHARP_RIGHT -> "大きく右折" to "↱"
            ManeuverType.U_TURN_RIGHT -> "右方向にUターン" to "↷"
            ManeuverType.U_TURN_LEFT -> "左方向にUターン" to "↶"
            ManeuverType.SHARP_LEFT -> "大きく左折" to "↰"
            ManeuverType.LEFT -> "左折" to "←"
            ManeuverType.SLIGHT_LEFT -> "緩やかに左方向へ" to "↖"
            ManeuverType.RAMP_STRAIGHT -> "正面のランプへ" to "↑"
            ManeuverType.RAMP_RIGHT -> "右方向のランプへ" to "↗"
            ManeuverType.RAMP_LEFT -> "左方向のランプへ" to "↖"
            ManeuverType.EXIT_RIGHT -> "右方向の出口へ" to "出口 ↗"
            ManeuverType.EXIT_LEFT -> "左方向の出口へ" to "↖ 出口"
            ManeuverType.KEEP_STRAIGHT -> "直進を維持" to "↑"
            ManeuverType.KEEP_RIGHT -> "右方向を維持" to "分岐 ↗"
            ManeuverType.KEEP_LEFT -> "左方向を維持" to "↖ 分岐"
            ManeuverType.MERGE -> "合流" to "合流 ↑"
            ManeuverType.ROUNDABOUT -> "環状交差点へ" to "⟳"
            ManeuverType.FERRY -> "フェリーへ" to "船"
            ManeuverType.DESTINATION -> "目的地です" to "◎"
            ManeuverType.UNKNOWN -> (maneuver.instruction.takeIf { it.isNotBlank() } ?: "案内を確認") to "?"
        }
        val signs = HighwaySignType.entries.flatMap { type ->
            maneuver.signs.filter { it.type == type }.map { it.text.trim() }.filter { it.isNotEmpty() }.distinct()
        }.distinct()
        val secondary = (signs.ifEmpty { maneuver.streetNames.filter { it.isNotBlank() }.distinct() })
            .joinToString(" / ").takeIf { it.isNotBlank() }
        return NavigationInstruction(text, secondary, symbol)
    }
}

fun formatGuidanceDistance(meters: Double): String {
    require(meters.isFinite() && meters >= 0.0)
    return when {
        meters < 1000 -> meters.roundToInt().toString() + " m"
        meters < 10_000 -> String.format(Locale.JAPAN, "%.1f km", meters / 1000)
        else -> (meters / 1000).roundToInt().toString() + " km"
    }
}

fun guidanceUiState(progress: NavigationProgress): GuidanceUiState {
    if (!progress.isProjectionReliable) return GuidanceUiState(
        status = GuidanceStatus.UNCERTAIN, primaryText = "経路付近の位置を確認中", secondaryText = "位置が不確実です",
    )
    val next = progress.nextManeuver ?: return GuidanceUiState(
        status = GuidanceStatus.NO_GUIDANCE, primaryText = "次の案内はありません",
    )
    val instruction = NavigationInstructionFormatter.format(next)
    val primary = if (next.type == ManeuverType.DESTINATION &&
        (progress.distanceToNextManeuverMeters ?: 0.0) > 15.0) "目的地へ" else instruction.primary
    return GuidanceUiState(GuidanceStatus.RELIABLE, primary, instruction.secondary,
        progress.distanceToNextManeuverMeters?.let(::formatGuidanceDistance), next.type, instruction.symbol,
        progress.nextNextManeuver?.let { following ->
            if (following.type == ManeuverType.DESTINATION) "目的地へ" else NavigationInstructionFormatter.format(following).primary
        })
}
