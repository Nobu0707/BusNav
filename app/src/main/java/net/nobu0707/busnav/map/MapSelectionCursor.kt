package net.nobu0707.busnav.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.nobu0707.busnav.domain.model.MapViewportInsets
import net.nobu0707.busnav.domain.model.VisibleMapViewport

enum class MapSelectionMode { NONE, ROUTE_POINT, FREE_DESTINATION, DETOUR_REJOIN, DETOUR_POINT }

@Composable
fun MapSelectionCursor(mode: MapSelectionMode, modifier: Modifier = Modifier, bottomInsetPx: Int = 0) {
    if (mode == MapSelectionMode.NONE) return
    val color = MaterialTheme.colorScheme.primary
    val detour = mode == MapSelectionMode.DETOUR_REJOIN || mode == MapSelectionMode.DETOUR_POINT
    Canvas(modifier.fillMaxSize().testTag(if (detour) "detour_cursor" else if (mode == MapSelectionMode.ROUTE_POINT) "editor_cursor" else "free_cursor")
        .semantics { contentDescription = if (detour) "迂回設定位置・地図中央" else if (mode == MapSelectionMode.ROUTE_POINT) "登録位置・地図中央" else "目的地・地図中央" }) {
        val rect = VisibleMapViewport(size.width.toInt(), size.height.toInt(),
            MapViewportInsets(bottom = bottomInsetPx)).rect
        val c = Offset(rect.centerX, rect.centerY)
        drawCircle(Color.White, 8.dp.toPx(), c)
        drawLine(color, Offset(c.x, c.y - 16.dp.toPx()), Offset(c.x, c.y + 16.dp.toPx()), 2.dp.toPx())
        drawLine(color, Offset(c.x - 16.dp.toPx(), c.y), Offset(c.x + 16.dp.toPx(), c.y), 2.dp.toPx())
    }
}
