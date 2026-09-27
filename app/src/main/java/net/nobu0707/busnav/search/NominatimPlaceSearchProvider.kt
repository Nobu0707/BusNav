package net.nobu0707.busnav.search

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.search.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** The production search host is fixed. There is no local or public fallback. */
class NominatimPlaceSearchProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build(),
    private val parser: NominatimParser = NominatimParser(),
    private val base: HttpUrl = "https://search-busnav.nobu0707.net".toHttpUrl(),
) : PlaceSearchProvider {
    private val lookupCache = mutableMapOf<PlaceObjectId, PlaceSearchItem>()
    private val lookedUp = mutableSetOf<PlaceObjectId>()

    override suspend fun search(query: String, context: PlaceSearchContext): PlaceSearchResult {
        if (query.isBlank()) return PlaceSearchResult.Success(emptyList())
        return when (val response = fetch(searchUrl(query, context))) {
            is Fetch.Body -> runCatching { parser.parseList(response.text, context.currentLocation) }
                .fold({ PlaceSearchResult.Success(it) }, { PlaceSearchResult.Failure(PlaceSearchFailure.Parse) })
            is Fetch.Error -> PlaceSearchResult.Failure(response.failure)
        }
    }

    override suspend fun reverseGeocode(point: GeoPoint): ReverseGeocodeResult =
        when (val response = fetch(reverseUrl(point))) {
            is Fetch.Body -> runCatching { parser.parseSingle(response.text) }
                .fold({ ReverseGeocodeResult.Success(it) }, { ReverseGeocodeResult.Failure(PlaceSearchFailure.Parse) })
            is Fetch.Error -> ReverseGeocodeResult.Failure(response.failure)
        }

    override suspend fun lookup(ids: List<PlaceObjectId>): PlaceLookupResult {
        val distinct = ids.distinct()
        if (distinct.isEmpty()) return PlaceLookupResult.Success(emptyList())
        val missing = synchronized(lookupCache) { distinct.filterNot(lookedUp::contains) }
        if (missing.isNotEmpty()) {
            when (val response = fetch(lookupUrl(missing))) {
                is Fetch.Body -> {
                    val items = runCatching { parser.parseList(response.text) }.getOrNull()
                        ?: return PlaceLookupResult.Failure(PlaceSearchFailure.Parse)
                    synchronized(lookupCache) {
                        lookedUp.addAll(missing)
                        items.forEach { lookupCache[it.id] = it }
                    }
                }
                is Fetch.Error -> return PlaceLookupResult.Failure(response.failure)
            }
        }
        return PlaceLookupResult.Success(synchronized(lookupCache) { distinct.mapNotNull(lookupCache::get) })
    }

    internal fun searchUrl(query: String, context: PlaceSearchContext): HttpUrl {
        val builder = base.newBuilder().addPathSegment("search")
            .addQueryParameter("q", query).addQueryParameter("format", "jsonv2")
            .addQueryParameter("countrycodes", "jp").addQueryParameter("accept-language", "ja")
            .addQueryParameter("addressdetails", "1").addQueryParameter("namedetails", "1")
            .addQueryParameter("extratags", "1").addQueryParameter("limit", "10")
        val bounds = when (context.biasMode) {
            SearchBiasMode.NONE -> null
            SearchBiasMode.CURRENT_LOCATION -> context.currentLocation?.let(::nearbyBounds)
            SearchBiasMode.VISIBLE_MAP, SearchBiasMode.VISIBLE_MAP_BOUNDED -> context.visibleMapBounds
        }
        bounds?.let { builder.addQueryParameter("viewbox", "${it.west},${it.north},${it.east},${it.south}") }
        if (bounds != null && context.biasMode == SearchBiasMode.VISIBLE_MAP_BOUNDED)
            builder.addQueryParameter("bounded", "1")
        return builder.build()
    }

    internal fun reverseUrl(point: GeoPoint): HttpUrl = base.newBuilder().addPathSegment("reverse")
        .addQueryParameter("lat", point.latitude.toString()).addQueryParameter("lon", point.longitude.toString())
        .addQueryParameter("format", "jsonv2").addQueryParameter("accept-language", "ja")
        .addQueryParameter("addressdetails", "1").addQueryParameter("namedetails", "1")
        .addQueryParameter("extratags", "1").build()

    internal fun lookupUrl(ids: List<PlaceObjectId>): HttpUrl = base.newBuilder().addPathSegment("lookup")
        .addQueryParameter("osm_ids", ids.joinToString(",") { it.value })
        .addQueryParameter("format", "jsonv2").addQueryParameter("accept-language", "ja")
        .addQueryParameter("addressdetails", "1").addQueryParameter("namedetails", "1")
        .addQueryParameter("extratags", "1").build()

    private fun nearbyBounds(point: GeoPoint): GeoBounds {
        val latitudeSpan = 0.45
        val longitudeSpan = (latitudeSpan / kotlin.math.cos(Math.toRadians(point.latitude)).coerceAtLeast(0.2)).coerceAtMost(2.0)
        return GeoBounds((point.longitude - longitudeSpan).coerceAtLeast(-180.0),
            (point.latitude - latitudeSpan).coerceAtLeast(-90.0),
            (point.longitude + longitudeSpan).coerceAtMost(180.0),
            (point.latitude + latitudeSpan).coerceAtMost(90.0))
    }

    private sealed interface Fetch {
        data class Body(val text: String) : Fetch
        data class Error(val failure: PlaceSearchFailure) : Fetch
    }

    private suspend fun fetch(url: HttpUrl): Fetch = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        try {
            val response = client.newCall(request).await()
            response.use {
                when (it.code) {
                    429 -> Fetch.Error(PlaceSearchFailure.RateLimited)
                    in 500..599 -> Fetch.Error(PlaceSearchFailure.ServerUnavailable)
                    in 200..299 -> it.body?.string()?.let(Fetch::Body) ?: Fetch.Error(PlaceSearchFailure.Parse)
                    else -> Fetch.Error(PlaceSearchFailure.Http(it.code))
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: SocketTimeoutException) {
            Fetch.Error(PlaceSearchFailure.Timeout)
        } catch (_: IOException) {
            Fetch.Error(PlaceSearchFailure.Network)
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }
}
