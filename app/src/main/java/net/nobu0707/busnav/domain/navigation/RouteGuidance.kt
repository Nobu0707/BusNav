package net.nobu0707.busnav.domain.navigation

/** Geometry and guidance are owned by the same immutable ScheduledRoute snapshot. */
data class RouteGuidance(val maneuvers: List<RouteManeuver>)

data class RouteManeuver(
    val index: Int,
    val type: ManeuverType,
    val instruction: String,
    val beginGeometryIndex: Int,
    val endGeometryIndex: Int,
    val verbalPreTransitionInstruction: String? = null,
    val verbalPostTransitionInstruction: String? = null,
    val streetNames: List<String> = emptyList(),
    /** Provider segment length is metadata, NEVER the live progress distance axis. */
    val distanceMeters: Double? = null,
    val durationSeconds: Double? = null,
    val signs: List<HighwaySign> = emptyList(),
)

enum class ManeuverType {
    START, CONTINUE, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, U_TURN_RIGHT, U_TURN_LEFT,
    SHARP_LEFT, LEFT, SLIGHT_LEFT, RAMP_STRAIGHT, RAMP_RIGHT, RAMP_LEFT,
    EXIT_RIGHT, EXIT_LEFT, KEEP_STRAIGHT, KEEP_RIGHT, KEEP_LEFT, MERGE, ROUNDABOUT,
    FERRY, DESTINATION, UNKNOWN,
}

data class HighwaySign(val type: HighwaySignType, val text: String, val consecutiveCount: Int? = null)
enum class HighwaySignType { EXIT_NUMBER, EXIT_BRANCH, EXIT_TOWARD, EXIT_NAME }
