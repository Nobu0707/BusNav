package net.nobu0707.busnav.domain.route

data class RouteMetadata(
    val description: String? = null,
    val distanceMeters: Double? = null,
    val durationSeconds: Double? = null,
    val routingSource: String? = null,
)
