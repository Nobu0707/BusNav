package net.nobu0707.busnav.data.routing.valhalla

import net.nobu0707.busnav.domain.navigation.*

// Valhalla DirectionsLeg.Maneuver.Type; raw integers stay in the data layer.
internal fun mapManeuverType(type: Int): ManeuverType = when (type) {
    1, 2, 3 -> ManeuverType.START
    4, 5, 6 -> ManeuverType.DESTINATION
    7, 8 -> ManeuverType.CONTINUE
    9 -> ManeuverType.SLIGHT_RIGHT
    10 -> ManeuverType.RIGHT
    11 -> ManeuverType.SHARP_RIGHT
    12 -> ManeuverType.U_TURN_RIGHT
    13 -> ManeuverType.U_TURN_LEFT
    14 -> ManeuverType.SHARP_LEFT
    15 -> ManeuverType.LEFT
    16 -> ManeuverType.SLIGHT_LEFT
    17 -> ManeuverType.RAMP_STRAIGHT
    18 -> ManeuverType.RAMP_RIGHT
    19 -> ManeuverType.RAMP_LEFT
    20 -> ManeuverType.EXIT_RIGHT
    21 -> ManeuverType.EXIT_LEFT
    22 -> ManeuverType.KEEP_STRAIGHT
    23 -> ManeuverType.KEEP_RIGHT
    24 -> ManeuverType.KEEP_LEFT
    25, 37, 38 -> ManeuverType.MERGE
    26, 27 -> ManeuverType.ROUNDABOUT
    28, 29 -> ManeuverType.FERRY
    else -> ManeuverType.UNKNOWN
}

internal fun ValhallaSign?.toDomain(): List<HighwaySign> {
    if (this == null) return emptyList()
    return listOf(HighwaySignType.EXIT_NUMBER to numbers, HighwaySignType.EXIT_BRANCH to branches,
        HighwaySignType.EXIT_TOWARD to towards, HighwaySignType.EXIT_NAME to names).flatMap { (type, elements) ->
        elements.filter { it.text.isNotBlank() }.map { HighwaySign(type, it.text.trim(), it.consecutiveCount) }
    }.distinctBy { it.type to it.text }
}
