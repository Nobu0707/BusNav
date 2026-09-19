package net.nobu0707.busnav.ui.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.CancellationException
import net.nobu0707.busnav.data.route.createDevelopmentSampleRoute
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.route.ScheduledRouteRepository
import net.nobu0707.busnav.location.LocationProvider
import net.nobu0707.busnav.location.LocationUpdate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationStateHolderTest {
    private val sampleRoute = createDevelopmentSampleRoute()
    private val holder = createHolder(route = sampleRoute)

    private fun createHolder(
        route: ScheduledRoute? = null,
        error: Throwable? = null,
    ) = NavigationStateHolder(
        locationProvider = object : LocationProvider {
            override fun updates(): Flow<LocationUpdate> = emptyFlow()
            override fun isLocationEnabled(): Boolean = true
        },
        routeRepository = object : ScheduledRouteRepository {
            override suspend fun getActiveRoute(): ScheduledRoute? {
                error?.let { throw it }
                return route
            }
        },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    @Test
    fun `active route is loaded from repository`() {
        assertEquals(sampleRoute, holder.uiState.value.activeRoute)
        assertFalse(holder.uiState.value.isRouteLoading)
    }

    @Test
    fun `null route is a valid loaded state`() {
        val nullRouteHolder = createHolder(route = null)

        assertNull(nullRouteHolder.uiState.value.activeRoute)
        assertFalse(nullRouteHolder.uiState.value.isRouteLoading)
        assertNull(nullRouteHolder.uiState.value.routeError)
    }

    @Test
    fun `repository failure is isolated in route error`() {
        val failedHolder = createHolder(error = IllegalStateException("route unavailable"))

        assertNull(failedHolder.uiState.value.activeRoute)
        assertEquals("route unavailable", failedHolder.uiState.value.routeError)
        assertFalse(failedHolder.uiState.value.isRouteLoading)
    }

    @Test
    fun `repository cancellation is not converted to route error`() {
        val cancelledHolder = createHolder(error = CancellationException("cancelled"))

        assertNull(cancelledHolder.uiState.value.routeError)
        assertTrue(cancelledHolder.uiState.value.isRouteLoading)
    }

    @Test
    fun `manual gesture pauses following`() {
        holder.onManualMapGesture()

        assertFalse(holder.uiState.value.isFollowingLocation)
    }

    @Test
    fun `current location request resumes following and advances request id`() {
        holder.onManualMapGesture()
        val previousId = holder.uiState.value.recenterRequestId

        holder.onCurrentLocationRequested()

        assertTrue(holder.uiState.value.isFollowingLocation)
        assertTrue(holder.uiState.value.recenterRequestId > previousId)
    }

    @Test
    fun `route overview pauses following and advances request id`() {
        val previousId = holder.uiState.value.routeOverviewRequestId

        holder.onRouteOverviewRequested()

        assertFalse(holder.uiState.value.isFollowingLocation)
        assertTrue(holder.uiState.value.routeOverviewRequestId > previousId)
    }

    @Test
    fun `route overview without route does nothing`() {
        val nullRouteHolder = createHolder(route = null)

        nullRouteHolder.onRouteOverviewRequested()

        assertTrue(nullRouteHolder.uiState.value.isFollowingLocation)
        assertEquals(0, nullRouteHolder.uiState.value.routeOverviewRequestId)
    }
}
