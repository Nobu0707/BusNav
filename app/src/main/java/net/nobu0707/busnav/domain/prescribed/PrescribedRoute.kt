package net.nobu0707.busnav.domain.prescribed

import kotlinx.coroutines.flow.Flow
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.routeplan.RoutePlan
import net.nobu0707.busnav.domain.routeplan.validateForRouting
import net.nobu0707.busnav.domain.routing.VehicleProfile

enum class NavigationMode { PRESCRIBED, FREE }

data class PrescribedRouteRecord(
    val id: String,
    val name: String,
    val description: String?,
    val routePlan: RoutePlan,
    val route: ScheduledRoute,
    val vehicleProfile: VehicleProfile,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val schemaVersion: Int = 1,
) {
    fun validate() {
        require(id.isNotBlank() && name.isNotBlank()) { "名前を入力してください" }
        require(schemaVersion == 1)
        require(routePlan.validateForRouting().isRoutingReady) { "出発地と目的地を確認してください" }
        require(route.geometry.points.size >= 2)
        route.guidance?.maneuvers?.forEachIndexed { index, m ->
            require(m.index == index && m.beginGeometryIndex in route.geometry.points.indices &&
                m.endGeometryIndex in m.beginGeometryIndex..route.geometry.points.lastIndex)
            require(listOfNotNull(m.distanceMeters, m.durationSeconds).all { it.isFinite() && it >= 0 })
        }
        require(listOfNotNull(route.metadata.distanceMeters, route.metadata.durationSeconds).all { it.isFinite() && it >= 0 })
        require(routePlan.points.map { Triple(it.id, it.type.name, it.position) } ==
            route.points.map { Triple(it.id, it.type.name, it.position) }) {
            "地点が変更されています。経路を再計算して適用してください"
        }
        val dimensions = listOf(vehicleProfile.lengthMeters, vehicleProfile.widthMeters,
            vehicleProfile.heightMeters, vehicleProfile.weightMetricTons) +
            listOfNotNull(vehicleProfile.axleLoadMetricTons)
        require(dimensions.all { it.isFinite() && it > 0 })
        require(createdAtEpochMillis >= 0 && updatedAtEpochMillis >= 0)
    }
}

data class PrescribedRouteSummary(
    val id: String, val name: String, val description: String?,
    val distanceMeters: Double?, val startName: String?, val destinationName: String?,
    val updatedAtEpochMillis: Long, val schemaVersion: Int,
)

sealed interface PrescribedRouteLoad {
    data class Found(val record: PrescribedRouteRecord) : PrescribedRouteLoad
    data object Missing : PrescribedRouteLoad
    data object Corrupt : PrescribedRouteLoad
    data object Unsupported : PrescribedRouteLoad
}

interface PrescribedRouteRepository {
    fun observeAll(): Flow<List<PrescribedRouteSummary>>
    suspend fun getById(id: String): PrescribedRouteLoad
    /** existingOnly prevents a stale edit from resurrecting a deleted record. */
    suspend fun save(record: PrescribedRouteRecord, existingOnly: Boolean = false)
    suspend fun delete(id: String)
    suspend fun rename(id: String, name: String)
}