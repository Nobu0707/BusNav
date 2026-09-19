package net.nobu0707.busnav.domain.routing

data class VehicleProfile(
    val id: String,
    val name: String,
    val lengthMeters: Double,
    val widthMeters: Double,
    val heightMeters: Double,
    val weightMetricTons: Double,
    val axleLoadMetricTons: Double? = null,
) {
    init {
        require(id.isNotBlank()) { "Vehicle profile id must not be blank" }
        require(name.isNotBlank()) { "Vehicle profile name must not be blank" }
        require(lengthMeters > 0.0) { "Vehicle length must be positive" }
        require(widthMeters > 0.0) { "Vehicle width must be positive" }
        require(heightMeters > 0.0) { "Vehicle height must be positive" }
        require(weightMetricTons > 0.0) { "Vehicle weight must be positive" }
        require(axleLoadMetricTons == null || axleLoadMetricTons > 0.0) {
            "Vehicle axle load must be positive when specified"
        }
    }

    companion object {
        /** Placeholder dimensions for development only; replace with the actual vehicle profile. */
        val DEVELOPMENT_LARGE_BUS = VehicleProfile(
            id = "development-large-bus",
            name = "大型バス（開発用車両条件）",
            lengthMeters = 12.0,
            widthMeters = 2.5,
            heightMeters = 3.5,
            weightMetricTons = 16.0,
            axleLoadMetricTons = 10.0,
        )
    }
}
