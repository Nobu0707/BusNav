package net.nobu0707.busnav.data.route

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.RouteGeometry
import net.nobu0707.busnav.domain.route.RouteMetadata
import net.nobu0707.busnav.domain.route.RoutePoint
import net.nobu0707.busnav.domain.route.RoutePointType
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.route.ScheduledRouteRepository

class InMemoryScheduledRouteRepository(
    private val activeRoute: ScheduledRoute? = null,
) : ScheduledRouteRepository {
    override suspend fun getActiveRoute(): ScheduledRoute? = activeRoute
}

fun createDevelopmentSampleRoute(): ScheduledRoute {
    val geometryPoints = listOf(
        GeoPoint(35.6812, 139.7671),
        GeoPoint(35.6896, 139.7528),
        GeoPoint(35.6995, 139.7380),
        GeoPoint(35.7101, 139.7219),
        GeoPoint(35.7212, 139.7065),
        GeoPoint(35.7295, 139.7109),
        GeoPoint(35.7370, 139.7258),
    )
    return ScheduledRoute(
        id = "development-sample-route",
        name = "開発用サンプルルート",
        geometry = RouteGeometry(geometryPoints),
        points = listOf(
            RoutePoint("sample-start", RoutePointType.START, geometryPoints.first(), "サンプル始点"),
            RoutePoint("sample-stop-1", RoutePointType.STOP, geometryPoints[2], "サンプル停留所1"),
            RoutePoint("sample-stop-2", RoutePointType.STOP, geometryPoints[4], "サンプル停留所2"),
            RoutePoint("sample-destination", RoutePointType.DESTINATION, geometryPoints.last(), "サンプル終点"),
        ),
        metadata = RouteMetadata(description = "実在の営業路線ではない開発確認専用データ"),
    )
}
