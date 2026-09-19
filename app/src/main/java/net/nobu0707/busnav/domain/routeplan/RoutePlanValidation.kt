package net.nobu0707.busnav.domain.routeplan

enum class RoutePlanValidationError {
    MISSING_START,
    MULTIPLE_STARTS,
    MISSING_DESTINATION,
    MULTIPLE_DESTINATIONS,
    DUPLICATE_POINT_IDS,
    START_MUST_PRECEDE_DESTINATION,
    INTERMEDIATE_OUTSIDE_ENDPOINTS,
}

data class RoutePlanValidationResult(
    val errors: Set<RoutePlanValidationError>,
) {
    val isRoutingReady: Boolean get() = errors.isEmpty()
}

fun RoutePlan.validateForRouting(): RoutePlanValidationResult {
    val errors = linkedSetOf<RoutePlanValidationError>()
    val starts = points.withIndex().filter { it.value.type == RoutePlanPointType.START }
    val destinations = points.withIndex().filter { it.value.type == RoutePlanPointType.DESTINATION }

    when (starts.size) {
        0 -> errors += RoutePlanValidationError.MISSING_START
        1 -> Unit
        else -> errors += RoutePlanValidationError.MULTIPLE_STARTS
    }
    when (destinations.size) {
        0 -> errors += RoutePlanValidationError.MISSING_DESTINATION
        1 -> Unit
        else -> errors += RoutePlanValidationError.MULTIPLE_DESTINATIONS
    }
    if (points.map(RoutePlanPoint::id).distinct().size != points.size) {
        errors += RoutePlanValidationError.DUPLICATE_POINT_IDS
    }

    if (starts.size == 1 && destinations.size == 1) {
        val startIndex = starts.single().index
        val destinationIndex = destinations.single().index
        if (startIndex >= destinationIndex) {
            errors += RoutePlanValidationError.START_MUST_PRECEDE_DESTINATION
        }
        if (points.withIndex().any { (index, point) ->
                point.type in INTERMEDIATE_TYPES && index !in (startIndex + 1) until destinationIndex
            }
        ) {
            errors += RoutePlanValidationError.INTERMEDIATE_OUTSIDE_ENDPOINTS
        }
    }

    return RoutePlanValidationResult(errors)
}

internal val INTERMEDIATE_TYPES = setOf(RoutePlanPointType.VIA, RoutePlanPointType.SHAPING)
