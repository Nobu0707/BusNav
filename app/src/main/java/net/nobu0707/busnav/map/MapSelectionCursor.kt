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

enum class MapSelectionMode { NONE, ROUTE_POINT, FREE_DESTINATION }

@Composable
fun MapSelectionCursor(mode: MapSelectionMode, modifier: Modifier = Modifier) {
    if (mode == MapSelectionMode.NONE) return
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxSize().testTag(if (mode == MapSelectionMode.ROUTE_POINT) "editor_cursor" else "free_cursor")
        .semantics { contentDescription = if (mode == MapSelectionMode.ROUTE_POINT) "登録位置・地図中央" else "目的地・地図中央" }) {
        val c = center
        drawCircle(Color.White, 8.dp.toPx(), c)
        drawLine(color, Offset(c.x, c.y - 16.dp.toPx()), Offset(c.x, c.y + 16.dp.toPx()), 2.dp.toPx())
        drawLine(color, Offset(c.x - 16.dp.toPx(), c.y), Offset(c.x + 16.dp.toPx(), c.y), 2.dp.toPx())
    }
}
