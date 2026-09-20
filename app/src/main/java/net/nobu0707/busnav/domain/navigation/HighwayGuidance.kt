package net.nobu0707.busnav.domain.navigation

enum class HighwayGuidancePhase { NONE, HIGHWAY_CRUISE, APPROACHING_DECISION, IMMINENT_DECISION, TRANSITION }
enum class HighwayDecisionType { CONTINUE, KEEP_LEFT, KEEP_RIGHT, EXIT_LEFT, EXIT_RIGHT, RAMP_LEFT, RAMP_RIGHT, RAMP_STRAIGHT, MERGE }
enum class HighwayFacilityType { JUNCTION, INTERCHANGE, EXIT, RAMP, UNKNOWN }

data class HighwaySignDisplay(
    val exitNumber: String? = null,
    val routeRefs: List<String> = emptyList(),
    val toward: List<String> = emptyList(),
    val facilityNames: List<String> = emptyList(),
)

data class HighwayDecision(
    val maneuverIndex: Int,
    val type: HighwayDecisionType,
    val facilityType: HighwayFacilityType,
    val distanceAlongRouteMeters: Double,
    val sign: HighwaySignDisplay,
    val streetNames: List<String>,
)

data class HighwayGuidanceSnapshot(
    val phase: HighwayGuidancePhase = HighwayGuidancePhase.NONE,
    val currentDecision: HighwayDecision? = null,
    val nextDecision: HighwayDecision? = null,
    val distanceToDecisionMeters: Double? = null,
    val distanceToNextDecisionMeters: Double? = null,
    val isReliable: Boolean = false,
    val isApproachEmphasized: Boolean = false,
)

object HighwayDecisionExtractor {
    private val junction = Regex("(?:ジャンクション|(?<![A-Za-z])(?:JCT|Junction))(?:$|[\\s・/、（(])", RegexOption.IGNORE_CASE)
    private val interchange = Regex("(?:インターチェンジ|(?<![A-Za-z])(?:IC|Interchange))(?:$|[\\s・/、（(])", RegexOption.IGNORE_CASE)

    fun classifyFacility(texts: List<String>, type: HighwayDecisionType): HighwayFacilityType {
        val jct = texts.any { junction.containsMatchIn(java.text.Normalizer.normalize(it.trim(), java.text.Normalizer.Form.NFKC)) }
        val ic = texts.any { interchange.containsMatchIn(java.text.Normalizer.normalize(it.trim(), java.text.Normalizer.Form.NFKC)) }
        return when {
            jct && ic -> HighwayFacilityType.UNKNOWN
            jct -> HighwayFacilityType.JUNCTION
            ic -> HighwayFacilityType.INTERCHANGE
            type == HighwayDecisionType.EXIT_LEFT || type == HighwayDecisionType.EXIT_RIGHT -> HighwayFacilityType.EXIT
            type in ramps -> HighwayFacilityType.RAMP
            else -> HighwayFacilityType.UNKNOWN
        }
    }

    fun signDisplay(signs: List<HighwaySign>): HighwaySignDisplay {
        val seen = mutableSetOf<String>()
        fun values(type: HighwaySignType) = signs.filter { it.type == type }.map { it.text.trim() }
            .filter { it.isNotEmpty() && seen.add(it) }
        return HighwaySignDisplay(values(HighwaySignType.EXIT_NUMBER).joinToString(" / ").ifEmpty { null },
            values(HighwaySignType.EXIT_BRANCH), values(HighwaySignType.EXIT_TOWARD), values(HighwaySignType.EXIT_NAME))
    }

    fun extract(guidance: RouteGuidance?, distanceIndex: RouteDistanceIndex): List<HighwayDecision> =
        guidance?.maneuvers.orEmpty().mapNotNull { maneuver ->
            val type = when (maneuver.type) {
                ManeuverType.RAMP_STRAIGHT -> HighwayDecisionType.RAMP_STRAIGHT
                ManeuverType.RAMP_RIGHT -> HighwayDecisionType.RAMP_RIGHT
                ManeuverType.RAMP_LEFT -> HighwayDecisionType.RAMP_LEFT
                ManeuverType.EXIT_RIGHT -> HighwayDecisionType.EXIT_RIGHT
                ManeuverType.EXIT_LEFT -> HighwayDecisionType.EXIT_LEFT
                ManeuverType.KEEP_STRAIGHT -> HighwayDecisionType.CONTINUE
                ManeuverType.KEEP_RIGHT -> HighwayDecisionType.KEEP_RIGHT
                ManeuverType.KEEP_LEFT -> HighwayDecisionType.KEEP_LEFT
                ManeuverType.MERGE -> HighwayDecisionType.MERGE
                ManeuverType.CONTINUE -> if (maneuver.signs.any { it.text.isNotBlank() }) HighwayDecisionType.CONTINUE else null
                else -> null
            } ?: return@mapNotNull null
            HighwayDecision(maneuver.index, type, classifyFacility(maneuver.signs.map { it.text }, type),
                distanceIndex.distanceAtGeometryIndex(maneuver.beginGeometryIndex), signDisplay(maneuver.signs),
                maneuver.streetNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct())
        }.sortedBy { it.distanceAlongRouteMeters }

    private val ramps = setOf(HighwayDecisionType.RAMP_LEFT, HighwayDecisionType.RAMP_RIGHT, HighwayDecisionType.RAMP_STRAIGHT)
}

data class HighwayGuidanceConfig(
    val previewDistanceMeters: Double = 5000.0,
    val approachDistanceMeters: Double = 2000.0,
    val imminentDistanceMeters: Double = 700.0,
    val transitionDistanceMeters: Double = 120.0,
    val passedToleranceMeters: Double = 30.0,
    val presentationHysteresisMeters: Double = 100.0,
) {
    init {
        val thresholds = listOf(previewDistanceMeters, approachDistanceMeters, imminentDistanceMeters, transitionDistanceMeters, passedToleranceMeters)
        require(thresholds.all { it.isFinite() && it >= 0 } && thresholds.zipWithNext().all { (a, b) -> a >= b })
        require(presentationHysteresisMeters.isFinite() && presentationHysteresisMeters >= 0)
    }
}

/** Pure transition; the caller owns the previous snapshot. No clock, routing or position prediction. */
class HighwayGuidanceCalculator(val decisions: List<HighwayDecision>, val config: HighwayGuidanceConfig = HighwayGuidanceConfig()) {
    fun calculate(progressMeters: Double, reliability: ProjectionReliability,
        previous: HighwayGuidanceSnapshot? = null): HighwayGuidanceSnapshot {
        if (!progressMeters.isFinite() || reliability != ProjectionReliability.RELIABLE) {
            return (previous ?: HighwayGuidanceSnapshot()).copy(isReliable = false,
                distanceToDecisionMeters = null, distanceToNextDecisionMeters = null, isApproachEmphasized = false)
        }
        val index = decisions.indexOfFirst { it.distanceAlongRouteMeters + config.passedToleranceMeters >= progressMeters }
        if (index < 0) return HighwayGuidanceSnapshot(isReliable = true)
        val current = decisions[index]
        val next = decisions.getOrNull(index + 1)
        val distance = (current.distanceAlongRouteMeters - progressMeters).coerceAtLeast(0.0)
        val retained = previous?.currentDecision == current && previous.phase != HighwayGuidancePhase.HIGHWAY_CRUISE &&
            previous.phase != HighwayGuidancePhase.NONE
        val preview = config.previewDistanceMeters + if (retained) config.presentationHysteresisMeters else 0.0
        val phase = when {
            distance <= config.transitionDistanceMeters -> HighwayGuidancePhase.TRANSITION
            distance <= config.imminentDistanceMeters -> HighwayGuidancePhase.IMMINENT_DECISION
            distance <= preview -> HighwayGuidancePhase.APPROACHING_DECISION
            else -> HighwayGuidancePhase.HIGHWAY_CRUISE
        }
        return HighwayGuidanceSnapshot(phase, current, next, distance,
            next?.let { (it.distanceAlongRouteMeters - progressMeters).coerceAtLeast(0.0) }, true,
            distance <= config.approachDistanceMeters)
    }
}
