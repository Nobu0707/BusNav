package net.nobu0707.busnav.map

import kotlin.math.abs

object MapControlsPolicy {
    const val ZOOM_STEP = 1.0
    const val RULER_CARD_WIDTH_DP = 68f
    const val RULER_MIN_BAR_DP = 28f
    const val RULER_MAX_BAR_DP = 56f

    fun generalCompassVisible(navigationActive: Boolean, bearing: Double): Boolean =
        !navigationActive && bearing.isFinite() &&
            abs(((bearing + 180.0) % 360.0 + 360.0) % 360.0 - 180.0) >= 4.0

    fun steppedZoom(current: Double, direction: Int, minimum: Double, maximum: Double): Double =
        (current + direction.coerceIn(-1, 1) * ZOOM_STEP).coerceIn(minimum, maximum)
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
            width in minimumWidthPx * 0.9f..maximumWidthPx
        }
        if (old != null) return old.copy(widthPx = (old.distanceMeters / metersPerPixel).toFloat())
        // Minimum is preferred, maximum is strict. Never shorten a bar while
        // keeping its label. Hide when even 10 m cannot fit truthfully.
        return niceMeters.map { ScaleRulerReading(it, (it / metersPerPixel).toFloat()) }
            .lastOrNull { it.widthPx in 1f..maximumWidthPx }
    }
}
