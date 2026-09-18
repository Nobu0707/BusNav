package net.nobu0707.busnav.location

import kotlinx.coroutines.flow.Flow

interface LocationProvider {
    fun updates(): Flow<LocationUpdate>
    fun isLocationEnabled(): Boolean
}

