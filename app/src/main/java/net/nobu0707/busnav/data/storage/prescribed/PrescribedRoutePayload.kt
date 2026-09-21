package net.nobu0707.busnav.data.storage.prescribed

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.prescribed.PrescribedRouteRecord
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.domain.routeplan.*
import net.nobu0707.busnav.domain.routing.VehicleProfile

@Serializable data class PointPayload(val latitude: Double, val longitude: Double) {
    fun domain() = GeoPoint(latitude, longitude)
}
@Serializable data class RoutePointPayload(val id: String, val type: String, val position: PointPayload, val name: String?)
@Serializable data class RoutePlanPayload(val id: String, val name: String?, val points: List<RoutePointPayload>)
@Serializable data class MetadataPayload(val description: String?, val distanceMeters: Double?, val durationSeconds: Double?, val routingSource: String?)
@Serializable data class SignPayload(val type: String, val text: String, val consecutiveCount: Int?)
@Serializable data class ManeuverPayload(
    val index: Int, val type: String, val instruction: String, val beginGeometryIndex: Int, val endGeometryIndex: Int,
    val verbalPreTransitionInstruction: String?, val verbalPostTransitionInstruction: String?,
    val streetNames: List<String>, val distanceMeters: Double?, val durationSeconds: Double?, val signs: List<SignPayload>,
)
@Serializable data class ScheduledRoutePayload(
    val id: String, val name: String, val geometry: List<PointPayload>,
    val points: List<RoutePointPayload>, val metadata: MetadataPayload, val maneuvers: List<ManeuverPayload>?,
)
@Serializable data class VehicleProfilePayload(
    val id: String, val name: String, val lengthMeters: Double, val widthMeters: Double,
    val heightMeters: Double, val weightMetricTons: Double, val axleLoadMetricTons: Double?,
)
@Serializable data class PrescribedRoutePayloadV1(
    val schemaVersion: Int = 1, val plan: RoutePlanPayload,
    val route: ScheduledRoutePayload, val vehicle: VehicleProfilePayload,
)

class UnsupportedPrescribedRouteSchema : IllegalArgumentException("Unsupported prescribed route schema")

object PrescribedRouteCodec {
    private val json = Json { encodeDefaults = true }
    fun encode(record: PrescribedRouteRecord): String = json.encodeToString(PrescribedRoutePayloadV1.serializer(), payload(record))
    fun decode(value: String): PrescribedRoutePayloadV1 {
        val element = json.parseToJsonElement(value)
        // Inspect version before mapping future DTO fields. Future migrations dispatch here.
        val version = element.jsonObject["schemaVersion"]?.jsonPrimitive?.int
            ?: throw IllegalArgumentException("Missing payload schema version")
        if (version != 1) throw UnsupportedPrescribedRouteSchema()
        return json.decodeFromJsonElement(PrescribedRoutePayloadV1.serializer(), element)
    }

    fun payload(record: PrescribedRouteRecord): PrescribedRoutePayloadV1 {
        val plan = record.routePlan
        val route = record.route
        val vehicle = record.vehicleProfile
        return PrescribedRoutePayloadV1(
            plan = RoutePlanPayload(plan.id, plan.name, plan.points.map { RoutePointPayload(it.id, it.type.name, it.position.payload(), it.name) }),
            route = ScheduledRoutePayload(route.id, route.name, route.geometry.points.map { it.payload() },
                route.points.map { RoutePointPayload(it.id, it.type.name, it.position.payload(), it.name) },
                route.metadata.let { MetadataPayload(it.description, it.distanceMeters, it.durationSeconds, it.routingSource) },
                route.guidance?.maneuvers?.map { m -> ManeuverPayload(m.index, m.type.name, m.instruction,
                    m.beginGeometryIndex, m.endGeometryIndex, m.verbalPreTransitionInstruction, m.verbalPostTransitionInstruction,
                    m.streetNames, m.distanceMeters, m.durationSeconds, m.signs.map { SignPayload(it.type.name, it.text, it.consecutiveCount) }) }),
            vehicle = VehicleProfilePayload(vehicle.id, vehicle.name, vehicle.lengthMeters, vehicle.widthMeters,
                vehicle.heightMeters, vehicle.weightMetricTons, vehicle.axleLoadMetricTons),
        )
    }

    fun record(id: String, name: String, description: String?, created: Long, updated: Long,
        payload: PrescribedRoutePayloadV1): PrescribedRouteRecord {
        require(payload.schemaVersion == 1)
        val p = payload.plan
        val r = payload.route
        val v = payload.vehicle
        return PrescribedRouteRecord(id, name, description,
            RoutePlan(p.id, p.name, p.points.map { RoutePlanPoint(it.id, RoutePlanPointType.valueOf(it.type), it.position.domain(), it.name) }),
            ScheduledRoute(r.id, r.name, RouteGeometry(r.geometry.map { it.domain() }),
                r.points.map { RoutePoint(it.id, RoutePointType.valueOf(it.type), it.position.domain(), it.name) },
                r.metadata.let { RouteMetadata(it.description, it.distanceMeters, it.durationSeconds, it.routingSource) },
                r.maneuvers?.let { maneuvers -> RouteGuidance(maneuvers.map { m -> RouteManeuver(m.index, ManeuverType.valueOf(m.type),
                    m.instruction, m.beginGeometryIndex, m.endGeometryIndex, m.verbalPreTransitionInstruction, m.verbalPostTransitionInstruction,
                    m.streetNames, m.distanceMeters, m.durationSeconds, m.signs.map { HighwaySign(HighwaySignType.valueOf(it.type), it.text, it.consecutiveCount) }) }) }),
            VehicleProfile(v.id, v.name, v.lengthMeters, v.widthMeters, v.heightMeters, v.weightMetricTons, v.axleLoadMetricTons),
            created, updated).also { it.validate() }
    }
    private fun GeoPoint.payload() = PointPayload(latitude, longitude)
}