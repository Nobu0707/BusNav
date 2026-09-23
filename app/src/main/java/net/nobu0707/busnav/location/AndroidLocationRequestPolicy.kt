package net.nobu0707.busnav.location

/** LocationManager requests GPS every second while precise permission is available. */
object AndroidLocationRequestPolicy {
    const val updateIntervalMillis = 1_000L
    const val minimumDistanceMeters = 1f
    fun providers(hasFinePermission: Boolean): List<String> =
        if (hasFinePermission) listOf("gps", "network") else listOf("network")
}
