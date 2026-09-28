package net.nobu0707.busnav.data.facility

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import net.nobu0707.busnav.domain.facility.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.RouteGeometry
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** This host is fixed: a failed IPv6 connection must not fall back to another service. */
class HttpRouteFacilityProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build(),
    private val url: String = "https://search-busnav.nobu0707.net/busnav/v1/route-facilities",
) : RouteFacilityProvider {
    override suspend fun findFacilities(route: RouteGeometry, options: RouteFacilityQueryOptions): RouteFacilityQueryResult =
        withContext(Dispatchers.IO) {
            val polyline = try { RouteFacilityPolyline.encode(route) }
                catch (_: IllegalArgumentException) { return@withContext RouteFacilityQueryResult.Failure(RouteFacilityFailure.InvalidResponse) }
            val payload = buildJsonObject {
                put("route_polyline6", polyline)
                put("corridor_meters", options.corridorMeters)
                put("types", JsonArray(options.types.sortedBy { it.name }.map { JsonPrimitive(it.name) }))
                put("limit", options.limit)
            }.toString()
            if (payload.toByteArray(Charsets.UTF_8).size > 128 * 1024)
                return@withContext RouteFacilityQueryResult.Failure(RouteFacilityFailure.Http413)
            val request = Request.Builder().url(url).post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json").build()
            try {
                client.newCall(request).execute().use { response ->
                    val failure = when (response.code) {
                        400 -> RouteFacilityFailure.Http400
                        413 -> RouteFacilityFailure.Http413
                        429 -> RouteFacilityFailure.Http429
                        in 500..599 -> RouteFacilityFailure.ServerUnavailable
                        in 200..299 -> null
                        else -> RouteFacilityFailure.InvalidResponse
                    }
                    if (failure != null) return@withContext RouteFacilityQueryResult.Failure(failure)
                    val body = response.body?.string() ?: return@withContext RouteFacilityQueryResult.Failure(RouteFacilityFailure.Parse)
                    try { RouteFacilityQueryResult.Success(RouteFacilityResponseParser.parse(body)) }
                    catch (_: IllegalArgumentException) { RouteFacilityQueryResult.Failure(RouteFacilityFailure.Parse) }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: SocketTimeoutException) { RouteFacilityQueryResult.Failure(RouteFacilityFailure.Timeout) }
            catch (_: IOException) { RouteFacilityQueryResult.Failure(RouteFacilityFailure.Network) }
        }
}

object RouteFacilityResponseParser {
    fun parse(body: String): List<RouteFacilityCandidate> {
        val root = Json.parseToJsonElement(body) as? JsonObject ?: throw IllegalArgumentException("Expected object")
        val version = root["version"] ?: root["schema_version"]
        if (version != null && (version as? JsonPrimitive)?.intOrNull != 1)
            throw IllegalArgumentException("Unsupported version")
        val rows = (root["facilities"] ?: root["candidates"]) as? JsonArray
            ?: throw IllegalArgumentException("Missing facilities")
        return rows.mapNotNull(::candidate).distinctBy { it.id }.sortedBy { it.routeProgressMeters }
    }

    private fun candidate(value: JsonElement): RouteFacilityCandidate? {
        val row = value as? JsonObject ?: return null
        fun string(key: String) = (row[key] as? JsonPrimitive)?.contentOrNull
        fun number(key: String) = string(key)?.toDoubleOrNull()?.takeIf { it.isFinite() }
        val osmType = string("osm_type")?.singleOrNull()?.uppercaseChar()?.takeIf { it in "NWR" } ?: return null
        val osmId = string("osm_id")?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val type = runCatching { RouteFacilityType.valueOf(string("type") ?: "") }.getOrNull() ?: return null
        val confidence = runCatching { DirectionConfidence.valueOf(string("direction_confidence") ?: "") }.getOrNull() ?: return null
        val latitude = number("lat")?.takeIf { it in -90.0..90.0 } ?: return null
        val longitude = number("lon")?.takeIf { it in -180.0..180.0 } ?: return null
        val progress = number("route_progress_m")?.takeIf { it >= 0 } ?: return null
        val corridor = number("corridor_distance_m")?.takeIf { it >= 0 } ?: return null
        return RouteFacilityCandidate(RouteFacilityId(osmType, osmId), string("name").orEmpty().ifBlank { "名称未登録" },
            type, GeoPoint(latitude, longitude), progress, corridor, confidence)
    }
}
