package net.nobu0707.busnav.location

import net.nobu0707.busnav.domain.model.GeoPoint

data class LocationState(
    val point: GeoPoint,
    val accuracyMeters: Float?,
    val bearingDegrees: Float?,
    val speedMetersPerSecond: Float?,
    val timestampMillis: Long,
) {
    val normalizedBearingDegrees: Float?
        get() = bearingDegrees?.let { ((it % 360f) + 360f) % 360f }
}

sealed interface LocationUpdate {
    data class Position(val location: LocationState) : LocationUpdate
    data class Error(val message: String) : LocationUpdate
    data object Disabled : LocationUpdate
}

