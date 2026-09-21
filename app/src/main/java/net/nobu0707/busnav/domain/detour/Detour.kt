package net.nobu0707.busnav.domain.detour

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.FreeNavigationConfig
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.routeplan.*
import net.nobu0707.busnav.domain.routing.RoutingSummary

enum class PrescribedNavigationSubmode { NORMAL, DETOUR }
enum class DetourReason { OFF_ROUTE_RECOVERY, MANUAL, ROAD_CLOSURE, OPERATIONS_INSTRUCTION, OTHER }
enum class DetourSessionState { IDLE, SELECTING_REJOIN, EDITING, CALCULATING, PREVIEW, ACTIVE, REJOIN_PENDING, COMPLETED, FAILED }
enum class RejoinTargetSource { AUTOMATIC_CANDIDATE, MANUAL }
enum class DetourDraftPointType { VIA, SHAPING }
data class RejoinTarget(val id: String, val geometryIndex: Int, val progressMeters: Double,
    val point: GeoPoint, val source: RejoinTargetSource)
data class DetourDraftPoint(val id: String, val type: DetourDraftPointType, val position: GeoPoint)
data class DetourDraft(val prescribedRouteId: String, val reason: DetourReason, val start: GeoPoint,
    val anchorProgressMeters: Double, val rejoinTarget: RejoinTarget, val points: List<DetourDraftPoint>) {
    fun toRoutePlan(id: String) = RoutePlan(id, "迂回経路", buildList {
        add(RoutePlanPoint("start", RoutePlanPointType.START, start, "現在地"))
        points.forEach { add(RoutePlanPoint(it.id, RoutePlanPointType.valueOf(it.type.name), it.position)) }
        add(RoutePlanPoint("rejoin", RoutePlanPointType.DESTINATION, rejoinTarget.point, "所定経路へ復帰"))
    })
}
data class DetourCandidate(val id: String, val draft: DetourDraft, val route: ScheduledRoute, val summary: RoutingSummary)
data class ActiveDetour(val candidate: DetourCandidate, val activatedAtElapsedMillis: Long)
data class ReliablePrescribedProgress(val progressMeters: Double, val timestampMillis: Long)

data class DetourConfig(
    val minimumForwardMeters: Double = 500.0,
    val preferredOffsetsMeters: List<Double> = listOf(1000.0, 3000.0, 5000.0),
    val maximumForwardMeters: Double = 10_000.0,
    val minimumSpacingMeters: Double = 500.0,
    val maneuverSafetyBufferMeters: Double = 200.0,
    val destinationSafetyBufferMeters: Double = 300.0,
    val maximumCandidates: Int = 3,
    val manualSelectionMaxDistanceMeters: Double = 80.0,
    val maxAnchorAgeMillis: Long = 300_000,
    val maxEditingSpeedMetersPerSecond: Float = 2f,
    val locationQuality: FreeNavigationConfig = FreeNavigationConfig(),
    val rejoin: RejoinConfig = RejoinConfig(),
) {
    init {
        require(listOf(minimumForwardMeters, maximumForwardMeters, minimumSpacingMeters,
            maneuverSafetyBufferMeters, destinationSafetyBufferMeters, manualSelectionMaxDistanceMeters)
            .all { it.isFinite() && it > 0 })
        require(preferredOffsetsMeters.all { it.isFinite() && it > minimumForwardMeters })
        require(maximumForwardMeters > minimumForwardMeters && maximumCandidates > 0 && maxAnchorAgeMillis > 0)
        require(maxEditingSpeedMetersPerSecond.isFinite() && maxEditingSpeedMetersPerSecond >= 0)
    }
    fun editingLocked(speed: Float?) = speed != null && speed.isFinite() && speed > maxEditingSpeedMetersPerSecond
}
