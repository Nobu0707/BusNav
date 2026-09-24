package net.nobu0707.busnav.location

/** Stationary starts need fresh fixes even when the device has not moved. */
object AndroidLocationRequestPolicy {
    const val updateIntervalMillis = 1_000L
    const val minimumDistanceMeters = 0f
    fun providers(hasFinePermission: Boolean): List<String> =
        if (hasFinePermission) listOf("gps", "network") else listOf("network")
}
