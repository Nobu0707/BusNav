package net.nobu0707.busnav.ui.traffic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.nobu0707.busnav.domain.traffic.*
import net.nobu0707.busnav.ui.navigation.formatGuidanceDistance
import java.text.DateFormat
import java.util.Date

fun TrafficRouteImpact.alertText(): String {
    val where = if (matchingConfidence != TrafficMatchConfidence.HIGH) "経路付近に" else "経路上に"
    val distance = distanceAheadMeters?.let { formatGuidanceDistance(it) + "先 " }.orEmpty()
    return distance + where + event.kind.label() + "情報" + highwayDecisionLabel?.let { "（$it）" }.orEmpty()
}
fun TrafficEventKind.label(): String = when (this) {
    TrafficEventKind.ROAD_CLOSURE -> "× 通行止め"
    TrafficEventKind.ENTRY_CLOSURE -> "× 入口閉鎖"
    TrafficEventKind.EXIT_CLOSURE -> "× 出口閉鎖"
    TrafficEventKind.WINTER_CLOSURE -> "× 冬期閉鎖"
    TrafficEventKind.LANE_RESTRICTION -> "! 車線規制"
    TrafficEventKind.SPEED_RESTRICTION -> "! 速度規制"
    TrafficEventKind.ACCIDENT -> "! 事故"
    TrafficEventKind.ROADWORK -> "工 工事"
    TrafficEventKind.OBSTACLE -> "! 路上障害物"
    TrafficEventKind.CONGESTION -> "≋ 渋滞"
    TrafficEventKind.EVENT_RESTRICTION -> "! イベント規制"
    TrafficEventKind.WEATHER_HAZARD -> "! 気象影響"
    TrafficEventKind.DISASTER -> "! 災害による影響"
    TrafficEventKind.UNKNOWN -> "! その他交通障害"
}
private fun timestamp(value: Long?) = value?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "不明"
private fun canConsider(impact: TrafficRouteImpact) = impact.position in listOf(TrafficImpactPosition.AHEAD, TrafficImpactPosition.CURRENT) &&
    impact.level in listOf(TrafficImpactLevel.BLOCKING, TrafficImpactLevel.RESTRICTION)

@Composable fun TrafficAlert(state: TrafficUiState, prescribed: Boolean, onConsider: (TrafficRouteImpact) -> Unit, onOpen: () -> Unit) {
    val impact = state.alert
    val stale = state.status in listOf(TrafficProviderStatus.STALE, TrafficProviderStatus.ERROR, TrafficProviderStatus.UNAVAILABLE)
    if (impact == null && !stale && state.highwayWarning == null) return
    val text = impact?.alertText() ?: state.statusText
    Card(Modifier.fillMaxWidth().testTag("traffic_alert").semantics { contentDescription = text }.clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = if (impact?.level == TrafficImpactLevel.BLOCKING) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            state.highwayWarning?.let { Text("! 次の分岐・出入口 ${it.highwayDecisionLabel}：${it.event.kind.label()}",
                Modifier.testTag("traffic_highway_warning"), style = MaterialTheme.typography.labelMedium) }
            Text(if (stale) state.statusText else state.snapshot.source.displayName, style = MaterialTheme.typography.labelSmall)
            if (prescribed && impact != null && canConsider(impact)) TextButton(onClick = { onConsider(impact) },
                modifier = Modifier.testTag("traffic_consider_detour")) { Text("迂回を検討") }
        }
    }
}

@Composable fun TrafficPanel(state: TrafficUiState, prescribed: Boolean, onConsider: (TrafficRouteImpact) -> Unit,
    onDismiss: () -> Unit, developerContent: @Composable () -> Unit = {}) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("交通情報・規制") },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("traffic_panel"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.statusText, Modifier.testTag("traffic_status"))
            Text(state.snapshot.source.displayName)
            Text("最終受信：${timestamp(state.snapshot.receivedAtEpochMillis)}", style = MaterialTheme.typography.bodySmall)
            state.ageMillis?.let { Text("情報の経過：約${it / 60_000}分", style = MaterialTheme.typography.bodySmall) }
            if (state.status == TrafficProviderStatus.AVAILABLE && state.activeEvents.isEmpty()) Text("受信情報に現在有効な規制はありません")
            developerContent()
            fun impactsFor(event: TrafficEvent) = state.impacts.firstOrNull { it.event.id == event.id && it.event.source == event.source }
            val routeEvents = state.impacts.filter { it.position in listOf(TrafficImpactPosition.AHEAD, TrafficImpactPosition.CURRENT) }.map { it.event }
            val other = state.activeEvents.filter { it !in routeEvents }
            listOf("経路への影響" to routeEvents, "周辺・その他（後方／位置未確定を含む）" to other, "予告規制" to state.futureEvents).forEach { (label, events) ->
                if (events.isNotEmpty()) Text(label, style = MaterialTheme.typography.titleSmall)
                events.forEach { event ->
                    val impact = impactsFor(event)
                    val key = event.source.providerId + ":" + event.id
                    Card(Modifier.fillMaxWidth().clickable { selected = if (selected == key) null else key }.testTag("traffic_event_${event.id}")) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(event.kind.label())
                            Text(event.roadName ?: event.roadReference ?: "道路名不明")
                            impact?.let { Text(it.alertText(), style = MaterialTheme.typography.bodySmall) }
                            Text("提供：${event.source.displayName}", style = MaterialTheme.typography.labelSmall)
                            if (selected == key) {
                                Text(event.title)
                                event.description?.let { Text(it) }
                                Text("有効期間：${timestamp(event.validFromEpochMillis)} ～ ${timestamp(event.validUntilEpochMillis)}")
                                Text("更新：${timestamp(state.snapshot.updatedAt(event))}")
                                Text("出典：${event.source.attribution}")
                                Text("影響：${impact?.level ?: TrafficImpactLevel.NONE} / ${impact?.position ?: TrafficImpactPosition.OFF_ROUTE}")
                                Text("一致の信頼度：${impact?.matchingConfidence ?: TrafficMatchConfidence.LOW} / 方向：${event.direction}")
                            }
                            if (prescribed && impact != null && canConsider(impact)) TextButton(onClick = { onConsider(impact) }) { Text("迂回を検討") }
                        }
                    }
                }
            }
            Text("交通規制・渋滞を考慮した自動再探索には未対応です。", style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } })
}
