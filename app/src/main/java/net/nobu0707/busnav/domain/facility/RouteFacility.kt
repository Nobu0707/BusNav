package net.nobu0707.busnav.domain.facility

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.RouteGeometry

data class RouteFacilityId(val osmType: Char, val osmId: Long) {
    init { require(osmType in "NWR" && osmId > 0) }
}

enum class RouteFacilityType { SERVICE_AREA, PARKING_AREA }
enum class DirectionConfidence { GEOMETRIC_CANDIDATE }

data class RouteFacilityCandidate(
    val id: RouteFacilityId,
    val name: String,
    val type: RouteFacilityType,
    val point: GeoPoint,
    val routeProgressMeters: Double,
    val corridorDistanceMeters: Double,
    val directionConfidence: DirectionConfidence,
)

data class RouteFacilityQueryOptions(
    val corridorMeters: Int = 1200,
    val types: Set<RouteFacilityType> = RouteFacilityType.entries.toSet(),
    val limit: Int = 100,
) {
    init { require(corridorMeters in 100..3000 && types.isNotEmpty() && limit in 1..200) }
}

sealed interface RouteFacilityQueryResult {
    data class Success(val candidates: List<RouteFacilityCandidate>) : RouteFacilityQueryResult
    data class Failure(val reason: RouteFacilityFailure) : RouteFacilityQueryResult
}

enum class RouteFacilityFailure {
    Network, Timeout, Http400, Http413, Http429, ServerUnavailable, Parse, InvalidResponse,
}

interface RouteFacilityProvider {
    suspend fun findFacilities(route: RouteGeometry, options: RouteFacilityQueryOptions): RouteFacilityQueryResult
}

data class PlannedRestStop(
    val facilityId: RouteFacilityId,
    val name: String,
    val facilityType: RouteFacilityType,
    val point: GeoPoint,
    val routeProgressMeters: Double,
    val status: PlannedStopStatus = PlannedStopStatus.UPCOMING,
)

enum class PlannedStopStatus { UPCOMING, PASSED, NOT_ON_CURRENT_ROUTE_CANDIDATES }

data class FacilityProgress(
    val distanceAheadMeters: Double?,
    val passed: Boolean,
    val nearNotified: Boolean,
)

/** A held reliable progress is never replaced by an ambiguous matcher result. */
class RouteFacilityProgress {
    private val passed = mutableSetOf<RouteFacilityId>()
    private val notified = mutableSetOf<RouteFacilityId>()
    private var lastReliableMeters: Double? = null

    fun reset() { passed.clear(); notified.clear(); lastReliableMeters = null }
    fun update(progressMeters: Double?, reliable: Boolean) {
        if (reliable && progressMeters != null && progressMeters.isFinite() && progressMeters >= 0) {
            lastReliableMeters = progressMeters
        }
    }
    fun progress(candidate: RouteFacilityCandidate): FacilityProgress = progress(candidate.id, candidate.routeProgressMeters)
    fun progress(stop: PlannedRestStop): FacilityProgress = progress(stop.facilityId, stop.routeProgressMeters)
    private fun progress(id: RouteFacilityId, routeProgressMeters: Double): FacilityProgress {
        val ahead = lastReliableMeters?.let { routeProgressMeters - it }
        if (ahead != null && ahead < -500) passed += id
        return FacilityProgress(ahead, id in passed, id in notified)
    }
    fun notifyNear(stop: PlannedRestStop): Boolean {
        val state = progress(stop)
        if (state.passed || state.distanceAheadMeters == null || state.distanceAheadMeters !in 0.0..3000.0 || stop.facilityId in notified) return false
        notified += stop.facilityId
        return true
    }
}

fun reconcilePlannedStops(stops: List<PlannedRestStop>, candidates: List<RouteFacilityCandidate>): List<PlannedRestStop> {
    val byId = candidates.associateBy { it.id }
    return stops.map { stop ->
        byId[stop.facilityId]?.let {
            stop.copy(name = it.name, facilityType = it.type, point = it.point,
                routeProgressMeters = it.routeProgressMeters, status = PlannedStopStatus.UPCOMING)
        } ?: stop.copy(status = PlannedStopStatus.NOT_ON_CURRENT_ROUTE_CANDIDATES)
    }.sortedBy { it.routeProgressMeters }
}
