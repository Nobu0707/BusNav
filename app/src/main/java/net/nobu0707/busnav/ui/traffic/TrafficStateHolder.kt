package net.nobu0707.busnav.ui.traffic

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.nobu0707.busnav.domain.traffic.*
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.ui.navigation.NavigationStateHolder

data class TrafficUiState(
    val snapshot: TrafficSnapshot = TrafficSnapshot(NoOpTrafficInformationProvider().source, TrafficProviderStatus.NOT_CONFIGURED),
    val status: TrafficProviderStatus = TrafficProviderStatus.NOT_CONFIGURED,
    val impacts: List<TrafficRouteImpact> = emptyList(),
    val activeEvents: List<TrafficEvent> = emptyList(),
    val futureEvents: List<TrafficEvent> = emptyList(),
    val ageMillis: Long? = null,
    val highwayWarning: TrafficRouteImpact? = null,
    val route: ScheduledRoute? = null,
) {
    val alert: TrafficRouteImpact? get() = impacts.firstOrNull { it.position == TrafficImpactPosition.AHEAD }
        ?: impacts.firstOrNull { it.position == TrafficImpactPosition.CURRENT }
        ?: impacts.firstOrNull { it.position == TrafficImpactPosition.AMBIGUOUS && it.level != TrafficImpactLevel.NONE }
    val statusText: String get() = when (status) {
        TrafficProviderStatus.NOT_CONFIGURED -> "交通情報サービス未接続"
        TrafficProviderStatus.AVAILABLE -> snapshot.source.displayName
        TrafficProviderStatus.STALE -> "交通情報が古くなっています"
        TrafficProviderStatus.UNAVAILABLE -> "交通情報を取得できません（解除は未確認）"
        TrafficProviderStatus.ERROR -> "交通情報の取得エラー（解除は未確認）"
    }
}

/** Foreground-only observation and local validity deadlines. Never polls or invokes RoutingEngine. */
class TrafficStateHolder(
    val provider: TrafficInformationProvider,
    private val navigation: NavigationStateHolder,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val config: TrafficConfig = TrafficConfig(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val diagnostics: (String) -> Unit = {},
) {
    private val _state = MutableStateFlow(TrafficUiState())
    val state = _state.asStateFlow()
    private val snapshots = MutableStateFlow(_state.value.snapshot)
    private val clock = MutableStateFlow(now())
    private var job: Job? = null
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            launch {
                provider.observeTraffic().catch { error ->
                    if (error is CancellationException) throw error
                    emit(TrafficSnapshot(provider.source, TrafficProviderStatus.ERROR))
                }.collect { snapshots.value = it.reconcile(snapshots.value) }
            }
            launch {
                snapshots.collectLatest { snapshot ->
                  while (isActive) {
                    clock.value = now()
                    val deadlines = snapshot.events.flatMap { listOfNotNull(it.validFromEpochMillis, it.validUntilEpochMillis) } +
                        listOfNotNull((snapshot.dataUpdatedAtEpochMillis ?: snapshot.receivedAtEpochMillis)?.plus(config.staleAfterMillis))
                    delay((deadlines.filter { it > now() }.minOrNull()?.minus(now()) ?: 30_000).coerceIn(1, 30_000))
                  }
                }
            }
            launch {
                var cachedRoute: ScheduledRoute? = null
                var cachedEvents: List<TrafficEvent>? = null
                var analyzer: TrafficRouteImpactAnalyzer? = null
                var geometryImpacts = emptyList<TrafficRouteImpact>()
                combine(snapshots, navigation.uiState, clock) { snapshot, nav, _ -> snapshot to nav }.collectLatest { (snapshot, nav) ->
                    val time = now()
                    val active = snapshot.events.filter { it.validity(time) == TrafficValidity.ACTIVE }
                    if (cachedRoute !== nav.activeRoute || cachedEvents != active) {
                        val prepared = withContext(dispatcher) {
                            val matcher = nav.activeRoute?.let { TrafficRouteImpactAnalyzer(it, config) }
                            matcher to active.mapNotNull { matcher?.match(it) }
                        }
                        analyzer = prepared.first; geometryImpacts = prepared.second
                        cachedRoute = nav.activeRoute; cachedEvents = active
                    }
                    val impacts = geometryImpacts.map { analyzer!!.position(it, nav.trafficProgressMeters) }
                        .sortedWith(TrafficRouteImpactAnalyzer.impactOrder)
                    val next = TrafficUiState(snapshot, snapshot.effectiveStatus(time, config), impacts, active,
                        snapshot.events.filter { it.validity(time) == TrafficValidity.FUTURE }, snapshot.ageMillis(time), impacts.firstOrNull { impact ->
                            val decision = nav.trafficHighwayDecisionProgressMeters
                            decision != null && impact.highwayDecisionLabel != null && impact.startProgressMeters != null && impact.endProgressMeters != null &&
                                decision in (impact.startProgressMeters - 200)..(impact.endProgressMeters + 200) }, nav.activeRoute)
                    if (_state.value.status != next.status) diagnostics("traffic.provider." + next.status.name.lowercase())
                    val priorBlocking = _state.value.impacts.filter { it.level == TrafficImpactLevel.BLOCKING }.map { it.event.id }.toSet()
                    if (impacts.any { it.level == TrafficImpactLevel.BLOCKING && it.event.id !in priorBlocking }) diagnostics("traffic.event.route_blocking")
                    if (_state.value.activeEvents.any { it.validity(time) == TrafficValidity.EXPIRED }) diagnostics("traffic.event.expired")
                    _state.value = next
                }
            }
        }
    }
    fun stop() { job?.cancel(); job = null }
    fun currentSnapshot() = snapshots.value
}
