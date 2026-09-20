package net.nobu0707.busnav.data.routing.valhalla

import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.RouteGeometry
import net.nobu0707.busnav.domain.route.RouteMetadata
import net.nobu0707.busnav.domain.route.RoutePoint
import net.nobu0707.busnav.domain.route.RoutePointType
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.domain.routeplan.RoutingRequest
import net.nobu0707.busnav.domain.routing.RoutingResult
import net.nobu0707.busnav.domain.routing.RoutingSummary

internal enum class ValhallaResponseStage {
    EMPTY_BODY,
    JSON_DECODE,
    TRIP,
    SUMMARY,
    LEGS,
    SHAPE,
    POLYLINE,
    GEOMETRY,
    ROUTE_CONSTRUCTION,
}

internal class ValhallaResponseException(
    val stage: ValhallaResponseStage,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

internal class ValhallaRouteResponseParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
    private val routeIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val diagnostics: RoutingDiagnostics = NoOpRoutingDiagnostics,
) {
    fun parse(request: RoutingRequest, body: String): RoutingResult.Success {
        if (body.isBlank()) fail(ValhallaResponseStage.EMPTY_BODY, "Response body is empty")

        val response = try {
            json.decodeFromString<ValhallaRouteResponse>(body)
        } catch (error: SerializationException) {
            diagnostics.debug("parse.json") { "success=false" }
            fail(ValhallaResponseStage.JSON_DECODE, "Unable to decode Valhalla response", error)
        }
        diagnostics.debug("parse.json") { "success=true" }

        diagnostics.debug("parse.trip") { "present=${response.trip != null}" }
        val trip = response.trip
            ?: fail(ValhallaResponseStage.TRIP, "Response does not contain trip")

        val summaryJson = trip.summary
            ?: fail(ValhallaResponseStage.SUMMARY, "Trip does not contain summary")
        val distanceKm = summaryJson.length?.takeIf { it >= 0.0 && it.isFinite() }
            ?: fail(ValhallaResponseStage.SUMMARY, "Summary length is missing or invalid")
        val durationSeconds = summaryJson.time?.takeIf { it >= 0.0 && it.isFinite() }
            ?: fail(ValhallaResponseStage.SUMMARY, "Summary time is missing or invalid")
        diagnostics.debug("parse.summary") {
            "lengthKm=$distanceKm durationSeconds=$durationSeconds"
        }

        if (trip.legs.isEmpty()) fail(ValhallaResponseStage.LEGS, "Trip does not contain legs")
        diagnostics.debug("parse.legs") { "count=${trip.legs.size}" }

        val geometryPoints = mutableListOf<GeoPoint>()
        trip.legs.forEachIndexed { index, leg ->
            val shape = leg.shape?.takeIf(String::isNotEmpty)
                ?: fail(ValhallaResponseStage.SHAPE, "Leg $index does not contain shape")
            diagnostics.debug("parse.shape") { "leg=$index encodedLength=${shape.length}" }
            val decoded = try {
                Polyline6Decoder.decode(shape)
            } catch (error: IllegalArgumentException) {
                fail(ValhallaResponseStage.POLYLINE, "Unable to decode shape for leg $index", error)
            }
            diagnostics.debug("parse.polyline") { "leg=$index decodedPointCount=${decoded.size}" }
            if (geometryPoints.lastOrNull() == decoded.firstOrNull()) {
                geometryPoints += decoded.drop(1)
            } else {
                geometryPoints += decoded
            }
        }

        val geometry = try {
            RouteGeometry(geometryPoints)
        } catch (error: IllegalArgumentException) {
            fail(ValhallaResponseStage.GEOMETRY, "Decoded route geometry is invalid", error)
        }
        diagnostics.debug("parse.geometry") { "pointCount=${geometry.points.size}" }

        val summary = try {
            RoutingSummary(distanceKm * 1_000.0, durationSeconds)
        } catch (error: IllegalArgumentException) {
            fail(ValhallaResponseStage.SUMMARY, "Routing summary is invalid", error)
        }

        val route = try {
            ScheduledRoute(
                id = "${request.routePlanId}-${routeIdFactory()}",
                name = request.routeName?.takeIf(String::isNotBlank) ?: "計算ルート",
                geometry = geometry,
                points = request.points.map { point ->
                    RoutePoint(
                        id = point.id,
                        type = when (point.type) {
                            RoutePlanPointType.START -> RoutePointType.START
                            RoutePlanPointType.DESTINATION -> RoutePointType.DESTINATION
                            RoutePlanPointType.VIA -> RoutePointType.VIA
                            RoutePlanPointType.SHAPING -> RoutePointType.SHAPING
                        },
                        position = point.position,
                        name = point.name,
                    )
                },
                metadata = RouteMetadata(
                    description = "大型車条件による計算ルート",
                    distanceMeters = summary.distanceMeters,
                    durationSeconds = summary.durationSeconds,
                    routingSource = "valhalla",
                ),
            )
        } catch (error: IllegalArgumentException) {
            fail(ValhallaResponseStage.ROUTE_CONSTRUCTION, "Scheduled route is invalid", error)
        }
        diagnostics.debug("parse.route") { "success=true routePointCount=${route.points.size}" }
        return RoutingResult.Success(route, summary)
    }

    private fun fail(
        stage: ValhallaResponseStage,
        message: String,
        cause: Throwable? = null,
    ): Nothing = throw ValhallaResponseException(stage, message, cause)
}
