package net.nobu0707.busnav.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.nobu0707.busnav.domain.navigation.NavigationCameraState

@Composable
internal fun MapControls(navigationCamera: NavigationCameraState, cameraBearing: Double,
    onToggleOrientation: (() -> Unit)?, onResetNorth: () -> Unit,
    scaleMode: ScaleMode, onScale: () -> Unit, ruler: ScaleRulerReading?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (navigationCamera.active && onToggleOrientation != null) {
            NavigationCompass(navigationCamera.orientation, cameraBearing, onToggleOrientation)
        } else if (MapControlsPolicy.generalCompassVisible(navigationCamera.active, cameraBearing)) {
            FilledTonalButton(onClick = onResetNorth,
                modifier = Modifier.size(64.dp, 56.dp).testTag("general_north_compass")
                    .semantics { contentDescription = "北を上に戻す" },
                contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(16.dp)) {
                Text("N ↑", style = MaterialTheme.typography.titleMedium)
            }
        }
        FilledTonalButton(onClick = onScale,
            modifier = Modifier.size(64.dp, 56.dp).testTag("map_scale_preset")
                .semantics { contentDescription = "縮尺を変更" },
            contentPadding = PaddingValues(2.dp), shape = RoundedCornerShape(16.dp)) {
            Text(when (scaleMode) {
                ScaleMode.NEAR -> "近く"
                ScaleMode.NORMAL -> "標準"
                ScaleMode.WIDE -> "広域"
                ScaleMode.CUSTOM -> "縮尺"
            }, style = MaterialTheme.typography.labelMedium)
        }
        if (ruler != null) ScaleRuler(ruler)
    }
}

@Composable
private fun ScaleRuler(reading: ScaleRulerReading) {
    val density = LocalDensity.current
    val width = with(density) { reading.widthPx.toDp() }
    val color = MaterialTheme.colorScheme.onSurface
    Card(Modifier.testTag("map_scale_ruler"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(reading.label, style = MaterialTheme.typography.labelSmall)
            Canvas(Modifier.width(width).height(9.dp)) {
                val stroke = 2.dp.toPx()
                val bottom = size.height - stroke
                drawLine(color, Offset(0f, bottom), Offset(size.width, bottom), stroke)
                drawLine(color, Offset(0f, 0f), Offset(0f, bottom), stroke)
                drawLine(color, Offset(size.width, 0f), Offset(size.width, bottom), stroke)
            }
        }
    }
}
