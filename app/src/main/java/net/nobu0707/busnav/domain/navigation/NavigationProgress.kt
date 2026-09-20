package net.nobu0707.busnav.domain.navigation

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.ScheduledRoute

enum class ProjectionReliability { RELIABLE, UNCERTAIN, UNRELIABLE }

data class NavigationProgress(
    val projection: RouteProjection,
    val distanceAlongRouteMeters: Double,
    val remainingRouteMeters: Double,
    val nextManeuver: RouteManeuver?,
    val nextNextManeuver: RouteManeuver?,
    val distanceToNextManeuverMeters: Double?,
    val reliability: ProjectionReliability,
) {
    val isProjectionReliable: Boolean get() = reliability == ProjectionReliability.RELIABLE
}

data class NavigationProgressConfig(
    val passedManeuverToleranceMeters: Double = 15.0,
    val reliableDistanceMeters: Double = 30.0,
    val uncertainDistanceMeters: Double = 80.0,
    val backwardToleranceMeters: Double = 15.0,
) {
    init {
        require(passedManeuverToleranceMeters.isFinite() && passedManeuverToleranceMeters >= 0)
        require(reliableDistanceMeters.isFinite() && reliableDistanceMeters >= 0)
        require(uncertainDistanceMeters.isFinite() && uncertainDistanceMeters >= reliableDistanceMeters)
        require(backwardToleranceMeters.isFinite() && backwardToleranceMeters >= 0)
    }
}

/** Route-bound calculator caches geometry and maneuver distances; has no routing engine dependency. */
class NavigationProgressCalculator(
    val route: ScheduledRoute,
    val config: NavigationProgressConfig = NavigationProgressConfig(),
) {
    val distanceIndex = RouteDistanceIndex(route.geometry)
    val highwayCalculator = HighwayGuidanceCalculator(HighwayDecisionExtractor.extract(route.guidance, distanceIndex))
    private val maneuvers = route.guidance?.maneuvers.orEmpty()
    private val begins = maneuvers.map { distanceIndex.distanceAtGeometryIndex(it.beginGeometryIndex) }

    fun calculate(position: GeoPoint, hintSegmentIndex: Int? = null): NavigationProgress =
        calculate(RouteProjector.project(position, route.geometry, distanceIndex, hintSegmentIndex))

    fun calculate(projection: RouteProjection, progressMeters: Double = projection.distanceAlongRouteMeters): NavigationProgress {
        require(progressMeters.isFinite() && progressMeters in 0.0..distanceIndex.totalMeters)
        val nextIndex = maneuvers.indices.firstOrNull {
            begins[it] + config.passedManeuverToleranceMeters >= progressMeters
        }
        val next = nextIndex?.let(maneuvers::get)
        val reliability = when {
            projection.distanceFromRouteMeters <= config.reliableDistanceMeters -> ProjectionReliability.RELIABLE
            projection.distanceFromRouteMeters <= config.uncertainDistanceMeters -> ProjectionReliability.UNCERTAIN
            else -> ProjectionReliability.UNRELIABLE
        }
        return NavigationProgress(projection, progressMeters, (distanceIndex.totalMeters - progressMeters).coerceAtLeast(0.0),
            next, nextIndex?.let { maneuvers.getOrNull(it + 1) },
            nextIndex?.let { (begins[it] - progressMeters).coerceAtLeast(0.0) }, reliability)
    }
}

/** Small reliable backward jitter is held. Larger reverse movement is allowed; no rerouting. */
class NavigationProgressTracker(val calculator: NavigationProgressCalculator) {
    private var previous: NavigationProgress? = null
    fun update(position: GeoPoint): NavigationProgress {
        val raw = calculator.calculate(position, previous?.projection?.segmentIndex)
        val last = previous
        val progress = if (raw.isProjectionReliable && last != null &&
            last.distanceAlongRouteMeters - raw.distanceAlongRouteMeters in 0.0..calculator.config.backwardToleranceMeters) {
            calculator.calculate(raw.projection, last.distanceAlongRouteMeters)
        } else raw
        if (progress.isProjectionReliable) previous = progress
        return progress
    }
}
