package net.nobu0707.busnav.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.nobu0707.busnav.domain.navigation.NavigationCameraState

@Composable
internal fun MapControls(navigationCamera: NavigationCameraState, cameraBearing: Double,
    onToggleOrientation: (() -> Unit)?, onResetNorth: () -> Unit,
    onZoom: (Int) -> Unit, ruler: ScaleRulerReading?, modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null) {
    val compass: @Composable () -> Unit = {
        if (navigationCamera.active && onToggleOrientation != null) {
            NavigationCompass(navigationCamera.orientation, cameraBearing, onToggleOrientation)
        } else if (MapControlsPolicy.generalCompassVisible(navigationCamera.active, cameraBearing)) {
            GeneralNorthCompass(cameraBearing, onResetNorth)
        }
    }
    val zoom: @Composable () -> Unit = {
        Surface(Modifier.width(MapControlsPolicy.RULER_CARD_WIDTH_DP.dp).testTag("zoom_scale_control"),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 3.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ZoomButton(1, onZoom)
                if (ruler != null) {
                    HorizontalDivider()
                    ScaleRuler(ruler)
                }
                HorizontalDivider()
                ZoomButton(-1, onZoom)
            }
        }
    }
    val search: @Composable () -> Unit = {
        if (onSearch != null) OutlinedButton(onClick = onSearch,
            modifier = Modifier.testTag("map_search").semantics { contentDescription = "場所を検索" }) {
            Text("検索")
        }
    }
    BoxWithConstraints(modifier) {
        // Short landscape maps keep the vertical zoom pair beside the compass,
        // so this group stays above the bottom-right location controls.
        if (maxHeight < 260.dp) {
            Row(Modifier.verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) { compass(); search(); zoom() }
        } else {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                compass(); search(); zoom()
            }
        }
    }
}

@Composable
private fun ZoomButton(direction: Int, onZoom: (Int) -> Unit) {
    Box(Modifier.size(MapControlsPolicy.RULER_CARD_WIDTH_DP.dp, 48.dp)
        .testTag(if (direction > 0) "map_zoom_in" else "map_zoom_out")
        .semantics { contentDescription = if (direction > 0) "地図を拡大" else "地図を縮小" }
        .clickable { onZoom(direction) }, contentAlignment = Alignment.Center) {
        Text(if (direction > 0) "+" else "−", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ScaleRuler(reading: ScaleRulerReading) {
    val width = with(LocalDensity.current) { reading.widthPx.toDp() }
    val color = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().testTag("map_scale_ruler").padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
            Text(reading.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
            Canvas(Modifier.width(width).height(9.dp)) {
                val stroke = 2.dp.toPx()
                val bottom = size.height - stroke
                drawLine(color, Offset(0f, bottom), Offset(size.width, bottom), stroke)
                drawLine(color, Offset(0f, 0f), Offset(0f, bottom), stroke)
                drawLine(color, Offset(size.width, 0f), Offset(size.width, bottom), stroke)
            }
    }
}
