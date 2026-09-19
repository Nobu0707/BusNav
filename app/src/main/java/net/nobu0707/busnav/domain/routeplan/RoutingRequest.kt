package net.nobu0707.busnav.domain.routeplan

import net.nobu0707.busnav.domain.model.GeoPoint

data class RoutingRequestPoint(
    val position: GeoPoint,
    val type: RoutePlanPointType,
    val name: String?,
)

data class RoutingRequest(
    val routePlanId: String,
    val origin: GeoPoint,
    val destination: GeoPoint,
    val intermediatePoints: List<RoutingRequestPoint>,
)

sealed interface RoutingRequestResult {
    data class Ready(val request: RoutingRequest) : RoutingRequestResult
    data class Invalid(val validation: RoutePlanValidationResult) : RoutingRequestResult
}

fun RoutePlan.toRoutingRequest(): RoutingRequestResult {
    val validation = validateForRouting()
    if (!validation.isRoutingReady) return RoutingRequestResult.Invalid(validation)
    val start = points.single { it.type == RoutePlanPointType.START }
    val destination = points.single { it.type == RoutePlanPointType.DESTINATION }
    return RoutingRequestResult.Ready(
        RoutingRequest(
            routePlanId = id,
            origin = start.position,
            destination = destination.position,
            intermediatePoints = points.filter { it.type in INTERMEDIATE_TYPES }.map {
                RoutingRequestPoint(position = it.position, type = it.type, name = it.name)
            },
        ),
    )
}
