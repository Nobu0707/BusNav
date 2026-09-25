package net.nobu0707.busnav.map

import kotlin.math.abs
import kotlin.math.log2

enum class ScalePreset(val spanMeters: Double, val label: String) {
    NEAR(500.0, "近く"), NORMAL(1800.0, "標準"), WIDE(5000.0, "広域");

    fun next(): ScalePreset = entries[(ordinal + 1) % entries.size]
}

enum class ScaleMode { NEAR, NORMAL, WIDE, CUSTOM }

object MapControlsPolicy {
    fun generalCompassVisible(navigationActive: Boolean, bearing: Double): Boolean =
        !navigationActive && bearing.isFinite() &&
            abs(((bearing + 180.0) % 360.0 + 360.0) % 360.0 - 180.0) >= 4.0

    fun nextPreset(mode: ScaleMode): ScalePreset = when (mode) {
        ScaleMode.NEAR -> ScalePreset.NORMAL
        ScaleMode.NORMAL -> ScalePreset.WIDE
        ScaleMode.WIDE -> ScalePreset.NEAR
        ScaleMode.CUSTOM -> ScalePreset.NORMAL
    }

    fun zoomForSpan(currentZoom: Double, currentSpanMeters: Double, targetSpanMeters: Double): Double =
        if (currentSpanMeters.isFinite() && currentSpanMeters > 0.0)
            (currentZoom + log2(currentSpanMeters / targetSpanMeters)).coerceIn(2.0, 20.0)
        else currentZoom
}

data class ScaleRulerReading(val distanceMeters: Int, val widthPx: Float) {
    val label: String get() = if (distanceMeters >= 1000) "${distanceMeters / 1000} km" else "$distanceMeters m"
}

object ScaleRulerPolicy {
    private val niceMeters = intArrayOf(10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000)

    fun choose(metersPerPixel: Double, minimumWidthPx: Float, maximumWidthPx: Float,
        previous: ScaleRulerReading? = null): ScaleRulerReading? {
        if (!metersPerPixel.isFinite() || metersPerPixel <= 0.0 ||
            minimumWidthPx <= 0f || maximumWidthPx < minimumWidthPx) return null
        val old = previous?.takeIf {
            val width = (it.distanceMeters / metersPerPixel).toFloat()
            width in minimumWidthPx * 0.9f..maximumWidthPx * 1.1f
        }
        if (old != null) return old.copy(widthPx = (old.distanceMeters / metersPerPixel).toFloat())
        val candidate = niceMeters.map { ScaleRulerReading(it, (it / metersPerPixel).toFloat()) }
            .filter { it.widthPx in minimumWidthPx..maximumWidthPx }
        return candidate.lastOrNull() ?: niceMeters.map { ScaleRulerReading(it, (it / metersPerPixel).toFloat()) }
            .minByOrNull { abs(it.widthPx - (minimumWidthPx + maximumWidthPx) / 2f) }
    }
}
