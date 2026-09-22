package net.nobu0707.busnav.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.nobu0707.busnav.domain.navigation.*

@Composable
fun NavigationCompass(orientation: NavigationMapOrientation, cameraBearing: Double, onToggle: () -> Unit,
    modifier: Modifier = Modifier) {
    val heading = orientation == NavigationMapOrientation.HEADING_UP
    val description = if (heading) "地図表示：進行方向が上。タップで北を上にする"
        else "地図表示：北が上。タップで進行方向を上にする"
    val needle = MaterialTheme.colorScheme.error
    val outline = MaterialTheme.colorScheme.onSurface
    FilledTonalButton(onClick = onToggle,
        modifier = modifier.size(width = 64.dp, height = 80.dp).testTag("navigation_compass")
            .semantics { contentDescription = description },
        contentPadding = PaddingValues(4.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(44.dp).rotate(northScreenRotation(cameraBearing).toFloat()), contentAlignment = Alignment.TopCenter) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(outline.copy(alpha = 0.45f), radius = size.minDimension / 2 - 2.dp.toPx(),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    val cx = size.width / 2
                    drawPath(Path().apply {
                        moveTo(cx, size.height * 0.34f)
                        lineTo(cx - size.width * 0.12f, size.height * 0.72f)
                        lineTo(cx + size.width * 0.12f, size.height * 0.72f)
                        close()
                    }, needle)
                    drawLine(outline, Offset(cx, size.height * 0.72f), Offset(cx, size.height * 0.87f), 2.dp.toPx())
                }
                Text("N", style = MaterialTheme.typography.labelSmall, color = outline)
            }
            Text(if (heading) "進行方向" else "北固定", style = MaterialTheme.typography.labelSmall)
        }
    }
}
