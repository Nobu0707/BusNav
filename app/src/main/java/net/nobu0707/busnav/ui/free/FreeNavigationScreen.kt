package net.nobu0707.busnav.ui.free

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.nobu0707.busnav.map.*
import net.nobu0707.busnav.ui.routing.RouteCalculationState
import java.util.Locale

@Composable
fun FreeNavigationScreen(
    state: FreeNavigationUiState,
    onCancel: () -> Unit,
    onSetDestination: () -> Unit,
    onCalculate: () -> Unit,
    onStart: () -> Unit,
    onChangeDestination: () -> Unit,
    onPermission: () -> Unit,
    needsPermission: Boolean,
    cursorReady: Boolean,
    mapContent: @Composable (Modifier) -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val controls: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("安全な場所で操作してください", style = MaterialTheme.typography.bodySmall)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("free_error")) }
                when (state.stage) {
                    FreeNavigationStage.SELECTING -> {
                        if (!state.isRecalculation) Text("地図を動かして中央の十字を目的地に合わせます")
                        state.plan?.let { Text(it.destinationName ?: "選択した目的地", Modifier.testTag("free_destination")) }
                    }
                    FreeNavigationStage.CALCULATING -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("大型車条件で経路を計算中", Modifier.testTag("free_calculating"))
                    }
                    FreeNavigationStage.PREVIEW -> {
                        Text("案内経路のプレビュー", Modifier.testTag("free_preview"), style = MaterialTheme.typography.titleMedium)
                        Text("目的地：" + (state.plan?.destinationName ?: "地図で選択した地点"))
                        (state.calculation as? RouteCalculationState.Success)?.summary?.let {
                            Text(String.format(Locale.JAPAN, "%.1f km ・ 約%.0f分", it.distanceMeters / 1000, it.durationSeconds / 60))
                        }
                        val profile = state.vehicleProfile
                        Text("${profile.name} / 長${profile.lengthMeters}m・幅${profile.widthMeters}m・高${profile.heightMeters}m・${profile.weightMetricTons}t",
                            style = MaterialTheme.typography.bodySmall)
                        if (state.previewRoute?.guidance?.maneuvers.isNullOrEmpty()) Text("案内情報なし・経路線を表示します")
                    }
                    FreeNavigationStage.IDLE -> Unit
                }
            }
            if (needsPermission) Button(onClick = onPermission) { Text("位置情報を許可") }
            // Actions stay outside the scrolling details, including in short landscape viewports.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state.stage) {
                    FreeNavigationStage.SELECTING -> {
                        if (!state.isRecalculation) OutlinedButton(onClick = onSetDestination, enabled = cursorReady,
                            modifier = Modifier.testTag("free_set_destination")) { Text("目的地に設定") }
                        if (state.plan != null) Button(onClick = onCalculate,
                            modifier = Modifier.testTag("free_calculate")) { Text("経路を計算") }
                    }
                    FreeNavigationStage.PREVIEW -> {
                        Button(onClick = onStart, modifier = Modifier.testTag("free_start")) {
                            Text(if (state.isRecalculation) "新しい経路を使用" else "案内開始")
                        }
                        if (!state.isRecalculation) TextButton(onClick = onChangeDestination) { Text("目的地を変更") }
                    }
                    else -> Unit
                }
            }
        }
    }
    val map: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
            mapContent(Modifier.fillMaxSize())
            if (state.stage == FreeNavigationStage.SELECTING && !state.isRecalculation)
                MapSelectionCursor(MapSelectionMode.FREE_DESTINATION)
        }
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag("free_screen")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("free_cancel")) { Text("キャンセル") }
            Text("現在地からナビ", Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
        }
        if (landscape) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                map(Modifier.fillMaxHeight().weight(0.58f))
                controls(Modifier.fillMaxHeight().weight(0.42f))
            }
        } else {
            map(Modifier.fillMaxWidth().weight(1f))
            controls(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.42f).dp))
        }
    }
}
