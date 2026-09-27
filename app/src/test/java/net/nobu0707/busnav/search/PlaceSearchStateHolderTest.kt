package net.nobu0707.busnav.search

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.search.*
import net.nobu0707.busnav.ui.search.PlaceSearchStateHolder
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaceSearchStateHolderTest {
    @Test fun typingDebouncesAndOneCharacterWaitsForIme() = runTest {
        val provider = FakeProvider()
        val holder = PlaceSearchStateHolder(provider, backgroundScope)
        holder.setQuery("東")
        advanceTimeBy(1000)
        assertEquals(0, provider.queries.size)
        holder.setQuery("東京")
        advanceTimeBy(200)
        holder.setQuery("東京駅")
        advanceTimeBy(449)
        runCurrent()
        assertEquals(0, provider.queries.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("東京駅"), provider.queries)
        holder.setQuery("駅")
        holder.submit()
        runCurrent()
        assertEquals(listOf("東京駅", "駅"), provider.queries)
    }

    @Test fun lateCancelledResponseCannotReplaceNewerResults() = runTest {
        val provider = FakeProvider()
        val holder = PlaceSearchStateHolder(provider, backgroundScope)
        holder.setQuery("東京")
        holder.submit()
        runCurrent()
        holder.setQuery("大阪")
        holder.submit()
        runCurrent()
        provider.finish("大阪")
        runCurrent()
        provider.finish("東京")
        runCurrent()
        assertEquals("大阪", holder.state.value.items.single().name)
    }

    @Test fun lookupIsOnlyRequestedForSelectedResult() = runTest {
        val provider = FakeProvider()
        val holder = PlaceSearchStateHolder(provider, backgroundScope)
        holder.setQuery("東京")
        holder.submit()
        runCurrent()
        provider.finish("東京")
        runCurrent()
        assertEquals(0, provider.lookups)
        holder.select(holder.state.value.items.single())
        runCurrent()
        assertEquals(1, provider.lookups)
    }

    private class FakeProvider : PlaceSearchProvider {
        val queries = mutableListOf<String>()
        val pending = mutableMapOf<String, CompletableDeferred<PlaceSearchResult>>()
        var lookups = 0
        override suspend fun search(query: String, context: PlaceSearchContext): PlaceSearchResult {
            queries += query
            val response = CompletableDeferred<PlaceSearchResult>()
            pending[query] = response
            return withContext(NonCancellable) { response.await() }
        }
        fun finish(query: String) {
            val item = PlaceSearchItem(PlaceObjectId("N1"), query, query, GeoPoint(35.0, 139.0),
                PlaceCategory.PLACE, null, null, null, null, emptyMap(), emptyMap(), null)
            pending.getValue(query).complete(PlaceSearchResult.Success(listOf(item)))
        }
        override suspend fun reverseGeocode(point: GeoPoint) = ReverseGeocodeResult.Success(null)
        override suspend fun lookup(ids: List<PlaceObjectId>): PlaceLookupResult {
            lookups++
            return PlaceLookupResult.Success(emptyList())
        }
    }
}
