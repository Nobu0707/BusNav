package net.nobu0707.busnav.ui.routeplan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import net.nobu0707.busnav.ui.routing.RouteCalculationState
import net.nobu0707.busnav.ui.routing.userMessage

object RoutePlanEditorTestTags {
    const val HANDLE = "editor_sheet_handle"
    const val SHEET = "editor_sheet"
    const val REGISTER = "editor_register"
    const val CURSOR = "editor_cursor"
    const val FOOTER = "editor_footer"
    const val SCREEN = "route_plan_editor"
    const val MAP = "route_plan_map"
    const val POINT_LIST = "route_plan_point_list"
    const val EMPTY = "route_plan_empty"
    const val COMPLETE = "route_plan_complete"
    const val OVERVIEW = "route_plan_overview"
    const val BACK = "route_plan_back"
    const val CALCULATE = "route_plan_calculate"
    const val CALCULATING = "route_plan_calculating"
    const val RESULT = "route_plan_result"
    const val APPLY = "route_plan_apply"
    const val FAILURE = "route_plan_failure"
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
    onOpenConnections: (() -> Unit)? = null,
    libraryActions: (@Composable () -> Unit)? = null,
    calculationState: RouteCalculationState = RouteCalculationState.Idle,
    onCalculate: () -> Unit = {},
    onApplyCalculatedRoute: () -> Unit = {},
    onRegisterCursor: () -> Unit = {},
    onSheetStateChanged: (EditorSheetState) -> Unit = {},
    onSheetHeightChanged: (EditorSheetState, Int) -> Unit = { _, _ -> },
    mapContent: @Composable (Modifier) -> Unit,
) {
    var endpointPendingDeletion by remember { mutableStateOf<RoutePlanPoint?>(null) }
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing).testTag(RoutePlanEditorTestTags.SCREEN),
    ) {
        val density = LocalDensity.current
        val headerHeight = 56.dp
        val mapHeight = (maxHeight - headerHeight).coerceAtLeast(120.dp)
        val peek = 104.dp
        val landscape = maxWidth > maxHeight
        val expanded = (mapHeight - if (landscape) 64.dp else 128.dp).coerceAtLeast(peek)
        val partial = (mapHeight * 0.40f).coerceAtLeast(if (landscape) 160.dp else 240.dp).coerceIn(peek, expanded)
        val listState = rememberLazyListState()
        LaunchedEffect(calculationState) {
            if (calculationState !is RouteCalculationState.Idle) listState.scrollToItem(1)
        }
        var level by remember(uiState.sheetState) { mutableStateOf(uiState.sheetState) }
        var drag by remember { mutableStateOf(0f) }
        fun heightFor(value: EditorSheetState) = when (value) {
            EditorSheetState.PEEK -> peek
            EditorSheetState.PARTIAL -> partial
            EditorSheetState.EXPANDED -> expanded
        }
        val height = (heightFor(level) + with(density) { drag.toDp() }).coerceIn(peek, expanded)
        fun settle() {
            val destination = if (drag > with(density) { 24.dp.toPx() }) {
                if (level == EditorSheetState.PEEK) EditorSheetState.PARTIAL else EditorSheetState.EXPANDED
            } else if (drag < -with(density) { 24.dp.toPx() }) {
                if (level == EditorSheetState.EXPANDED) EditorSheetState.PARTIAL else EditorSheetState.PEEK
            } else level
            drag = 0f
            level = destination
            onSheetStateChanged(destination)
        }
        val nestedScroll = object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val before = drag
                val base = with(density) { heightFor(level).toPx() }
                drag = (drag - available.y).coerceIn(with(density) { peek.toPx() } - base, with(density) { expanded.toPx() } - base)
                return Offset(0f, before - drag)
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                settle()
                return Velocity.Zero
            }
        }
        LaunchedEffect(height, level, density) { onSheetHeightChanged(level, with(density) { height.roundToPx() }) }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 8.dp)) {
                EditorHeader(onBack, onOpenConnections)
            }
            Box(Modifier.fillMaxWidth().weight(1f).testTag(RoutePlanEditorTestTags.MAP)) {
                mapContent(Modifier.fillMaxSize())
                net.nobu0707.busnav.map.MapSelectionCursor(net.nobu0707.busnav.map.MapSelectionMode.ROUTE_POINT)
                Card(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Text(if (calculationState is RouteCalculationState.Success && calculationState.planRevision == uiState.revision)
                        "探索結果（道路沿いルート）" else "仮ルート（経路探索前プレビュー）",
                        Modifier.padding(6.dp), style = MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.align(Alignment.BottomEnd).padding(bottom = height + 4.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPlanOverview, enabled = uiState.currentPlan.points.isNotEmpty(),
                        modifier = Modifier.heightIn(min = 48.dp).testTag(RoutePlanEditorTestTags.OVERVIEW)) { Text("プラン全体") }
                    Button(onClick = onRegisterCursor,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(RoutePlanEditorTestTags.REGISTER)) { Text(uiState.selectedAddMode.displayName + "を登録") }
                }
                Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(height)
                    .testTag(RoutePlanEditorTestTags.SHEET).semantics { stateDescription = level.name },
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().height(48.dp).testTag(RoutePlanEditorTestTags.HANDLE)
                            .semantics { contentDescription = "地点一覧を引き出す・縮める" }
                            .clickable {
                                level = if (level == EditorSheetState.EXPANDED) EditorSheetState.PEEK else EditorSheetState.EXPANDED
                                onSheetStateChanged(level)
                            }
                            .pointerInput(level, density) {
                                detectVerticalDragGestures(onDragEnd = { settle() }, onDragCancel = { drag = 0f }) { change, amount ->
                                    change.consume()
                                    val base = with(density) { heightFor(level).toPx() }
                                    drag = (drag - amount).coerceIn(with(density) { peek.toPx() } - base, with(density) { expanded.toPx() } - base)
                                }
                            }, contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(40.dp, 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp)))
                                Text("地点一覧・" + uiState.currentPlan.points.size + "件", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        val intermediates = uiState.currentPlan.points.filterNot { it.type.isEndpoint }
                        LazyColumn(Modifier.fillMaxWidth().weight(1f).nestedScroll(nestedScroll)
                            .testTag(RoutePlanEditorTestTags.POINT_LIST), state = listState,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                            item(key = "types") {
                                AddModeSelector(uiState.selectedAddMode, onSelectAddMode)
                                Text("地図を動かして中央の十字を合わせ、地点を登録します。", style = MaterialTheme.typography.bodySmall)
                                Text("通過指定：" + SHAPING_HELPER, style = MaterialTheme.typography.bodySmall)
                                Text(if (uiState.validation.isRoutingReady) "出発地・目的地を設定済み" else "出発地と目的地を設定してください",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("開発用車両条件（実車の業務運行には使用しないでください）", style = MaterialTheme.typography.labelSmall)
                            }
                            if (libraryActions != null) item(key = "library") { libraryActions() }
                            item(key = "calculation") {
                                RouteCalculationPanel(calculationState, uiState.revision, onApplyCalculatedRoute)
                            }
                            if (uiState.currentPlan.points.isEmpty()) item(key = "empty") {
                                Text("地点はまだありません", Modifier.testTag(RoutePlanEditorTestTags.EMPTY))
                            }
                            itemsIndexed(uiState.currentPlan.points, key = { _, point -> point.id }) { _, point ->
                                val index = intermediates.indexOfFirst { it.id == point.id }
                                RoutePlanPointRow(point, point.id == uiState.selectedPointId, index > 0,
                                    index >= 0 && index < intermediates.lastIndex,
                                    { onSelectPoint(point.id) },
                                    { if (point.type.isEndpoint) endpointPendingDeletion = point else onRemovePoint(point.id) },
                                    { onMovePoint(point.id, -1) }, { onMovePoint(point.id, 1) }, { onTogglePointType(point.id) })
                            }
                        }
                        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp).testTag(RoutePlanEditorTestTags.FOOTER),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = {
                                level = EditorSheetState.PARTIAL
                                onSheetStateChanged(level)
                                onCalculate()
                            }, enabled = uiState.validation.isRoutingReady && calculationState !is RouteCalculationState.Calculating,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(RoutePlanEditorTestTags.CALCULATE)) { Text("経路探索") }
                            Button(onClick = onComplete,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(RoutePlanEditorTestTags.COMPLETE)) { Text("編集完了") }
                        }
                    }
                }
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
private fun EditorHeader(onBack: () -> Unit, onOpenConnections: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .testTag(RoutePlanEditorTestTags.BACK)
                .semantics { contentDescription = "ナビ画面へ戻る" },
        ) { Text("戻る") }
        Text("ルート編集", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (net.nobu0707.busnav.BuildConfig.DEBUG && onOpenConnections != null) {
            TextButton(onClick = onOpenConnections, modifier = Modifier.testTag("connections_open")) { Text("開発接続設定") }
        }
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
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = "${type.displayName}追加モード" },
    )
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
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(point.type.displayName, fontWeight = FontWeight.SemiBold)
                point.name?.let { name -> Text(
                    name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                ) }
            }
            Text("%.6f, %.6f".format(java.util.Locale.ROOT, point.position.latitude, point.position.longitude),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!point.type.isEndpoint) {
                TextButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(RoutePlanEditorTestTags.moveUp(point.id)).semantics {
                        contentDescription = "${point.type.displayName}を上へ移動"
                    },
                ) { Text("↑") }
                TextButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(RoutePlanEditorTestTags.moveDown(point.id)).semantics {
                        contentDescription = "${point.type.displayName}を下へ移動"
                    },
                ) { Text("↓") }
                TextButton(
                    onClick = onToggleType,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(RoutePlanEditorTestTags.toggle(point.id)).semantics {
                        contentDescription = "経由地と通過指定を切り替え"
                    },
                ) { Text("切替") }
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.heightIn(min = 48.dp).testTag(RoutePlanEditorTestTags.delete(point.id)).semantics {
                    contentDescription = "${point.type.displayName}を削除"
                },
            ) { Text("削除") }
            }
        }
    }
}

@Composable
private fun RouteCalculationPanel(
    state: RouteCalculationState,
    currentRevision: Long,
    onApply: () -> Unit,
) {
    when (state) {
        RouteCalculationState.Idle -> Unit
        is RouteCalculationState.Calculating -> Row(
            modifier = Modifier.fillMaxWidth().testTag(RoutePlanEditorTestTags.CALCULATING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator()
            Text("経路を探索しています", style = MaterialTheme.typography.bodySmall)
        }
        is RouteCalculationState.Failure -> Text(
            state.reason.userMessage(),
            modifier = Modifier.testTag(RoutePlanEditorTestTags.FAILURE),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        is RouteCalculationState.Success -> {
            val current = state.planRevision == currentRevision
            Card(
                modifier = Modifier.fillMaxWidth().testTag(RoutePlanEditorTestTags.RESULT),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("探索結果", fontWeight = FontWeight.SemiBold)
                    Text(formatRouteSummary(state.summary.distanceMeters, state.summary.durationSeconds))
                    if (!current) Text("プラン変更前の結果です。再探索してください。", color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = onApply,
                        enabled = current,
                        modifier = Modifier.fillMaxWidth().testTag(RoutePlanEditorTestTags.APPLY),
                    ) { Text("このルートを使用") }
                }
            }
        }
    }
}

private fun formatRouteSummary(distanceMeters: Double, durationSeconds: Double): String {
    val distance = "%.1f km".format(distanceMeters / 1_000.0)
    val totalMinutes = (durationSeconds / 60.0).toInt()
    val duration = if (totalMinutes >= 60) "${totalMinutes / 60}時間${totalMinutes % 60}分" else "${totalMinutes}分"
    return "探索距離 $distance・推定所要時間 $duration"
}

private val RoutePlanPointType.isEndpoint: Boolean
    get() = this == RoutePlanPointType.START || this == RoutePlanPointType.DESTINATION
