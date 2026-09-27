package net.nobu0707.busnav.ui.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.search.*

data class PlaceSearchUiState(
    val query: String = "",
    val context: PlaceSearchContext = PlaceSearchContext(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    val items: List<PlaceSearchItem> = emptyList(),
    val failure: PlaceSearchFailure? = null,
    val selected: PlaceSearchItem? = null,
    val nearby: String? = null,
)

class PlaceSearchStateHolder(private val provider: PlaceSearchProvider, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(PlaceSearchUiState())
    val state = _state.asStateFlow()
    private var request: Job? = null
    private var detail: Job? = null
    private var generation = 0L

    fun open(context: PlaceSearchContext) {
        close()
        _state.value = PlaceSearchUiState(context = context)
    }

    fun close() { request?.cancel(); detail?.cancel(); generation++ }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value, items = emptyList(), searched = false, failure = null)
        schedule(immediate = false)
    }

    fun setBias(mode: SearchBiasMode) {
        _state.value = _state.value.copy(context = _state.value.context.copy(biasMode = mode))
        schedule(immediate = true)
    }

    fun submit() = schedule(immediate = true)
    fun retry() = schedule(immediate = true)

    private fun schedule(immediate: Boolean) {
        request?.cancel()
        val id = ++generation
        val snapshot = _state.value
        val query = snapshot.query.trim()
        if (query.isEmpty() || (query.length == 1 && !immediate)) {
            _state.value = snapshot.copy(searching = false, searched = false, items = emptyList(), failure = null)
            return
        }
        _state.value = snapshot.copy(searching = true, failure = null)
        request = scope.launch {
            if (!immediate) delay(450)
            val result = provider.search(query, snapshot.context)
            if (id != generation) return@launch
            _state.value = when (result) {
                is PlaceSearchResult.Success -> _state.value.copy(searching = false, searched = true,
                    items = result.items, failure = null)
                is PlaceSearchResult.Failure -> _state.value.copy(searching = false, searched = true,
                    items = emptyList(), failure = result.reason)
            }
        }
    }

    fun select(item: PlaceSearchItem) {
        detail?.cancel()
        _state.value = _state.value.copy(selected = item, nearby = null)
        detail = scope.launch {
            val result = provider.lookup(listOf(item.id))
            if (_state.value.selected?.id != item.id) return@launch
            if (result is PlaceLookupResult.Success) {
                result.items.firstOrNull()?.let { detailItem ->
                    _state.value = _state.value.copy(selected = detailItem)
                }
            }
        }
    }

    /** Point creation is synchronous; this only adds a tentative nearby label later. */
    fun enrichPoint(point: GeoPoint, onNearby: (String) -> Unit) {
        scope.launch {
            val result = provider.reverseGeocode(point)
            if (result is ReverseGeocodeResult.Success) result.item?.let { onNearby("付近: ${it.name}") }
        }
    }
}
