package net.nobu0707.busnav.ui.routeplan

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.routeplan.RoutePlan
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType

/** Pure camera data: no MapView/Activity references survive recreation. */
data class EditorCamera(val center: GeoPoint, val zoom: Double, val bearing: Double = 0.0, val tilt: Double = 0.0)
data class EditorCameraRequest(val id: Long, val points: List<GeoPoint>)
enum class EditorSheetState { PEEK, PARTIAL, EXPANDED }

fun editorEntryPoints(active: ScheduledRoute?, candidate: ScheduledRoute?, plan: RoutePlan): List<GeoPoint> =
    active?.geometry?.points?.takeIf { it.isNotEmpty() }
        ?: candidate?.geometry?.points?.takeIf { it.isNotEmpty() }
        ?: plan.points.map { it.position }

val RoutePlanPointType.displayName: String
    get() = when (this) {
        RoutePlanPointType.START -> "出発地"
        RoutePlanPointType.DESTINATION -> "目的地"
        RoutePlanPointType.VIA -> "経由地"
        RoutePlanPointType.SHAPING -> "通過指定"
    }

const val SHAPING_HELPER = "この付近を通るよう経路を調整"
