package net.nobu0707.busnav.ui.prescribed

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.nobu0707.busnav.domain.prescribed.*
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.routeplan.RoutePlan
import net.nobu0707.busnav.domain.routing.VehicleProfile

data class PrescribedRouteLibraryUiState(
    val routes: List<PrescribedRouteSummary> = emptyList(),
    val loading: Boolean = true, val busy: Boolean = false, val error: String? = null,
    val draft: PrescribedRouteRecord? = null,
    val current: PrescribedRouteRecord? = null,
)

class PrescribedRouteLibraryStateHolder(
    private val repository: PrescribedRouteRepository,
    private val scope: CoroutineScope,
    private val activeId: () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val _state = MutableStateFlow(PrescribedRouteLibraryUiState())
    val state = _state.asStateFlow()
    init {
        scope.launch {
            repository.observeAll().catch { error ->
                if (error is CancellationException) throw error
                _state.update { it.copy(loading = false, error = "一覧を読み込めませんでした") }
            }.collect { routes -> _state.update { it.copy(routes = routes, loading = false) } }
        }
    }

    fun open(id: String, opened: (PrescribedRouteRecord) -> Unit) = action {
        val record = load(id)
        _state.update { it.copy(current = record, draft = null) }
        opened(record)
    }
    fun edit(id: String, editing: (PrescribedRouteRecord) -> Unit) = action {
        val record = load(id)
        _state.update { it.copy(draft = record) }
        editing(record)
    }
    fun cancelDraft() { _state.update { it.copy(draft = null, error = null) } }
    fun clearCurrent() { _state.update { it.copy(current = null, draft = null) } }
    fun acceptCandidate(plan: RoutePlan, route: ScheduledRoute, vehicle: VehicleProfile): Boolean {
        val draft = _state.value.draft
        val snapshot = (draft ?: PrescribedRouteRecord(newId(), route.name, null, plan, route, vehicle, now(), now()))
            .copy(routePlan = plan, route = route, vehicleProfile = vehicle)
        try { snapshot.validate() }
        catch (e: IllegalArgumentException) {
            _state.update { it.copy(error = e.message ?: "経路の内容を確認してください") }
            return false
        }
        _state.update { if (draft == null) it.copy(current = snapshot, error = null) else it.copy(draft = snapshot, error = null) }
        return true
    }
    fun canSaveDraft(plan: RoutePlan): Boolean = _state.value.draft?.routePlan == plan

    fun save(name: String, description: String?, asNew: Boolean, plan: RoutePlan?,
        saved: (PrescribedRouteRecord) -> Unit) = action {
        val source = _state.value.draft ?: _state.value.current ?: error("保存する経路がありません")
        check(_state.value.draft == null || source.routePlan == plan) { "地点が変更されています。経路を再計算して適用してください" }
        val exists = _state.value.draft != null || activeId() == source.id || _state.value.routes.any { it.id == source.id }
        val record = source.copy(id = if (asNew || !exists) newId() else source.id,
            name = name.trim(), description = description?.takeIf { it.isNotBlank() },
            createdAtEpochMillis = if (asNew || !exists) now() else source.createdAtEpochMillis,
            updatedAtEpochMillis = now())
        repository.save(record, existingOnly = !asNew && exists)
        _state.update { it.copy(current = record, draft = null) }
        saved(record)
    }
    fun rename(id: String, name: String) = action {
        repository.rename(id, name)
        _state.update { state -> state.copy(current = state.current?.let {
            if (it.id == id) it.copy(name = name.trim()) else it
        }) }
    }
    fun duplicate(id: String) = action {
        val source = load(id)
        repository.save(source.copy(id = newId(), name = source.name + "（コピー）",
            createdAtEpochMillis = now(), updatedAtEpochMillis = now()))
    }
    fun delete(id: String) = action {
        check(id != activeId()) { "現在使用中の経路は削除できません。使用を終了してください" }
        repository.delete(id)
    }
    fun dismissError() { _state.update { it.copy(error = null) } }
    private suspend fun load(id: String): PrescribedRouteRecord = when (val result = repository.getById(id)) {
        is PrescribedRouteLoad.Found -> result.record
        PrescribedRouteLoad.Missing -> error("この経路は削除されています")
        PrescribedRouteLoad.Corrupt -> error("読み込めない経路です。保存データは保持されています")
        PrescribedRouteLoad.Unsupported -> error("この経路は新しい保存形式です。アプリの更新が必要です")
    }
    private fun action(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = e.message ?: "操作に失敗しました") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
}