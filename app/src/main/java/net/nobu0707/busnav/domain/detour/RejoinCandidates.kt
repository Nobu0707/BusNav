package net.nobu0707.busnav.domain.detour

import kotlin.math.abs
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.route.ScheduledRoute

data class RejoinSelection(val target: RejoinTarget? = null, val error: String? = null)

/** Route-bound, local geometry only. No engine and no automatic ranking. */
class RejoinCandidateGenerator(val route: ScheduledRoute, val anchor: Double,
    val config: DetourConfig = DetourConfig(), val distances: RouteDistanceIndex = RouteDistanceIndex(route.geometry)) {
    private val unsafe = route.guidance?.maneuvers.orEmpty().filter {
        it.type.name.startsWith("EXIT") || it.type.name.startsWith("RAMP") || it.type.name.startsWith("KEEP") ||
            it.type.name.startsWith("U_TURN") || it.type == ManeuverType.MERGE || it.type == ManeuverType.DESTINATION
    }.map { distances.distanceAtGeometryIndex(it.beginGeometryIndex) } +
        HighwayDecisionExtractor.extract(route.guidance, distances).map { it.distanceAlongRouteMeters }

    init { require(anchor.isFinite() && anchor in 0.0..distances.totalMeters) }

    fun problem(progress: Double): String? = when {
        !progress.isFinite() || progress <= anchor + config.minimumForwardMeters -> "現在の所定経路位置より十分先の地点を選択してください"
        progress > anchor + config.maximumForwardMeters -> "復帰地点が遠すぎます"
        progress >= distances.totalMeters - config.destinationSafetyBufferMeters -> "目的地直前には復帰地点を設定できません"
        unsafe.any { abs(it - progress) <= config.maneuverSafetyBufferMeters } -> "分岐・合流付近を避けて復帰地点を選択してください"
        else -> null
    }

    fun generate(): List<RejoinTarget> {
        val result = mutableListOf<RejoinTarget>()
        for (offset in config.preferredOffsetsMeters.sorted()) {
            val progress = anchor + offset
            if (problem(progress) != null || result.any { abs(it.progressMeters - progress) < config.minimumSpacingMeters }) continue
            result += atProgress(progress, RejoinTargetSource.AUTOMATIC_CANDIDATE)
            if (result.size == config.maximumCandidates) break
        }
        return result
    }

    fun manual(point: GeoPoint): RejoinSelection {
        val projected = RouteProjector.project(point, route.geometry, distances)
        if (projected.distanceFromRouteMeters > config.manualSelectionMaxDistanceMeters)
            return RejoinSelection(error = "所定経路に近い地点を選択してください")
        problem(projected.distanceAlongRouteMeters)?.let { return RejoinSelection(error = it) }
        // A crossing can represent several route occurrences. Do not silently choose one.
        if ((0 until route.geometry.points.lastIndex).any {
            val other = RouteProjector.projectSegment(point, distances, it)
            abs(other.distanceFromRouteMeters - projected.distanceFromRouteMeters) < 5.0 &&
                abs(other.distanceAlongRouteMeters - projected.distanceAlongRouteMeters) > config.minimumSpacingMeters
        }) return RejoinSelection(error = "経路が重なる地点です。別の地点を選択してください")
        return RejoinSelection(RejoinTarget("manual", projected.segmentIndex, projected.distanceAlongRouteMeters,
            projected.projectedPoint, RejoinTargetSource.MANUAL))
    }

    private fun atProgress(progress: Double, source: RejoinTargetSource): RejoinTarget {
        val i = (0 until route.geometry.points.lastIndex).first { distances.distanceAtGeometryIndex(it + 1) >= progress }
        val length = distances.distanceBetweenIndices(i, i + 1)
        val f = if (length == 0.0) 0.0 else (progress - distances.distanceAtGeometryIndex(i)) / length
        val a = route.geometry.points[i]; val b = route.geometry.points[i + 1]
        val point = GeoPoint(a.latitude + f * (b.latitude - a.latitude),
            longitudeDelta(a.longitude + f * longitudeDelta(b.longitude - a.longitude)))
        return RejoinTarget("candidate-${progress.toLong()}", i, progress, point, source)
    }
}
