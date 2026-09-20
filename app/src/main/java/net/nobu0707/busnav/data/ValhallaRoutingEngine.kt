package net.nobu0707.busnav.data.routing.valhalla

import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.domain.routeplan.RoutingRequest
import net.nobu0707.busnav.domain.routing.RoutingEngine
import net.nobu0707.busnav.domain.routing.RoutingFailure
import net.nobu0707.busnav.domain.routing.RoutingResult
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
    routeIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val diagnostics: RoutingDiagnostics = NoOpRoutingDiagnostics,
) : RoutingEngine {
    private val httpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
        .build()
    private val responseParser = ValhallaRouteResponseParser(json, routeIdFactory, diagnostics)

    override suspend fun calculateRoute(request: RoutingRequest): RoutingResult {
        if (request.points.size < 2) {
            diagnostics.error("request.invalid", "pointCount=${request.points.size}")
            return RoutingResult.Failure(RoutingFailure.INVALID_REQUEST)
        }
        val endpoint = routeEndpoint()
        if (endpoint == null) {
            diagnostics.error("request.configuration", "baseUrl is blank or invalid")
            return RoutingResult.Failure(RoutingFailure.CONFIGURATION)
        }
        val pointIds = request.points.joinToString(",") { it.id }
        val pointTypes = request.points.joinToString(",") { it.type.name }
        val duplicateIds = request.points.size - request.points.map { it.id }.distinct().size
        diagnostics.debug(
            "request.start",
            "routePlanId=${request.routePlanId} pointCount=${request.points.size} " +
                "pointIds=$pointIds pointTypes=$pointTypes duplicateIds=$duplicateIds " +
                "startCount=${request.points.count { it.type == RoutePlanPointType.START }} " +
                "destinationCount=${request.points.count { it.type == RoutePlanPointType.DESTINATION }} " +
                "endpoint=${endpoint.host}:${endpoint.port}",
        )

        return try {
            val requestBody = json.encodeToString(request.toValhallaRequest())
            val httpRequest = Request.Builder()
                .url(endpoint)
                .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(httpRequest).await().use { response ->
                val responseBodySource = response.body
                val contentType = responseBodySource?.contentType()
                val reportedContentLength = responseBodySource?.contentLength()
                val responseBody = responseBodySource?.string().orEmpty()
                diagnostics.debug(
                    "response.received",
                    "status=${response.code} bodyLength=${responseBody.length} " +
                        "bodyByteLength=${responseBody.toByteArray(StandardCharsets.UTF_8).size} " +
                        "contentType=$contentType contentLength=$reportedContentLength " +
                        "headerContentLength=${response.header("Content-Length")} " +
                        "bodySha256=${responseBody.sha256()}",
                )
                if (!response.isSuccessful) {
                    RoutingResult.Failure(classifyHttpFailure(response.code, responseBody))
                } else {
                    responseParser.parse(request, responseBody)
                }
            }
        } catch (error: CancellationException) {
            diagnostics.debug("request.cancelled", "routePlanId=${request.routePlanId}")
            throw error
        } catch (error: SocketTimeoutException) {
            diagnostics.error("request.timeout", error.describe(), error)
            RoutingResult.Failure(RoutingFailure.TIMEOUT)
        } catch (error: IOException) {
            diagnostics.error("request.network", error.describe(), error)
            RoutingResult.Failure(RoutingFailure.NETWORK)
        } catch (error: ValhallaResponseException) {
            diagnostics.error(
                "response.invalid",
                "stage=${error.stage} ${error.describe()}",
                error,
            )
            RoutingResult.Failure(RoutingFailure.INVALID_RESPONSE)
        } catch (error: Exception) {
            diagnostics.error("response.unexpected", error.describe(), error)
            RoutingResult.Failure(RoutingFailure.INVALID_RESPONSE)
        }
    }

    private fun routeEndpoint() = config.baseUrl
        .takeIf(String::isNotBlank)
        ?.trimEnd('/')
        ?.plus("/route")
        ?.toHttpUrlOrNull()

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
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
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

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun Throwable.describe(): String =
        "exception=${javaClass.name} message=${message ?: "<none>"}"

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val NO_ROUTE_ERROR_CODES = setOf(170, 171, 442)
    }
}
