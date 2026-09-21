package net.nobu0707.busnav.detour

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.prescribed.*
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.domain.routeplan.*
import net.nobu0707.busnav.domain.routing.*

fun ScheduledRoute.copy(geometry: RouteGeometry = this.geometry, guidance: RouteGuidance? = this.guidance) =
    ScheduledRoute(id, name, geometry, points, metadata, guidance)

fun point(meters: Double, offset: Double = 0.0) = GeoPoint(35.68 + offset / 111195.0, 139.76 + meters / 90300.0)
fun detourFixture(size: Int = 401): PrescribedRouteRecord {
    val geometry = RouteGeometry(List(size) { point(it * 12_000.0 / (size - 1)) })
    val plan = RoutePlan("public", "公開合成経路", listOf(
        RoutePlanPoint("s", RoutePlanPointType.START, geometry.first),
        RoutePlanPoint("d", RoutePlanPointType.DESTINATION, geometry.last)))
    val route = ScheduledRoute("original", "公開合成経路", geometry,
        plan.points.map { RoutePoint(it.id, RoutePointType.valueOf(it.type.name), it.position) },
        guidance = RouteGuidance(listOf(RouteManeuver(0, ManeuverType.RIGHT, "", size / 2, size / 2),
            RouteManeuver(1, ManeuverType.DESTINATION, "", size - 1, size - 1))))
    return PrescribedRouteRecord("saved", "保存経路", null, plan, route,
        VehicleProfile("saved-bus", "保存車両", 11.5, 2.4, 3.3, 14.0, 9.0), 1000, 1000)
}
fun resultFor(request: RoutingRequest): RoutingResult.Success {
    val geometry = RouteGeometry(request.points.map { it.position })
    return RoutingResult.Success(ScheduledRoute(request.routePlanId, "迂回", geometry,
        request.points.map { RoutePoint(it.id, RoutePointType.valueOf(it.type.name), it.position) },
        guidance = RouteGuidance(listOf(RouteManeuver(0, ManeuverType.RIGHT, "", 1, 1)))),
        RoutingSummary(1200.0, 200.0))
}
