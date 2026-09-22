package net.nobu0707.busnav.domain.traffic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.nobu0707.busnav.domain.model.GeoPoint

enum class TrafficEventKind { ROAD_CLOSURE, ENTRY_CLOSURE, EXIT_CLOSURE, LANE_RESTRICTION, SPEED_RESTRICTION, ACCIDENT, ROADWORK, OBSTACLE, CONGESTION, WINTER_CLOSURE, EVENT_RESTRICTION, WEATHER_HAZARD, DISASTER, UNKNOWN }
enum class TrafficSeverity { INFO, CAUTION, WARNING, CRITICAL }
/** FORWARD follows geometry vertex order (or bearingDegrees for a point); REVERSE opposes it. */
enum class TrafficDirection { FORWARD, REVERSE, BOTH, UNKNOWN }
enum class TrafficProviderStatus { AVAILABLE, STALE, UNAVAILABLE, ERROR, NOT_CONFIGURED }
enum class TrafficValidity { ACTIVE, FUTURE, EXPIRED }
data class TrafficSourceInfo(val providerId: String, val displayName: String, val isLive: Boolean, val attribution: String = displayName)
sealed interface TrafficGeometry {
    data class Point(val point: GeoPoint) : TrafficGeometry
    data class Polyline(val points: List<GeoPoint>) : TrafficGeometry { init { require(points.size >= 2) } }
    data class Polygon(val points: List<GeoPoint>) : TrafficGeometry { init { require(points.distinct().size >= 3) } }
}
data class TrafficEvent(
    val id: String,
    val kind: TrafficEventKind,
    val severity: TrafficSeverity,
    val title: String,
    val geometry: TrafficGeometry,
    val source: TrafficSourceInfo,
    val description: String? = null,
    val roadName: String? = null,
    val roadReference: String? = null,
    val direction: TrafficDirection = TrafficDirection.UNKNOWN,
    val bearingDegrees: Double? = null,
    val validFromEpochMillis: Long? = null,
    val validUntilEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long? = null,
    val sourceEventId: String? = null,
    val sourceRoadLinkId: String? = null,
) {
    init {
        require(id.isNotBlank() && title.isNotBlank())
        require(bearingDegrees == null || bearingDegrees.isFinite() && bearingDegrees in 0.0..360.0)
        require(validFromEpochMillis == null || validUntilEpochMillis == null || validUntilEpochMillis > validFromEpochMillis)
    }
    fun validity(now: Long) = when {
        validUntilEpochMillis != null && now >= validUntilEpochMillis -> TrafficValidity.EXPIRED
        validFromEpochMillis != null && now < validFromEpochMillis -> TrafficValidity.FUTURE
        else -> TrafficValidity.ACTIVE
    }
}
data class TrafficConfig(
    val staleAfterMillis: Long = 300_000,
    val corridorMeters: Double = 35.0,
    val strongGeometryMeters: Double = 8.0,
    val minimumOverlapMeters: Double = 30.0,
    val progressToleranceMeters: Double = 30.0,
    val rejoinSafetyBufferMeters: Double = 250.0,
) {
    init {
        require(staleAfterMillis > 0)
        require(listOf(corridorMeters, strongGeometryMeters, minimumOverlapMeters, progressToleranceMeters, rejoinSafetyBufferMeters).all { it.isFinite() && it > 0 })
        require(strongGeometryMeters <= corridorMeters)
    }
}
data class TrafficSnapshot(
    val source: TrafficSourceInfo,
    val status: TrafficProviderStatus,
    val events: List<TrafficEvent> = emptyList(),
    val receivedAtEpochMillis: Long? = null,
    val dataUpdatedAtEpochMillis: Long? = null,
) {
    fun ageMillis(now: Long): Long? = (dataUpdatedAtEpochMillis ?: receivedAtEpochMillis)?.let { (now - it).coerceAtLeast(0) }
    fun effectiveStatus(now: Long, config: TrafficConfig = TrafficConfig()): TrafficProviderStatus =
        if (status == TrafficProviderStatus.AVAILABLE && (ageMillis(now) == null || ageMillis(now)!! >= config.staleAfterMillis)) TrafficProviderStatus.STALE else status
    fun updatedAt(event: TrafficEvent): Long? = event.updatedAtEpochMillis ?: dataUpdatedAtEpochMillis ?: receivedAtEpochMillis
}
data class TrafficRefreshResult(val status: TrafficProviderStatus)
/** Licensed adapters normalize into this contract. Credentials and native payloads stay outside domain/UI. */
interface TrafficInformationProvider {
    val source: TrafficSourceInfo
    fun observeTraffic(): Flow<TrafficSnapshot>
    suspend fun refresh(): TrafficRefreshResult
}
class NoOpTrafficInformationProvider : TrafficInformationProvider {
    override val source = TrafficSourceInfo("none", "交通情報サービス未接続", false)
    override fun observeTraffic() = flowOf(TrafficSnapshot(source, TrafficProviderStatus.NOT_CONFIGURED))
    override suspend fun refresh() = TrafficRefreshResult(TrafficProviderStatus.NOT_CONFIGURED)
}
/** A failed fetch is not a clearance. Successful snapshots replace the entire set, keyed by source + ID. */
fun TrafficSnapshot.reconcile(previous: TrafficSnapshot?): TrafficSnapshot {
    if (status in listOf(TrafficProviderStatus.ERROR, TrafficProviderStatus.UNAVAILABLE, TrafficProviderStatus.STALE) &&
        events.isEmpty() && previous?.source?.providerId == source.providerId) return copy(
        events = previous.events, receivedAtEpochMillis = previous.receivedAtEpochMillis,
        dataUpdatedAtEpochMillis = previous.dataUpdatedAtEpochMillis)
    return copy(events = events.associateBy { it.source.providerId to it.id }.values.toList())
}
