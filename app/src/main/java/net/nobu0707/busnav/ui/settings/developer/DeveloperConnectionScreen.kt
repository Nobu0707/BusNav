package net.nobu0707.busnav.ui.settings.developer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.nobu0707.busnav.BuildConfig
import net.nobu0707.busnav.developer.*
import net.nobu0707.busnav.map.basemap.BasemapRegion

@Composable
fun DeveloperConnectionScreen(
    repository: DeveloperConnectionRepository,
    onBack: () -> Unit,
    checkConnection: (suspend (String, ConnectionService) -> ConnectionResult)? = null,
) {
    if (!BuildConfig.DEBUG) return
    val scope = rememberCoroutineScope()
    val checker = remember { ConnectionChecker() }
    val check = checkConnection ?: checker::check
    var valhalla by rememberSaveable { mutableStateOf("") }
    var basemap by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf(BasemapRegion.KANTO) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var valhallaStatus by remember { mutableStateOf<String?>(null) }
    var basemapStatus by remember { mutableStateOf<String?>(null) }
    var valhallaChecking by remember { mutableStateOf(false) }
    var basemapChecking by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val valhallaError = if (attempted) validationError(valhalla) else null
    val basemapError = if (attempted) validationError(basemap) else null

    LaunchedEffect(repository) {
        if (!loaded) {
            try {
                val settings = repository.settings.first()
                valhalla = settings.valhallaBaseUrl
                basemap = settings.basemapBaseUrl
                region = settings.basemapRegion
                loaded = true
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { snackbar.showSnackbar("設定を読み込めませんでした。画面を開き直してください") }
        }
    }
    BackHandler { onBack() }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp).testTag("developer_connections"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("戻る") }
            Text("開発接続設定", style = MaterialTheme.typography.headlineSmall)
            Text("開発用設定。実運行では使用しないでください", style = MaterialTheme.typography.bodySmall)
            Text("エミュレータ: 10.0.2.2\n実機: 開発PCのLAN IPを指定（同一LAN / Wi-Fi）", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = valhalla, onValueChange = { valhalla = it; valhallaStatus = null },
                label = { Text("Valhallaサーバー") }, singleLine = true,
                enabled = loaded && !busy && !valhallaChecking, isError = valhallaError != null,
                supportingText = { valhallaError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth().testTag("valhalla_url"))
            OutlinedButton(onClick = {
                valhallaChecking = true
                scope.launch {
                    try { valhallaStatus = check(valhalla, ConnectionService.VALHALLA).message() }
                    finally { valhallaChecking = false }
                }
            }, enabled = loaded && !busy && !valhallaChecking, modifier = Modifier.testTag("valhalla_check")) {
                Text(if (valhallaChecking) "確認中…" else "Valhalla接続テスト")
            }
            valhallaStatus?.let { Text(it, Modifier.testTag("valhalla_status")) }
            OutlinedTextField(value = basemap, onValueChange = { basemap = it; basemapStatus = null },
                label = { Text("地図タイルサーバー") }, singleLine = true,
                enabled = loaded && !busy && !basemapChecking, isError = basemapError != null,
                supportingText = { basemapError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth().testTag("basemap_url"))
            Text("地図地域 / Basemap region")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BasemapRegion.entries.forEach { choice ->
                    FilterChip(selected = region == choice,
                        onClick = { region = choice; basemapStatus = null },
                        enabled = loaded && !busy && !basemapChecking,
                        label = { Text(choice.label) },
                        modifier = Modifier.testTag("basemap_region_" + choice.id))
                }
            }
            OutlinedButton(onClick = {
                basemapChecking = true
                scope.launch {
                    try {
                        val service = if (region == BasemapRegion.KANTO) ConnectionService.KANTO else ConnectionService.CHUBU
                        val result = check(basemap, service)
                        basemapStatus = result.message() + if (result.httpCode == 404) "：選択した地域の地図データがありません" else ""
                    }
                    finally { basemapChecking = false }
                }
            }, enabled = loaded && !busy && !basemapChecking, modifier = Modifier.testTag("basemap_check")) {
                Text(if (basemapChecking) "確認中…" else "地図接続テスト")
            }
            basemapStatus?.let { Text(it, Modifier.testTag("basemap_status")) }
            Button(onClick = {
                attempted = true
                if (validationError(valhalla) == null && validationError(basemap) == null) {
                    busy = true
                    scope.launch {
                        try {
                            val valid = DeveloperConnectionSettings(valhalla, basemap, region).normalized()
                            repository.update(valid)
                            valhalla = valid.valhallaBaseUrl; basemap = valid.basemapBaseUrl
                            snackbar.showSnackbar("保存しました")
                        } catch (e: CancellationException) { throw e
                        } catch (_: Exception) { snackbar.showSnackbar("保存できませんでした")
                        } finally { busy = false }
                    }
                }
            }, enabled = loaded && !busy && !valhallaChecking && !basemapChecking, modifier = Modifier.testTag("connections_save")) { Text("保存") }
            OutlinedButton(onClick = {
                busy = true
                scope.launch {
                    try {
                        repository.reset()
                        val defaults = repository.settings.first()
                        valhalla = defaults.valhallaBaseUrl; basemap = defaults.basemapBaseUrl
                        region = defaults.basemapRegion
                        attempted = false; valhallaStatus = null; basemapStatus = null
                        snackbar.showSnackbar("デフォルトに戻しました")
                    } catch (e: CancellationException) { throw e
                    } catch (_: Exception) { snackbar.showSnackbar("設定を戻せませんでした")
                    } finally { busy = false }
                }
            }, enabled = loaded && !busy && !valhallaChecking && !basemapChecking, modifier = Modifier.testTag("connections_reset")) { Text("デフォルトに戻す") }
        }
    }
}

private fun validationError(value: String): String? =
    runCatching { normalizeBaseUrl(value) }.exceptionOrNull()?.message
