package net.nobu0707.busnav.test

import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import net.nobu0707.busnav.location.LocationUpdate

/** androidTest only. Repeated stationary synthetic fixes keep live UI tests independent of screenshot timing. */
fun repeatingSyntheticLocations(positions: StateFlow<LocationUpdate>): Flow<LocationUpdate> = flow {
    while (currentCoroutineContext().isActive) {
        val sample = positions.value
        emit(if (sample is LocationUpdate.Position) sample.copy(location = sample.location.copy(
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(), timestampMillis = System.currentTimeMillis())) else sample)
        delay(1000)
    }
}
