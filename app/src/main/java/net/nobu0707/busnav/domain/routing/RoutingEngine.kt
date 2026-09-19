package net.nobu0707.busnav.domain.routing

import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.routeplan.RoutingRequest

fun interface RoutingEngine {
    suspend fun calculateRoute(request: RoutingRequest): RoutingResult
}

data class RoutingSummary(
    val distanceMeters: Double,
    val durationSeconds: Double,
) {
    init {
        require(distanceMeters >= 0.0) { "Route distance must not be negative" }
        require(durationSeconds >= 0.0) { "Route duration must not be negative" }
    }
}

sealed interface RoutingResult {
    data class Success(
        val route: ScheduledRoute,
        val summary: RoutingSummary,
    ) : RoutingResult

    data class Failure(val reason: RoutingFailure) : RoutingResult
}

enum class RoutingFailure {
    INVALID_REQUEST,
    SERVICE_UNAVAILABLE,
    NETWORK,
    TIMEOUT,
    NO_ROUTE,
    RATE_LIMITED,
    SERVER_ERROR,
    INVALID_RESPONSE,
    CONFIGURATION,
}
