package net.nobu0707.busnav.search

import kotlinx.coroutines.runBlocking
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.search.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class NominatimPlaceSearchProviderTest {
    @Test fun forwardEncodingParametersAndBias() {
        val provider = NominatimPlaceSearchProvider()
        val context = PlaceSearchContext(GeoPoint(35.0, 139.0), GeoBounds(138.0, 34.0, 140.0, 36.0),
            SearchBiasMode.VISIBLE_MAP)
        val url = provider.searchUrl("東京駅 & #", context)
        assertEquals("search-busnav.nobu0707.net", url.host)
        assertEquals("東京駅 & #", url.queryParameter("q"))
        assertEquals("jsonv2", url.queryParameter("format"))
        assertEquals("jp", url.queryParameter("countrycodes"))
        assertEquals("ja", url.queryParameter("accept-language"))
        assertEquals("1", url.queryParameter("addressdetails"))
        assertEquals("1", url.queryParameter("namedetails"))
        assertEquals("1", url.queryParameter("extratags"))
        assertEquals("10", url.queryParameter("limit"))
        assertEquals("138.0,36.0,140.0,34.0", url.queryParameter("viewbox"))
        assertNull(url.queryParameter("bounded"))
        assertEquals("1", provider.searchUrl("駅", context.copy(biasMode = SearchBiasMode.VISIBLE_MAP_BOUNDED))
            .queryParameter("bounded"))
        assertNotNull(provider.searchUrl("駅", context.copy(biasMode = SearchBiasMode.CURRENT_LOCATION))
            .queryParameter("viewbox"))
        assertNull(provider.searchUrl("駅", context.copy(biasMode = SearchBiasMode.NONE))
            .queryParameter("viewbox"))
    }

    @Test fun reverseAndLookupUrls() {
        val provider = NominatimPlaceSearchProvider()
        assertEquals("35.0", provider.reverseUrl(GeoPoint(35.0, 139.0)).queryParameter("lat"))
        assertEquals("N123,W456,R789", provider.lookupUrl(listOf(PlaceObjectId("N123"),
            PlaceObjectId("W456"), PlaceObjectId("R789"))).queryParameter("osm_ids"))
    }

    @Test fun rateLimitAndSelectedOnlyLookupCache() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val provider = NominatimPlaceSearchProvider(base = server.url("/"))
            server.enqueue(MockResponse().setResponseCode(429))
            assertEquals(PlaceSearchResult.Failure(PlaceSearchFailure.RateLimited),
                provider.search("東京駅", PlaceSearchContext()))
            server.enqueue(MockResponse().setBody("""[{"osm_type":"node","osm_id":123,"lat":"35","lon":"139","name":"東京駅","category":"railway","type":"station"}]"""))
            val id = PlaceObjectId("N123")
            assertEquals(1, (provider.lookup(listOf(id)) as PlaceLookupResult.Success).items.size)
            assertEquals(1, (provider.lookup(listOf(id)) as PlaceLookupResult.Success).items.size)
            assertEquals(2, server.requestCount)
            server.takeRequest()
            assertEquals("N123", server.takeRequest().requestUrl?.queryParameter("osm_ids"))
        }
    }

    @Test fun reverseParseAndTimeoutAreSeparateOutcomes() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = OkHttpClient.Builder().readTimeout(150, TimeUnit.MILLISECONDS).build()
            val provider = NominatimPlaceSearchProvider(client = client, base = server.url("/"))
            server.enqueue(MockResponse().setBody("""{"osm_type":"node","osm_id":3,"lat":"35","lon":"139","name":"近くの店","category":"shop","type":"convenience"}"""))
            assertEquals("近くの店", (provider.reverseGeocode(GeoPoint(35.0, 139.0)) as ReverseGeocodeResult.Success).item?.name)
            server.enqueue(MockResponse().setBody("not-json"))
            assertEquals(PlaceSearchResult.Failure(PlaceSearchFailure.Parse), provider.search("駅", PlaceSearchContext()))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            assertEquals(PlaceSearchResult.Failure(PlaceSearchFailure.Timeout), provider.search("駅", PlaceSearchContext()))
        }
    }
}
