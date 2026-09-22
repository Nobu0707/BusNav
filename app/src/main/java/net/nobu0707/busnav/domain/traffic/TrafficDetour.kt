package net.nobu0707.busnav.domain.traffic

import net.nobu0707.busnav.domain.detour.DetourReason
import net.nobu0707.busnav.domain.route.ScheduledRoute

data class TrafficDetourContext(val eventId: String, val sourceId: String,
    val affectedStartProgress: Double?, val affectedEndProgress: Double?, val minimumSafeRejoinProgress: Double?)
fun TrafficRouteImpact.detourContext(config: TrafficConfig = TrafficConfig()) = TrafficDetourContext(event.id, event.source.providerId,
    startProgressMeters, endProgressMeters.takeIf { endKnown },
    endProgressMeters?.plus(config.rejoinSafetyBufferMeters)?.takeIf { endKnown && level == TrafficImpactLevel.BLOCKING })
fun TrafficEvent.detourReason() = when (kind) {
    TrafficEventKind.ROAD_CLOSURE, TrafficEventKind.ENTRY_CLOSURE, TrafficEventKind.EXIT_CLOSURE, TrafficEventKind.WINTER_CLOSURE -> DetourReason.ROAD_CLOSURE
    TrafficEventKind.ROADWORK -> DetourReason.ROADWORK
    else -> DetourReason.TRAFFIC_INCIDENT
}
enum class TrafficDetourConflict { NONE, POTENTIAL_CONFLICT, BLOCKING_CONFLICT, UNKNOWN }
data class TrafficDetourValidation(val conflict: TrafficDetourConflict, val eventIds: List<String> = emptyList()) {
    val activationAllowed get() = conflict != TrafficDetourConflict.BLOCKING_CONFLICT
    val message: String? get() = when (conflict) {
        TrafficDetourConflict.BLOCKING_CONFLICT -> "この迂回経路は規制区間を通る可能性があります。経由地・通過指定を追加して再計算してください。"
        TrafficDetourConflict.POTENTIAL_CONFLICT -> "迂回経路付近に規制情報があります。道路名・方向と現地の状況を確認してください。"
        TrafficDetourConflict.UNKNOWN -> "交通情報の接続・鮮度を確認できません。規制を回避できるとは限りません。"
        TrafficDetourConflict.NONE -> null
    }
}
class TrafficDetourValidator(private val config: TrafficConfig = TrafficConfig()) {
    fun validate(route: ScheduledRoute, snapshot: TrafficSnapshot, now: Long): TrafficDetourValidation {
        val impacts = TrafficRouteImpactAnalyzer(route, config).analyze(snapshot, now, null)
        val blocking = impacts.filter { it.level == TrafficImpactLevel.BLOCKING && it.matchingConfidence == TrafficMatchConfidence.HIGH }
        if (blocking.isNotEmpty()) return TrafficDetourValidation(TrafficDetourConflict.BLOCKING_CONFLICT, blocking.map { it.event.id })
        val possible = impacts.filter { it.position != TrafficImpactPosition.OFF_ROUTE &&
            TrafficRouteImpactAnalyzer.level(it.event.kind) in listOf(TrafficImpactLevel.BLOCKING, TrafficImpactLevel.RESTRICTION) }
        if (possible.isNotEmpty()) return TrafficDetourValidation(TrafficDetourConflict.POTENTIAL_CONFLICT, possible.map { it.event.id })
        return TrafficDetourValidation(if (snapshot.effectiveStatus(now, config) == TrafficProviderStatus.AVAILABLE)
            TrafficDetourConflict.NONE else TrafficDetourConflict.UNKNOWN)
    }
}
