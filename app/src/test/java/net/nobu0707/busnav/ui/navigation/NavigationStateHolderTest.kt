package net.nobu0707.busnav.ui.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import net.nobu0707.busnav.location.LocationProvider
import net.nobu0707.busnav.location.LocationUpdate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationStateHolderTest {
    private val holder = NavigationStateHolder(
        locationProvider = object : LocationProvider {
            override fun updates(): Flow<LocationUpdate> = emptyFlow()
            override fun isLocationEnabled(): Boolean = true
        },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

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
}
