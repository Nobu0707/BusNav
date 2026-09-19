package net.nobu0707.busnav.domain.route

interface ScheduledRouteRepository {
    suspend fun getActiveRoute(): ScheduledRoute?
}
