package net.nobu0707.busnav.prescribed

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.prescribed.*
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.domain.routeplan.*
import net.nobu0707.busnav.domain.routing.VehicleProfile

fun prescribedFixture(id: String = "record", size: Int = 4001, updated: Long = 2000): PrescribedRouteRecord {
    val geometry = RouteGeometry(List(size) { GeoPoint(35.68 + it * 0.00000123456789, 139.76 + it * 0.00000198765432) })
    val points = listOf(RoutePlanPoint("start", RoutePlanPointType.START, geometry.first, "公開テスト始点"),
        RoutePlanPoint("via", RoutePlanPointType.VIA, geometry.points[size / 3], "経由地"),
        RoutePlanPoint("shape", RoutePlanPointType.SHAPING, geometry.points[size / 2], "通過指定"),
        RoutePlanPoint("dest", RoutePlanPointType.DESTINATION, geometry.last, "公開テスト終点"))
    val maneuvers = List(100.coerceAtMost(size - 1)) { index ->
        val begin = index * (size - 1) / 100.coerceAtMost(size - 1)
        RouteManeuver(index, ManeuverType.entries[index % ManeuverType.entries.size], "案内 $index", begin,
            (begin + 1).coerceAtMost(size - 1), "事前案内", "事後案内", listOf("公道", "国道"),
            12.3456789, 9.87654321, HighwaySignType.entries.map { HighwaySign(it, "出口案内", 3) })
    }
    val route = ScheduledRoute("calculation-id", "計算時の名前", geometry,
        points.map { RoutePoint(it.id, RoutePointType.valueOf(it.type.name), it.position, it.name) },
        RouteMetadata("メタデータ", 1234.56789, 987.654321, "synthetic"),
        RouteGuidance(maneuvers))
    return PrescribedRouteRecord(id, "公開テスト経路", "説明", RoutePlan("plan-id", "プラン名", points), route,
        VehicleProfile("bus", "試験車両", 11.9, 2.49, 3.4, 15.3, 9.8), 1000, updated)
}