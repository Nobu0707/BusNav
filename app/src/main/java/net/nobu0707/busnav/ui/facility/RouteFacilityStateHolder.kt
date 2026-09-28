package net.nobu0707.busnav.ui.facility

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.nobu0707.busnav.data.facility.RouteFacilityPolyline
import net.nobu0707.busnav.domain.facility.*
import net.nobu0707.busnav.domain.prescribed.PrescribedRouteLoad
import net.nobu0707.busnav.domain.prescribed.PrescribedRouteRepository
import net.nobu0707.busnav.domain.route.RouteGeometry

enum class RouteFacilityLoadState { NO_ROUTE, LOADING, READY, ERROR }

data class RouteFacilityUiState(
    val loadState: RouteFacilityLoadState = RouteFacilityLoadState.NO_ROUTE,
    val candidates: List<RouteFacilityCandidate> = emptyList(),
    val plannedStops: List<PlannedRestStop> = emptyList(),
    val selectedId: RouteFacilityId? = null,
    val failure: RouteFacilityFailure? = null,
    val persistenceError: Boolean = false,
    val distancesReliable: Boolean = false,
    val distances: Map<RouteFacilityId, FacilityProgress> = emptyMap(),
    val nearNotice: RouteFacilityId? = null,
)

/** Owns one route query at a time. GPS changes only update local progress. */
class RouteFacilityStateHolder(
    private val provider: RouteFacilityProvider,
    private val scope: CoroutineScope,
    private val repository: PrescribedRouteRepository? = null,
    private val options: RouteFacilityQueryOptions = RouteFacilityQueryOptions(),
) {
    private val _state = MutableStateFlow(RouteFacilityUiState())
    val state = _state.asStateFlow()
    private val cache = mutableMapOf<String, RouteFacilityQueryResult>()
    private val progress = RouteFacilityProgress()
    private val persistMutex = Mutex()
    private var query: Job? = null
    private var generation = 0L
    private var fingerprint: String? = null
    private var activeRecordId: String? = null
    private var latestProgressMeters: Double? = null
    private var latestProgressReliable: Boolean = false
    var onPlannedStopsChanged: (String, List<PlannedRestStop>) -> Unit = { _, _ -> }

    fun activate(route: RouteGeometry?, plannedStops: List<PlannedRestStop> = emptyList(), recordId: String? = null) {
        if (route == null) {
            generation++
            query?.cancel()
            fingerprint = null
            activeRecordId = null
            _activeGeometry = null
            latestProgressMeters = null
            latestProgressReliable = false
            progress.reset()
            _state.value = RouteFacilityUiState()
            return
        }
        val encoded = try { RouteFacilityPolyline.encode(route) } catch (_: IllegalArgumentException) {
            _state.value = RouteFacilityUiState(loadState = RouteFacilityLoadState.ERROR, failure = RouteFacilityFailure.InvalidResponse)
            return
        }
        val next = RouteFacilityPolyline.fingerprint(encoded, options)
        if (fingerprint == next && activeRecordId == recordId) {
            if (plannedStops.isNotEmpty() && _state.value.plannedStops.isEmpty()) {
                val reconciled = if (_state.value.loadState == RouteFacilityLoadState.READY)
                    reconcilePlannedStops(plannedStops, _state.value.candidates) else plannedStops
                _state.value = _state.value.copy(plannedStops = reconciled)
                updateProgress(latestProgressMeters, latestProgressReliable)
                if (reconciled != plannedStops) persist()
            }
            return
        }
        val carriedStops = if (recordId == null && activeRecordId == null) _state.value.plannedStops else plannedStops
        generation++
        val revision = generation
        query?.cancel()
        fingerprint = next
        activeRecordId = recordId
        _activeGeometry = route
        latestProgressMeters = null
        latestProgressReliable = false
        progress.reset()
        _state.value = RouteFacilityUiState(loadState = RouteFacilityLoadState.LOADING, plannedStops = carriedStops.sortedBy { it.routeProgressMeters })
        val cached = cache[next]
        if (cached != null) { applyResult(cached, revision); return }
        query = scope.launch {
            val result = try { provider.findFacilities(route, options) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { RouteFacilityQueryResult.Failure(RouteFacilityFailure.InvalidResponse) }
            if (revision != generation || fingerprint != next) return@launch
            // Success is cached for this session. Errors do not hammer the endpoint on GPS updates.
            if (result is RouteFacilityQueryResult.Success) cache[next] = result
            applyResult(result, revision)
        }
    }

    private fun applyResult(result: RouteFacilityQueryResult, revision: Long) {
        if (revision != generation) return
        when (result) {
            is RouteFacilityQueryResult.Success -> {
                val before = _state.value.plannedStops
                val reconciled = reconcilePlannedStops(before, result.candidates)
                _state.value = _state.value.copy(loadState = RouteFacilityLoadState.READY,
                    candidates = result.candidates, plannedStops = reconciled, failure = null)
                if (reconciled != before) persist()
                updateProgress(latestProgressMeters, latestProgressReliable)
            }
            is RouteFacilityQueryResult.Failure -> _state.value = _state.value.copy(
                loadState = RouteFacilityLoadState.ERROR, failure = result.reason)
        }
    }

    fun updateProgress(meters: Double?, reliable: Boolean) {
        latestProgressMeters = meters
        latestProgressReliable = reliable
        progress.update(meters, reliable)
        val current = _state.value
        val distances = (current.candidates.map { it.id to progress.progress(it) } +
            current.plannedStops.map { it.facilityId to progress.progress(it) }).toMap()
        val stops = current.plannedStops.map { stop ->
            if (stop.status == PlannedStopStatus.NOT_ON_CURRENT_ROUTE_CANDIDATES) stop
            else if (distances[stop.facilityId]?.passed == true) stop.copy(status = PlannedStopStatus.PASSED) else stop
        }
        val near = if (reliable) stops.firstOrNull { it.status == PlannedStopStatus.UPCOMING && progress.notifyNear(it) }?.facilityId else null
        _state.value = current.copy(distances = distances, plannedStops = stops,
            distancesReliable = reliable && meters != null, nearNotice = near ?: current.nearNotice)
        if (stops != current.plannedStops) persist()
    }

    fun clearNearNotice() { _state.value = _state.value.copy(nearNotice = null) }
    fun select(id: RouteFacilityId?) { _state.value = _state.value.copy(selectedId = id) }

    fun addPlannedStop(id: RouteFacilityId, moving: Boolean): Boolean {
        if (moving) return false
        val candidate = _state.value.candidates.firstOrNull { it.id == id } ?: return false
        if (_state.value.plannedStops.any { it.facilityId == id }) return false
        val stops = (_state.value.plannedStops + PlannedRestStop(id, candidate.name, candidate.type,
            candidate.point, candidate.routeProgressMeters)).sortedBy { it.routeProgressMeters }
        _state.value = _state.value.copy(plannedStops = stops)
        persist()
        return true
    }

    fun removePlannedStop(id: RouteFacilityId, moving: Boolean): Boolean {
        if (moving || _state.value.plannedStops.none { it.facilityId == id }) return false
        val stops = _state.value.plannedStops.filterNot { it.facilityId == id }
        _state.value = _state.value.copy(plannedStops = stops)
        persist()
        return true
    }

    private fun persist() {
        val recordId = activeRecordId ?: return
        onPlannedStopsChanged(recordId, _state.value.plannedStops)
        scope.launch {
            try {
                persistMutex.withLock {
                    val found = repository?.getById(recordId) as? PrescribedRouteLoad.Found ?: return@withLock
                    // Never resurrect a deleted record or overwrite edits to a different route.
                    if (activeRecordId != recordId || found.record.route.geometry != _activeGeometry) return@withLock
                    repository.save(found.record.copy(plannedStops = _state.value.plannedStops,
                        updatedAtEpochMillis = System.currentTimeMillis()), existingOnly = true)
                    _state.value = _state.value.copy(persistenceError = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = _state.value.copy(persistenceError = true)
            }
        }
    }
    private var _activeGeometry: RouteGeometry? = null
}
