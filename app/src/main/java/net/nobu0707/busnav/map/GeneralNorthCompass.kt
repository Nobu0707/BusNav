package net.nobu0707.busnav.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.nobu0707.busnav.domain.navigation.northScreenRotation
import org.maplibre.android.camera.CameraPosition

internal fun northUpCamera(current: CameraPosition): CameraPosition =
    CameraPosition.Builder(current).bearing(0.0).build()

@Composable
internal fun GeneralNorthCompass(cameraBearing: Double, onResetNorth: () -> Unit) {
    val needle = MaterialTheme.colorScheme.error
    FilledTonalButton(onClick = onResetNorth,
        modifier = Modifier.size(64.dp, 56.dp).testTag("general_north_compass")
            .semantics { contentDescription = "北を上に戻す" },
        contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(16.dp)) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            // Same clockwise screen rotation as NavigationCompass. Only the pointer rotates;
            // the centered N and the touch target stay upright, including at 180 degrees.
            Canvas(Modifier.fillMaxSize().rotate(northScreenRotation(cameraBearing).toFloat())) {
                drawPath(Path().apply {
                    moveTo(size.width * 0.5f, size.height * 0.02f)
                    lineTo(size.width * 0.38f, size.height * 0.25f)
                    lineTo(size.width * 0.62f, size.height * 0.25f)
                    close()
                }, needle)
            }
            Text("N", style = MaterialTheme.typography.labelMedium)
        }
    }
}
