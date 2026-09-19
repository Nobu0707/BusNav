package net.nobu0707.busnav.ui.routeplan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.nobu0707.busnav.domain.routeplan.RoutePlanPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType

object RoutePlanEditorTestTags {
    const val SCREEN = "route_plan_editor"
    const val MAP = "route_plan_map"
    const val POINT_LIST = "route_plan_point_list"
    const val EMPTY = "route_plan_empty"
    const val COMPLETE = "route_plan_complete"
    const val OVERVIEW = "route_plan_overview"
    const val BACK = "route_plan_back"
    fun point(id: String) = "route_plan_point_$id"
    fun delete(id: String) = "route_plan_delete_$id"
    fun moveUp(id: String) = "route_plan_move_up_$id"
    fun moveDown(id: String) = "route_plan_move_down_$id"
    fun toggle(id: String) = "route_plan_toggle_$id"
}

@Composable
fun RoutePlanEditorScreen(
    uiState: RoutePlanUiState,
    onBack: () -> Unit,
    onSelectAddMode: (RoutePlanPointType) -> Unit,
    onSelectPoint: (String) -> Unit,
    onRemovePoint: (String) -> Unit,
    onMovePoint: (String, Int) -> Unit,
    onTogglePointType: (String) -> Unit,
    onPlanOverview: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    mapContent: @Composable (Modifier) -> Unit,
) {
    var endpointPendingDeletion by remember { mutableStateOf<RoutePlanPoint?>(null) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag(RoutePlanEditorTestTags.SCREEN),
    ) {
        val isLandscape = maxWidth.value >= 600f && maxWidth > maxHeight
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorPanel(
                    uiState = uiState,
                    onBack = onBack,
                    onSelectAddMode = onSelectAddMode,
                    onSelectPoint = onSelectPoint,
                    onDeleteRequest = { point ->
                        if (point.type.isEndpoint) endpointPendingDeletion = point else onRemovePoint(point.id)
                    },
                    onMovePoint = onMovePoint,
                    onTogglePointType = onTogglePointType,
                    onComplete = onComplete,
                    modifier = Modifier.fillMaxHeight().weight(0.38f),
                )
                PlanMapPanel(
                    hasPoints = uiState.currentPlan.points.isNotEmpty(),
                    onPlanOverview = onPlanOverview,
                    modifier = Modifier.fillMaxHeight().weight(0.62f),
                    mapContent = mapContent,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorHeader(onBack = onBack)
                PlanMapPanel(
                    hasPoints = uiState.currentPlan.points.isNotEmpty(),
                    onPlanOverview = onPlanOverview,
                    modifier = Modifier.fillMaxWidth().weight(0.47f),
                    mapContent = mapContent,
                )
                EditorPanel(
                    uiState = uiState,
                    onBack = onBack,
                    onSelectAddMode = onSelectAddMode,
                    onSelectPoint = onSelectPoint,
                    onDeleteRequest = { point ->
                        if (point.type.isEndpoint) endpointPendingDeletion = point else onRemovePoint(point.id)
                    },
                    onMovePoint = onMovePoint,
                    onTogglePointType = onTogglePointType,
                    onComplete = onComplete,
                    showHeader = false,
                    modifier = Modifier.fillMaxWidth().weight(0.53f),
                )
            }
        }
    }

    endpointPendingDeletion?.let { point ->
        AlertDialog(
            onDismissRequest = { endpointPendingDeletion = null },
            title = { Text("${point.type.displayName}を削除しますか？") },
            text = { Text("経路探索可能な状態ではなくなります。") },
            confirmButton = {
                TextButton(onClick = {
                    onRemovePoint(point.id)
                    endpointPendingDeletion = null
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { endpointPendingDeletion = null }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun EditorPanel(
    uiState: RoutePlanUiState,
    onBack: () -> Unit,
    onSelectAddMode: (RoutePlanPointType) -> Unit,
    onSelectPoint: (String) -> Unit,
    onDeleteRequest: (RoutePlanPoint) -> Unit,
    onMovePoint: (String, Int) -> Unit,
    onTogglePointType: (String) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier,
    showHeader: Boolean = true,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showHeader) EditorHeader(onBack)
            Text("追加する地点", style = MaterialTheme.typography.labelLarge)
            AddModeSelector(uiState.selectedAddMode, onSelectAddMode)
            Text(
                "種類を選び、地図を長押しして追加します。VIAは必ず通る地点、SHAPINGはルート形状の誘導点です。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RoutePlanPointList(
                uiState = uiState,
                onSelectPoint = onSelectPoint,
                onDeleteRequest = onDeleteRequest,
                onMovePoint = onMovePoint,
                onTogglePointType = onTogglePointType,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            val status = if (uiState.validation.isRoutingReady) {
                "出発地・到着地を設定済み"
            } else {
                "出発地と到着地を設定してください"
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().testTag(RoutePlanEditorTestTags.COMPLETE),
            ) { Text("編集完了") }
        }
    }
}

@Composable
private fun EditorHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .testTag(RoutePlanEditorTestTags.BACK)
                .semantics { contentDescription = "ナビ画面へ戻る" },
        ) { Text("戻る") }
        Text("ルート編集", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AddModeSelector(selected: RoutePlanPointType, onSelect: (RoutePlanPointType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AddModeChip(RoutePlanPointType.START, selected, onSelect, Modifier.weight(1f))
            AddModeChip(RoutePlanPointType.DESTINATION, selected, onSelect, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AddModeChip(RoutePlanPointType.VIA, selected, onSelect, Modifier.weight(1f))
            AddModeChip(RoutePlanPointType.SHAPING, selected, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AddModeChip(
    type: RoutePlanPointType,
    selected: RoutePlanPointType,
    onSelect: (RoutePlanPointType) -> Unit,
    modifier: Modifier,
) {
    FilterChip(
        selected = selected == type,
        onClick = { onSelect(type) },
        label = { Text(type.displayName) },
        modifier = modifier.semantics { contentDescription = "${type.displayName}追加モード" },
    )
}

@Composable
private fun RoutePlanPointList(
    uiState: RoutePlanUiState,
    onSelectPoint: (String) -> Unit,
    onDeleteRequest: (RoutePlanPoint) -> Unit,
    onMovePoint: (String, Int) -> Unit,
    onTogglePointType: (String) -> Unit,
    modifier: Modifier,
) {
    val intermediates = uiState.currentPlan.points.filterNot { it.type.isEndpoint }
    if (uiState.currentPlan.points.isEmpty()) {
        Box(modifier = modifier.testTag(RoutePlanEditorTestTags.EMPTY), contentAlignment = Alignment.Center) {
            Text("地点はまだありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = modifier.testTag(RoutePlanEditorTestTags.POINT_LIST),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(uiState.currentPlan.points, key = { index, item -> "$index-${item.id}" }) { _, point ->
            val intermediateIndex = intermediates.indexOfFirst { it.id == point.id }
            RoutePlanPointRow(
                point = point,
                selected = point.id == uiState.selectedPointId,
                canMoveUp = intermediateIndex > 0,
                canMoveDown = intermediateIndex >= 0 && intermediateIndex < intermediates.lastIndex,
                onSelect = { onSelectPoint(point.id) },
                onDelete = { onDeleteRequest(point) },
                onMoveUp = { onMovePoint(point.id, -1) },
                onMoveDown = { onMovePoint(point.id, 1) },
                onToggleType = { onTogglePointType(point.id) },
            )
        }
    }
}

@Composable
private fun RoutePlanPointRow(
    point: RoutePlanPoint,
    selected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleType: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RoutePlanEditorTestTags.point(point.id))
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(point.type.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    point.name ?: "%.5f, %.5f".format(point.position.latitude, point.position.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!point.type.isEndpoint) {
                TextButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.testTag(RoutePlanEditorTestTags.moveUp(point.id)).semantics {
                        contentDescription = "${point.type.displayName}を上へ移動"
                    },
                ) { Text("↑") }
                TextButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.testTag(RoutePlanEditorTestTags.moveDown(point.id)).semantics {
                        contentDescription = "${point.type.displayName}を下へ移動"
                    },
                ) { Text("↓") }
                TextButton(
                    onClick = onToggleType,
                    modifier = Modifier.testTag(RoutePlanEditorTestTags.toggle(point.id)).semantics {
                        contentDescription = "VIAとSHAPINGを切り替え"
                    },
                ) { Text("切替") }
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.testTag(RoutePlanEditorTestTags.delete(point.id)).semantics {
                    contentDescription = "${point.type.displayName}を削除"
                },
            ) { Text("削除") }
        }
    }
}

@Composable
private fun PlanMapPanel(
    hasPoints: Boolean,
    onPlanOverview: () -> Unit,
    modifier: Modifier,
    mapContent: @Composable (Modifier) -> Unit,
) {
    Box(modifier = modifier.testTag(RoutePlanEditorTestTags.MAP), contentAlignment = Alignment.BottomEnd) {
        mapContent(Modifier.fillMaxSize())
        Card(
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        ) {
            Text("仮ルート（経路探索前プレビュー）", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
            onClick = onPlanOverview,
            enabled = hasPoints,
            modifier = Modifier
                .padding(10.dp)
                .testTag(RoutePlanEditorTestTags.OVERVIEW)
                .semantics { contentDescription = "編集プラン全体を表示" },
        ) { Text("プラン全体") }
    }
}

private val RoutePlanPointType.isEndpoint: Boolean
    get() = this == RoutePlanPointType.START || this == RoutePlanPointType.DESTINATION

private val RoutePlanPointType.displayName: String
    get() = when (this) {
        RoutePlanPointType.START -> "出発地"
        RoutePlanPointType.DESTINATION -> "到着地"
        RoutePlanPointType.VIA -> "VIA・必ず通る"
        RoutePlanPointType.SHAPING -> "SHAPING・形状誘導"
    }
