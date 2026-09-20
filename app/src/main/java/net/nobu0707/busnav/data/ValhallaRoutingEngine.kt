package net.nobu0707.busnav.data.routing.valhalla

import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

data class RoutingDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val computation: CoroutineDispatcher = Dispatchers.Default,
)

private data class ResponsePayload(
    val body: String,
    val contentType: String?,
    val reportedContentLength: Long?,
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
    private val dispatchers: RoutingDispatchers = RoutingDispatchers(),
    private val baseUrlProvider: suspend () -> String = { config.baseUrl },
) : RoutingEngine {
    private val httpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
        .build()
    private val responseParser = ValhallaRouteResponseParser(json, routeIdFactory, diagnostics)

    override suspend fun calculateRoute(request: RoutingRequest): RoutingResult {
        if (request.points.size < 2) {
            diagnostics.error("request.invalid") { "pointCount=${request.points.size}" }
            return RoutingResult.Failure(RoutingFailure.INVALID_REQUEST)
        }
        val endpoint = routeEndpoint()
        if (endpoint == null) {
            diagnostics.error("request.configuration") { "baseUrl is blank or invalid" }
            return RoutingResult.Failure(RoutingFailure.CONFIGURATION)
        }
        diagnostics.debug("request.start") {
            val pointIds = request.points.joinToString(",") { it.id }
            val pointTypes = request.points.joinToString(",") { it.type.name }
            val duplicateIds = request.points.size - request.points.map { it.id }.distinct().size
            "routePlanId=${request.routePlanId} pointCount=${request.points.size} " +
                "pointIds=$pointIds pointTypes=$pointTypes duplicateIds=$duplicateIds " +
                "startCount=${request.points.count { it.type == RoutePlanPointType.START }} " +
                "destinationCount=${request.points.count { it.type == RoutePlanPointType.DESTINATION }} " +
                "endpoint=${endpoint.host}:${endpoint.port}"
        }

        val httpRequest = try {
            val requestBody = withContext(dispatchers.computation) {
                json.encodeToString(request.toValhallaRequest())
            }
            Request.Builder()
                .url(endpoint)
                .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            diagnostics.error("request.encode.failed", error) { error.describe() }
            return RoutingResult.Failure(RoutingFailure.INVALID_RESPONSE)
        }

        val response = try {
            httpClient.newCall(httpRequest).await()
        } catch (error: CancellationException) {
            diagnostics.debug("http.request.cancelled") { "routePlanId=${request.routePlanId}" }
            throw error
        } catch (error: SocketTimeoutException) {
            diagnostics.error("http.request.failed", error) {
                "classification=TIMEOUT ${error.describe()}"
            }
            return RoutingResult.Failure(RoutingFailure.TIMEOUT)
        } catch (error: IOException) {
            diagnostics.error("http.request.failed", error) {
                "classification=NETWORK ${error.describe()}"
            }
            return RoutingResult.Failure(RoutingFailure.NETWORK)
        } catch (error: Exception) {
            diagnostics.error("unexpected", error) { "stage=HTTP_REQUEST ${error.describe()}" }
            return RoutingResult.Failure(RoutingFailure.INVALID_RESPONSE)
        }

        return response.use {
            val payload = try {
                withContext(dispatchers.io) {
                    val responseBody = response.body
                    val contentType = responseBody?.contentType()?.toString()
                    val reportedContentLength = responseBody?.contentLength()
                    ResponsePayload(
                        body = responseBody?.string().orEmpty(),
                        contentType = contentType,
                        reportedContentLength = reportedContentLength,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: SocketTimeoutException) {
                diagnostics.error("response.read.failed", error) {
                    "classification=TIMEOUT status=${response.code} ${error.describe()}"
                }
                return@use RoutingResult.Failure(RoutingFailure.TIMEOUT)
            } catch (error: IOException) {
                diagnostics.error("response.read.failed", error) {
                    "classification=NETWORK status=${response.code} ${error.describe()}"
                }
                return@use RoutingResult.Failure(RoutingFailure.NETWORK)
            } catch (error: Exception) {
                diagnostics.error("response.read.failed", error) {
                    "classification=INVALID_RESPONSE status=${response.code} ${error.describe()}"
                }
                return@use RoutingResult.Failure(RoutingFailure.INVALID_RESPONSE)
            }

            try {
                withContext(dispatchers.computation) {
                    diagnostics.debug("response.received") {
                        val bodyBytes = payload.body.toByteArray(StandardCharsets.UTF_8)
                        "status=${response.code} bodyLength=${payload.body.length} " +
                            "bodyByteLength=${bodyBytes.size} contentType=${payload.contentType} " +
                            "contentLength=${payload.reportedContentLength} " +
                            "headerContentLength=${response.header("Content-Length")} " +
                            "bodySha256=${bodyBytes.sha256()}"
                    }
                    if (!response.isSuccessful) {
                        RoutingResult.Failure(classifyHttpFailure(response.code, payload.body))
                    } else {
                        responseParser.parse(request, payload.body)
                            .also { result ->
                                diagnostics.debug("route.success") {
                                    "geometryPointCount=${result.route.geometry.points.size} " +
                                        "routePointCount=${result.route.points.size}"
                                }
                            }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: ValhallaResponseException) {
                val event = if (error.stage == ValhallaResponseStage.ROUTE_CONSTRUCTION) {
                    "route.construction.failed"
                } else {
                    "response.parse.failed"
                }
                diagnostics.error(event, error) {
                    "stage=${error.stage} ${error.describe()}"
                }
                RoutingResult.Failure(RoutingFailure.INVALID_RESPONSE)
            } catch (error: Exception) {
                diagnostics.error("response.parse.failed", error) {
                    "stage=UNEXPECTED ${error.describe()}"
                }
                RoutingResult.Failure(RoutingFailure.INVALID_RESPONSE)
            }
        }
    }

    private suspend fun routeEndpoint() = baseUrlProvider()
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

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun Throwable.describe(): String =
        "exception=${javaClass.name} message=${message ?: "<none>"}"

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val NO_ROUTE_ERROR_CODES = setOf(170, 171, 442)
    }
}
