package net.nobu0707.busnav.domain.routeplan

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.routing.VehicleProfile

data class RoutingRequestPoint(
    val id: String,
    val position: GeoPoint,
    val type: RoutePlanPointType,
    val name: String?,
)

data class RoutingRequest(
    val routePlanId: String,
    val routeName: String?,
    val points: List<RoutingRequestPoint>,
    val vehicleProfile: VehicleProfile,
) {
    val origin: GeoPoint get() = points.first { it.type == RoutePlanPointType.START }.position
    val destination: GeoPoint get() = points.first { it.type == RoutePlanPointType.DESTINATION }.position
    val intermediatePoints: List<RoutingRequestPoint>
        get() = points.filter { it.type == RoutePlanPointType.VIA || it.type == RoutePlanPointType.SHAPING }
}

sealed interface RoutingRequestResult {
    data class Ready(val request: RoutingRequest) : RoutingRequestResult
    data class Invalid(val validation: RoutePlanValidationResult) : RoutingRequestResult
}

fun RoutePlan.toRoutingRequest(
    vehicleProfile: VehicleProfile = VehicleProfile.DEVELOPMENT_LARGE_BUS,
): RoutingRequestResult {
    val validation = validateForRouting()
    if (!validation.isRoutingReady) return RoutingRequestResult.Invalid(validation)
    return RoutingRequestResult.Ready(
        RoutingRequest(
            routePlanId = id,
            routeName = name,
            points = points.map {
                RoutingRequestPoint(id = it.id, position = it.position, type = it.type, name = it.name)
            },
            vehicleProfile = vehicleProfile,
        ),
    )
}
