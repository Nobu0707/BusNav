package net.nobu0707.busnav.map

import kotlin.math.*
import net.nobu0707.busnav.domain.model.GeoPoint

/** Caller projects the actual unobscured map rectangle, in MapView pixels. */
object VisibleMapSpanCalculator {
    data class ScreenPoint(val x: Float, val y: Float)
    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float)

    fun measure(rect: Rect, project: (ScreenPoint) -> GeoPoint): Double {
        if (rect.right <= rect.left || rect.bottom <= rect.top) return Double.NaN
        val x = (rect.left + rect.right) / 2
        // Vertical in portrait; also the short dimension in landscape.
        return distance(project(ScreenPoint(x, rect.top)), project(ScreenPoint(x, rect.bottom)))
    }

    fun distance(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin((lat2 - lat1) / 2).pow(2) + cos(lat1) * cos(lat2) *
            sin(Math.toRadians(b.longitude - a.longitude) / 2).pow(2)
        return 6_371_008.8 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}

class ShieldSpanPolicy(private val showBelowMeters: Double = 2200.0, private val hideAboveMeters: Double = 2600.0) {
    init { require(showBelowMeters > 0 && hideAboveMeters > showBelowMeters) }
    var visible = false
        private set
    fun update(spanMeters: Double): Boolean {
        visible = spanMeters.isFinite() && spanMeters > 0 &&
            if (visible) spanMeters <= hideAboveMeters else spanMeters <= showBelowMeters
        return visible
    }
}
