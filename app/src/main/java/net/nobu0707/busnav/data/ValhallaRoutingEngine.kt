package net.nobu0707.busnav.data.routing.valhalla

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.RouteGeometry
import net.nobu0707.busnav.domain.route.RouteMetadata
import net.nobu0707.busnav.domain.route.RoutePoint
import net.nobu0707.busnav.domain.route.RoutePointType
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.domain.routeplan.RoutingRequest
import net.nobu0707.busnav.domain.routing.RoutingEngine
import net.nobu0707.busnav.domain.routing.RoutingFailure
import net.nobu0707.busnav.domain.routing.RoutingResult
import net.nobu0707.busnav.domain.routing.RoutingSummary
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class RoutingConfig(
    val baseUrl: String,
    val connectTimeoutSeconds: Long = 10,
    val readTimeoutSeconds: Long = 60,
)

class ValhallaRoutingEngine(
    private val config: RoutingConfig,
    client: OkHttpClient? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
    private val routeIdFactory: () -> String = { UUID.randomUUID().toString() },
) : RoutingEngine {
    private val httpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    override suspend fun calculateRoute(request: RoutingRequest): RoutingResult {
        if (request.points.size < 2) return RoutingResult.Failure(RoutingFailure.INVALID_REQUEST)
        val endpoint = routeEndpoint() ?: return RoutingResult.Failure(RoutingFailure.CONFIGURATION)
        val body = json.encodeToString(request.toValhallaRequest())
        val httpRequest = Request.Builder()
            .url(endpoint)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .build()
        return try {
            httpClient.newCall(httpRequest).await().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    RoutingResult.Failure(classifyHttpFailure(response.code, responseBody))
                } else {
                    parseSuccess(request, responseBody)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: SocketTimeoutException) {
            RoutingResult.Failure(RoutingFailure.TIMEOUT)
        } catch (error: IOException) {
            RoutingResult.Failure(RoutingFailure.NETWORK)
        } catch (error: Exception) {
            RoutingResult.Failure(RoutingFailure.INVALID_RESPONSE)
        }
    }

    private fun routeEndpoint() = config.baseUrl
        .takeIf(String::isNotBlank)
        ?.trimEnd('/')
        ?.plus("/route")
        ?.toHttpUrlOrNull()

    private fun parseSuccess(request: RoutingRequest, body: String): RoutingResult {
        val response = try {
            json.decodeFromString<ValhallaRouteResponse>(body)
        } catch (error: SerializationException) {
            return RoutingResult.Failure(RoutingFailure.INVALID_RESPONSE)
        }
        val trip = response.trip ?: return RoutingResult.Failure(RoutingFailure.INVALID_RESPONSE)
        val summaryJson = trip.summary ?: return RoutingResult.Failure(RoutingFailure.INVALID_RESPONSE)
        val distanceKm = summaryJson.length?.takeIf { it >= 0.0 && it.isFinite() }
            ?: return RoutingResult.Failure(RoutingFailure.INVALID_RESPONSE)
        val durationSeconds = summaryJson.time?.takeIf { it >= 0.0 && it.isFinite() }
            ?: return RoutingResult.Failure(RoutingFailure.INVALID_RESPONSE)
        if (trip.legs.isEmpty()) return RoutingResult.Failure(RoutingFailure.INVALID_RESPONSE)
        val geometryPoints = mutableListOf<GeoPoint>()
        for (leg in trip.legs) {
            val shape = leg.shape ?: return RoutingResult.Failure(RoutingFailure.INVALID_RESPONSE)
            val decoded = try {
                Polyline6Decoder.decode(shape)
            } catch (error: IllegalArgumentException) {
                return RoutingResult.Failure(RoutingFailure.INVALID_RESPONSE)
            }
            if (geometryPoints.lastOrNull() == decoded.firstOrNull()) {
                geometryPoints += decoded.drop(1)
            } else {
                geometryPoints += decoded
            }
        }
        if (geometryPoints.size < 2) return RoutingResult.Failure(RoutingFailure.INVALID_RESPONSE)
        val summary = RoutingSummary(distanceKm * 1_000.0, durationSeconds)
        val route = ScheduledRoute(
            id = "${request.routePlanId}-${routeIdFactory()}",
            name = request.routeName?.takeIf(String::isNotBlank) ?: "計算ルート",
            geometry = RouteGeometry(geometryPoints),
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
        return RoutingResult.Success(route, summary)
    }

    private fun classifyHttpFailure(statusCode: Int, body: String): RoutingFailure {
        if (statusCode == 429) return RoutingFailure.RATE_LIMITED
        if (statusCode == 503) return RoutingFailure.SERVICE_UNAVAILABLE
        if (statusCode >= 500) return RoutingFailure.SERVER_ERROR
        val parsed = runCatching { json.decodeFromString<ValhallaErrorResponse>(body) }.getOrNull()
        val text = listOfNotNull(parsed?.error, parsed?.status).joinToString(" ").lowercase()
        val noRoute = parsed?.errorCode in NO_ROUTE_ERROR_CODES ||
            text.contains("no route") || text.contains("no path") || text.contains("path not found")
        return when {
            noRoute -> RoutingFailure.NO_ROUTE
            statusCode == 400 -> RoutingFailure.INVALID_REQUEST
            statusCode == 404 -> RoutingFailure.SERVICE_UNAVAILABLE
            else -> RoutingFailure.NETWORK
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                } else {
                    response.close()
                }
            }
        })
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val NO_ROUTE_ERROR_CODES = setOf(170, 171, 442)
    }
}
