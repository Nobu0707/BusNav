package net.nobu0707.busnav.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.nobu0707.busnav.domain.search.*
import java.util.Locale

@Composable
fun PlaceSearchDialog(state: PlaceSearchUiState, driving: Boolean, onQuery: (String) -> Unit,
    onBias: (SearchBiasMode) -> Unit, onSubmit: () -> Unit, onSelect: (PlaceSearchItem) -> Unit,
    onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.85f).testTag("place_search_dialog"),
            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("場所を検索", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("閉じる") }
                }
                OutlinedTextField(value = state.query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth()
                    .testTag("place_search_field"), singleLine = true, enabled = !driving,
                    label = { Text("施設名・駅・住所") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }))
                if (driving) Text("安全な場所に停車して検索してください", color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    BiasChip("全国", SearchBiasMode.NONE, state.context.biasMode, onBias)
                    if (state.context.currentLocation != null)
                        BiasChip("現在地周辺", SearchBiasMode.CURRENT_LOCATION, state.context.biasMode, onBias)
                    if (state.context.visibleMapBounds != null)
                        BiasChip("地図周辺", SearchBiasMode.VISIBLE_MAP, state.context.biasMode, onBias)
                }
                if (state.context.visibleMapBounds != null) Row {
                    Checkbox(checked = state.context.biasMode == SearchBiasMode.VISIBLE_MAP_BOUNDED,
                        onCheckedChange = { onBias(if (it) SearchBiasMode.VISIBLE_MAP_BOUNDED else SearchBiasMode.VISIBLE_MAP) })
                    Text("この地図の範囲で検索", modifier = Modifier.padding(top = 12.dp))
                }
                if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("place_search_loading"))
                state.failure?.let { failure ->
                    Text(failureMessage(failure), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("place_search_error"))
                    if (failure is PlaceSearchFailure.Network || failure is PlaceSearchFailure.Timeout ||
                        failure is PlaceSearchFailure.ServerUnavailable)
                        Text("現在の検索サーバーはIPv6接続が必要です", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onSubmit) { Text("再試行") }
                }
                if (state.searched && !state.searching && state.failure == null && state.items.isEmpty())
                    Text("候補が見つかりません", modifier = Modifier.testTag("place_search_empty"))
                LazyColumn(Modifier.weight(1f)) {
                    items(state.items, key = { it.id.value }) { item ->
                        Column(Modifier.fillMaxWidth().clickable { onSelect(item) }
                            .testTag("place_search_result_${item.id.value}").padding(vertical = 8.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(listOfNotNull(item.categoryLabel(), item.sourceType).joinToString(" / "),
                                style = MaterialTheme.typography.bodySmall)
                            Text(item.address?.summary ?: item.displayName.ifBlank { "${item.point.latitude}, ${item.point.longitude}" },
                                style = MaterialTheme.typography.bodySmall)
                            item.distanceFromCurrentMeters?.let {
                                Text(String.format(Locale.JAPAN, "現在地から %.1f km", it / 1000),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            HorizontalDivider()
                        }
                    }
                }
                Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RowScope.BiasChip(label: String, mode: SearchBiasMode, selected: SearchBiasMode,
    onBias: (SearchBiasMode) -> Unit) {
    FilterChip(selected = mode == selected, onClick = { onBias(mode) }, label = { Text(label) })
}

private fun failureMessage(failure: PlaceSearchFailure): String = when (failure) {
    PlaceSearchFailure.RateLimited -> "検索が多すぎます。少し待ってから再試行してください"
    PlaceSearchFailure.Network, PlaceSearchFailure.Timeout, PlaceSearchFailure.ServerUnavailable -> "検索サーバーへ接続できません"
    PlaceSearchFailure.Parse -> "検索結果を読み取れません"
    is PlaceSearchFailure.Http -> "検索に失敗しました（HTTP ${failure.code}）"
}

@Composable
fun SelectedPlaceDialog(item: PlaceSearchItem, actions: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(item.name) }, text = {
        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(listOfNotNull(item.categoryLabel(), item.sourceType).joinToString(" / "))
            Text(item.address?.summary ?: item.displayName)
            actions.forEach { (label, action) -> TextButton(onClick = action) { Text(label) } }
            Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("地図で確認") } })
}
