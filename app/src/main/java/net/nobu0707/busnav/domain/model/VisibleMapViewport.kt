package net.nobu0707.busnav.domain.model

data class MapViewportInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
)

data class VisibleMapRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    fun yAt(fractionFromTop: Float): Float = top + height * fractionFromTop
}

/** All coordinates are local to the MapView, including occlusions drawn over it. */
data class VisibleMapViewport(val width: Int, val height: Int, val insets: MapViewportInsets = MapViewportInsets()) {
    val rect: VisibleMapRect get() {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val left = insets.left.coerceIn(0, safeWidth - 1)
        val top = insets.top.coerceIn(0, safeHeight - 1)
        val right = (safeWidth - insets.right.coerceAtLeast(0)).coerceIn(left + 1, safeWidth)
        val bottom = (safeHeight - insets.bottom.coerceAtLeast(0)).coerceIn(top + 1, safeHeight)
        return VisibleMapRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }
}
