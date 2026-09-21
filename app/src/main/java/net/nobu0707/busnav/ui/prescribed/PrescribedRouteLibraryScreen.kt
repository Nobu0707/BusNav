package net.nobu0707.busnav.ui.prescribed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import net.nobu0707.busnav.domain.prescribed.PrescribedRouteSummary

@Composable
fun PrescribedRouteLibraryScreen(
    state: PrescribedRouteLibraryUiState, activeId: String?,
    onBack: () -> Unit, onCreate: () -> Unit, onSaveCurrent: (Boolean) -> Unit, onEndUse: () -> Unit,
    onOpen: (String) -> Unit, onNavigate: (String) -> Unit, onEdit: (String) -> Unit, onDuplicate: (String) -> Unit,
    onRename: (String, String) -> Unit, onDelete: (String) -> Unit,
) {
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameName by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp).testTag("prescribed_library")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("戻る") }
            Text("所定経路", style = MaterialTheme.typography.headlineSmall)
        }
        if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("library_error")) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Button(onClick = onCreate, enabled = !state.busy, modifier = Modifier.testTag("library_create")) { Text("新しい所定経路を作成") }
                if (state.current != null) {
                    OutlinedButton(onClick = { onSaveCurrent(activeId == null) }, enabled = !state.busy,
                        modifier = Modifier.testTag("library_save_current")) { Text(if (activeId == null) "所定経路として保存" else "上書き保存") }
                    if (activeId != null) TextButton(onClick = { onSaveCurrent(true) }, enabled = !state.busy) { Text("別名で保存") }
                }
                if (activeId != null) TextButton(onClick = onEndUse, enabled = !state.busy) { Text("経路の使用を終了") }
            }
            if (!state.loading && state.routes.isEmpty()) item {
                Text("所定経路が登録されていません", Modifier.testTag("library_empty"))
            }
            items(state.routes, key = { it.id }) { route ->
                LibraryCard(route, route.id == activeId, state.busy,
                    { onOpen(route.id) }, { onNavigate(route.id) }, { onEdit(route.id) }, { onDuplicate(route.id) },
                    { renameId = route.id; renameName = route.name }, { deleteId = route.id })
            }
        }
    }
    if (renameId != null) RouteNameDialog("名前変更", renameName, "", state.busy, state.error,
        onDismiss = { renameId = null }, onSave = { name, _ ->
            if (name.isNotBlank()) { onRename(requireNotNull(renameId), name); renameId = null }
        })
    if (deleteId != null) AlertDialog(onDismissRequest = { deleteId = null },
        title = { Text("この所定経路を削除しますか？") },
        text = { Text(state.routes.firstOrNull { it.id == deleteId }?.name.orEmpty()) },
        confirmButton = { TextButton(onClick = { onDelete(requireNotNull(deleteId)); deleteId = null },
            enabled = !state.busy && deleteId != activeId, modifier = Modifier.testTag("library_delete_confirm")) { Text("削除") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("キャンセル") } })
}

@Composable private fun LibraryCard(route: PrescribedRouteSummary, active: Boolean, busy: Boolean,
    open: () -> Unit, navigate: () -> Unit, edit: () -> Unit, duplicate: () -> Unit, rename: () -> Unit, delete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("library_route_" + route.id)) {
        Column(Modifier.padding(12.dp)) {
            Text(route.name, style = MaterialTheme.typography.titleMedium)
            if (active) Text("現在使用中", color = MaterialTheme.colorScheme.primary)
            if (route.schemaVersion != 1) Text("未対応の保存形式")
            route.description?.let { Text(it) }
            Text(route.distanceMeters?.let { String.format(Locale.JAPAN, "%.1f km", it / 1000) } ?: "距離情報なし")
            Text(listOfNotNull(route.startName, route.destinationName).joinToString(" → "))
            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(route.updatedAtEpochMillis)))
            Row {
                TextButton(onClick = open, enabled = !busy) { Text("開く") }
                TextButton(onClick = edit, enabled = !busy) { Text("編集") }
                Box {
                    TextButton(onClick = { menu = true }, enabled = !busy) { Text("その他") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("ナビに使用") }, onClick = { menu = false; navigate() })
                        DropdownMenuItem(text = { Text("複製") }, onClick = { menu = false; duplicate() })
                        DropdownMenuItem(text = { Text("名前変更") }, onClick = { menu = false; rename() })
                        DropdownMenuItem(text = { Text(if (active) "使用中は削除できません" else "削除") },
                            enabled = !active, onClick = { menu = false; delete() })
                    }
                }
            }
        }
    }
}

@Composable fun RouteNameDialog(title: String, initialName: String, initialDescription: String, busy: Boolean,
    error: String?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) },
        text = { Column {
            OutlinedTextField(name, { name = it }, label = { Text("名前（必須）") },
                singleLine = true, modifier = Modifier.testTag("library_name"))
            if (title != "名前変更") OutlinedTextField(description, { description = it }, label = { Text("説明") })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = { onSave(name, description) }, enabled = name.isNotBlank() && !busy,
            modifier = Modifier.testTag("library_save_confirm")) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("キャンセル") } })
}