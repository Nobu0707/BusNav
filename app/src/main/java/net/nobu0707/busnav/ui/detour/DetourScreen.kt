package net.nobu0707.busnav.ui.detour

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.nobu0707.busnav.domain.detour.*
import net.nobu0707.busnav.map.*
import java.util.Locale

@Composable
fun DetourScreen(state: DetourUiState, onCancel: () -> Unit, onTarget: (String) -> Unit,
    onMapMode: (DetourMapMode) -> Unit, onCursor: () -> Unit, onRemove: (String) -> Unit,
    onMove: (String, Int) -> Unit, onCalculate: () -> Unit, onActivate: () -> Unit,
    onEdit: () -> Unit, cursorReady: Boolean, guidanceContent: @Composable (Modifier) -> Unit = {}, mapContent: @Composable (Modifier) -> Unit) {
    val enabled = !state.locked && !state.preparing
    val preview = state.stage == DetourSessionState.PREVIEW
    val choosing = state.stage == DetourSessionState.SELECTING_REJOIN
    val controls: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.locked) guidanceContent(Modifier.fillMaxWidth().heightIn(max = 130.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (preview) "3. 迂回経路のプレビュー" else if (choosing) "1. 所定経路の復帰地点" else "2. 経由地・通過指定（任意）",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("detour_stage"))
                Text("水色：所定経路 / 太い紫線：迂回経路", style = MaterialTheme.typography.bodySmall)
                if (state.locked) Text(DetourStateHolder.SAFETY_MESSAGE, color = MaterialTheme.colorScheme.error)
                state.error?.let { Text(it, Modifier.testTag("detour_error"), color = MaterialTheme.colorScheme.error) }
                state.trafficContext?.let { context ->
                    Text(if (context.minimumSafeRejoinProgress == null) "規制の終端は未確認です。現地情報を確認して復帰地点を選択してください。"
                        else "規制区間の終端と安全余裕より先に復帰します。", style = MaterialTheme.typography.bodySmall)
                }
                if (preview) state.trafficValidation?.message?.let { Text(it, Modifier.testTag("traffic_detour_validation"), color = MaterialTheme.colorScheme.error) }
                if (state.preparing) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (choosing) {
                    if (!state.preparing && state.candidates.isEmpty()) Text("安全な自動候補がありません。地図で復帰地点を選択してください。")
                    state.candidates.forEachIndexed { i, target ->
                        OutlinedButton(onClick = { onTarget(target.id) }, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().testTag("detour_target_$i")) {
                            Text(String.format(Locale.JAPAN, "復帰候補%d・所定経路 約%.1f km先", i + 1,
                                (target.progressMeters - (state.anchorProgressMeters ?: 0.0)) / 1000))
                        }
                    }
                    TextButton(onClick = { onMapMode(DetourMapMode.REJOIN) }, enabled = enabled,
                        modifier = Modifier.testTag("detour_manual")) { Text("地図で復帰地点を選択") }
                }
                state.target?.let { target ->
                    Text(String.format(Locale.JAPAN, "復帰位置：所定経路の起点から %.1f km", target.progressMeters / 1000))
                }
                if (preview) {
                    state.candidate?.summary?.let {
                        Text(String.format(Locale.JAPAN, "迂回 %.1f km ・ 約%.0f分", it.distanceMeters / 1000, it.durationSeconds / 60))
                    }
                    Text("経由地・通過指定：${state.points.size}件")
                    state.vehicleProfile?.let {
                        Text("${it.name} / 長${it.lengthMeters}m・幅${it.widthMeters}m・高${it.heightMeters}m・${it.weightMetricTons}t",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onEdit, enabled = enabled) { Text("編集に戻る") }
                } else if (!choosing && state.stage != DetourSessionState.CALCULATING) {
                    TextButton(onClick = { onMapMode(DetourMapMode.REJOIN) }, enabled = enabled) { Text("復帰地点を変更") }
                    Text("経路計算は交通規制を自動回避しません。必要に応じて別の道路に経由地を置いてください。", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { onMapMode(DetourMapMode.VIA) }, enabled = enabled, modifier = Modifier.testTag("detour_via")) { Text("経由地を追加") }
                        TextButton(onClick = { onMapMode(DetourMapMode.SHAPING) }, enabled = enabled, modifier = Modifier.testTag("detour_shaping")) { Text("通過指定を追加") }
                    }
                    state.points.forEachIndexed { i, p ->
                        Column {
                            Text("${i + 1}. " + if (p.type == DetourDraftPointType.VIA) "経由地" else "通過指定")
                            Row {
                                TextButton(onClick = { onMove(p.id, -1) }, enabled = enabled && i > 0) { Text("上へ") }
                                TextButton(onClick = { onMove(p.id, 1) }, enabled = enabled && i < state.points.lastIndex) { Text("下へ") }
                                TextButton(onClick = { onRemove(p.id) }, enabled = enabled) { Text("削除") }
                            }
                        }
                    }
                }
                if (state.stage == DetourSessionState.CALCULATING) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("迂回経路を計算中", Modifier.testTag("detour_calculating"))
                }
            }
            if (state.mapMode != DetourMapMode.NONE) {
                Text("地図を動かして中央の十字を合わせます", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onCursor, enabled = enabled && cursorReady, modifier = Modifier.testTag("detour_set_cursor")) {
                    Text(if (state.mapMode == DetourMapMode.REJOIN) "復帰地点に設定" else "この地点を追加")
                }
            } else if (preview) {
                Button(onClick = onActivate, enabled = enabled && state.trafficValidation?.activationAllowed != false, modifier = Modifier.fillMaxWidth().testTag("detour_activate")) { Text("この迂回経路を使用") }
            } else if (!choosing && state.stage != DetourSessionState.CALCULATING) {
                Button(onClick = onCalculate, enabled = enabled && state.target != null, modifier = Modifier.testTag("detour_calculate")) { Text("迂回経路を計算") }
            }
        }
    }
    val map: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
            mapContent(Modifier.fillMaxSize())
            if (state.mapMode != DetourMapMode.NONE)
                MapSelectionCursor(if (state.mapMode == DetourMapMode.REJOIN) MapSelectionMode.DETOUR_REJOIN else MapSelectionMode.DETOUR_POINT)
        }
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag("detour_screen")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("detour_cancel")) { Text("キャンセル") }
            Text("迂回を設定", Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
        }
        if (LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            Row(Modifier.weight(1f)) {
                map(Modifier.fillMaxHeight().weight(0.55f))
                controls(Modifier.fillMaxHeight().weight(0.45f))
            }
        } else {
            map(Modifier.fillMaxWidth().weight(1f))
            controls(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.48f).dp))
        }
    }
}
