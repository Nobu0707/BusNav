package net.nobu0707.busnav.data.routing.valhalla

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.domain.routeplan.RoutingRequest

@Serializable
internal data class ValhallaRouteRequest(
    val locations: List<ValhallaLocation>,
    val costing: String = "truck",
    @SerialName("costing_options") val costingOptions: ValhallaCostingOptions,
    val units: String = "kilometers",
    @SerialName("shape_format") val shapeFormat: String = "polyline6",
    @SerialName("directions_type") val directionsType: String = "none",
)

@Serializable
internal data class ValhallaLocation(
    val lat: Double,
    val lon: Double,
    val type: String,
    val name: String? = null,
)

@Serializable
internal data class ValhallaCostingOptions(val truck: ValhallaTruckOptions)

@Serializable
internal data class ValhallaTruckOptions(
    val height: Double,
    val width: Double,
    val length: Double,
    val weight: Double,
    @SerialName("axle_load") val axleLoad: Double? = null,
    @SerialName("use_highways") val useHighways: Double = 0.8,
    @SerialName("use_living_streets") val useLivingStreets: Double = 0.1,
    @SerialName("use_tracks") val useTracks: Double = 0.0,
    @SerialName("exclude_unpaved") val excludeUnpaved: Boolean = true,
)

@Serializable
internal data class ValhallaRouteResponse(val trip: ValhallaTrip? = null)

@Serializable
internal data class ValhallaTrip(
    val summary: ValhallaSummary? = null,
    val legs: List<ValhallaLeg> = emptyList(),
)

@Serializable
internal data class ValhallaSummary(val length: Double? = null, val time: Double? = null)

@Serializable
internal data class ValhallaLeg(val shape: String? = null)

@Serializable
internal data class ValhallaErrorResponse(
    val error: String? = null,
    @SerialName("error_code") val errorCode: Int? = null,
    val status: String? = null,
)

internal fun RoutingRequest.toValhallaRequest(): ValhallaRouteRequest {
    val profile = vehicleProfile
    return ValhallaRouteRequest(
        locations = points.map { point ->
            ValhallaLocation(
                lat = point.position.latitude,
                lon = point.position.longitude,
                type = when (point.type) {
                    RoutePlanPointType.START, RoutePlanPointType.DESTINATION -> "break"
                    RoutePlanPointType.VIA -> "via"
                    RoutePlanPointType.SHAPING -> "through"
                },
                name = point.name,
            )
        },
        costingOptions = ValhallaCostingOptions(
            truck = ValhallaTruckOptions(
                height = profile.heightMeters,
                width = profile.widthMeters,
                length = profile.lengthMeters,
                weight = profile.weightMetricTons,
                axleLoad = profile.axleLoadMetricTons,
            ),
        ),
    )
}
