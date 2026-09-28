package net.nobu0707.busnav.ui.facility

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Locale
import net.nobu0707.busnav.domain.facility.*

private const val CAVEAT = "経路付近の施設候補です。反対方向の施設が含まれる場合があります。"
private const val LOCK = "安全な場所に停車して変更してください"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteFacilitySheet(
    state: RouteFacilityUiState,
    moving: Boolean,
    onDismiss: () -> Unit,
    onSelect: (RouteFacilityId) -> Unit,
    onAdd: (RouteFacilityId) -> Unit,
    onRemove: (RouteFacilityId) -> Unit,
) {
    val selected = state.candidates.firstOrNull { it.id == state.selectedId }
    val upcoming = state.candidates.filter { state.distances[it.id]?.passed != true }.take(20)
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("route_facility_sheet")) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp).padding(horizontal = 16.dp)) {
            item {
                Text("この先のSA/PA", style = MaterialTheme.typography.titleLarge)
                Text(CAVEAT, style = MaterialTheme.typography.bodySmall)
                if (moving) Text(LOCK, color = MaterialTheme.colorScheme.error)
                if (state.persistenceError) Text("休憩予定を保存できませんでした", color = MaterialTheme.colorScheme.error)
            }
            if (state.plannedStops.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)); Text("休憩予定", style = MaterialTheme.typography.titleMedium) }
                items(state.plannedStops, key = { "planned-${it.facilityId.osmType}${it.facilityId.osmId}" }) { stop ->
                    val distance = state.distances[stop.facilityId]?.distanceAheadMeters
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${stop.name} " + when (stop.status) {
                            PlannedStopStatus.UPCOMING -> distanceLabel(distance, state.distancesReliable)
                            PlannedStopStatus.PASSED -> "通過済み"
                            PlannedStopStatus.NOT_ON_CURRENT_ROUTE_CANDIDATES -> "現在の経路候補にありません"
                        }, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRemove(stop.facilityId) }, enabled = !moving) { Text("解除") }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("候補一覧", style = MaterialTheme.typography.titleMedium)
                when (state.loadState) {
                    RouteFacilityLoadState.NO_ROUTE -> Text("経路が設定されていません")
                    RouteFacilityLoadState.LOADING -> CircularProgressIndicator()
                    RouteFacilityLoadState.ERROR -> Text("SA/PA情報を取得できませんでした。現在のサーバーはIPv6接続が必要です。")
                    RouteFacilityLoadState.READY -> if (state.candidates.isEmpty()) Text("候補がありません")
                }
            }
            if (selected != null && !moving) item {
                HorizontalDivider()
                Text(selected.name, style = MaterialTheme.typography.titleMedium)
                Text("${if (selected.type == RouteFacilityType.SERVICE_AREA) "SA" else "PA"} ・${distanceLabel(state.distances[selected.id]?.distanceAheadMeters, state.distancesReliable)}")
                Text("経路から約${selected.corridorDistanceMeters.toInt()}m ・方向未確認")
                Text(CAVEAT, style = MaterialTheme.typography.bodySmall)
                Text("Data © OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
                if (state.plannedStops.any { it.facilityId == selected.id })
                    Button(onClick = { onRemove(selected.id) }) { Text("休憩予定を解除") }
                else Button(onClick = { onAdd(selected.id) }) { Text("休憩予定に設定") }
            }
            items(upcoming, key = { "${it.id.osmType}${it.id.osmId}" }) { candidate ->
                val planned = state.plannedStops.any { it.facilityId == candidate.id }
                Column(Modifier.fillMaxWidth().clickable(enabled = !moving) { onSelect(candidate.id) }
                    .padding(vertical = 8.dp).testTag("facility_${candidate.id.osmType}${candidate.id.osmId}")) {
                    Text("${candidate.name}  ${if (candidate.type == RouteFacilityType.SERVICE_AREA) "SA" else "PA"}",
                        style = MaterialTheme.typography.bodyLarge)
                    Text("${distanceLabel(state.distances[candidate.id]?.distanceAheadMeters, state.distancesReliable)} ・方向未確認" +
                        if (planned) " ・休憩予定" else "", style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
            item {
                Text("Data © OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

fun distanceLabel(meters: Double?, reliable: Boolean): String {
    if (!reliable || meters == null) return "距離を確認中"
    val ahead = meters.coerceAtLeast(0.0)
    return if (ahead < 1000) "約${ahead.toInt()}m先" else "約${String.format(Locale.JAPAN, "%.1f", ahead / 1000)}km先"
}