package net.nobu0707.busnav.ui.traffic

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.traffic.*

enum class TrafficFixtureScenario { CLEAR, CLOSURE, ENTRANCE, ACCIDENT, ROADWORK, CONGESTION, FUTURE, EXPIRED, PARALLEL, MIXED }

/** Synthetic public Kanto coordinates; no live feed, personal address, or location-derived data. */
class DebugFixtureTrafficInformationProvider(private val now: () -> Long = System::currentTimeMillis) : TrafficInformationProvider {
    override val source get() = stream.value.source
    private val fixtureSource = TrafficSourceInfo("debug-fixture", "開発用交通情報", false, "BusNav 合成データ")
    private val none = NoOpTrafficInformationProvider().source
    private val stream = MutableStateFlow(TrafficSnapshot(none, TrafficProviderStatus.NOT_CONFIGURED))
    var scenario = TrafficFixtureScenario.CLEAR
        private set
    override fun observeTraffic() = stream.asStateFlow()
    override suspend fun refresh(): TrafficRefreshResult { if (source == fixtureSource) select(scenario); return TrafficRefreshResult(stream.value.status) }
    fun disable() { stream.value = TrafficSnapshot(none, TrafficProviderStatus.NOT_CONFIGURED) }
    fun select(value: TrafficFixtureScenario) {
        scenario = value
        val time = now()
        fun point(m: Double, offset: Double = 0.0) = GeoPoint(35.68 + offset / 111195.0, 139.76 + m / 90300.0)
        fun event(id: String, kind: TrafficEventKind, start: Double, end: Double, offset: Double = 0.0) = TrafficEvent(
            id, kind, TrafficSeverity.WARNING, when (kind) {
                TrafficEventKind.ROAD_CLOSURE -> "× 通行止め（合成）"
                TrafficEventKind.ENTRY_CLOSURE -> "× 入口閉鎖（合成）"
                TrafficEventKind.ACCIDENT -> "! 事故（合成）"
                TrafficEventKind.ROADWORK -> "工 工事（合成）"
                else -> "≋ 渋滞（合成）"
            }, TrafficGeometry.Polyline(listOf(point(start, offset), point(end, offset))), fixtureSource,
            roadName = if (offset == 0.0) "合成高速道路" else "合成並行一般道", direction = TrafficDirection.FORWARD,
            validUntilEpochMillis = time + 3_600_000, updatedAtEpochMillis = time)
        val closure = event("closure", TrafficEventKind.ROAD_CLOSURE, 1500.0, 2400.0)
        val events = when (value) {
            TrafficFixtureScenario.CLEAR -> emptyList()
            TrafficFixtureScenario.CLOSURE -> listOf(closure)
            TrafficFixtureScenario.ENTRANCE -> listOf(event("entry", TrafficEventKind.ENTRY_CLOSURE, 3000.0, 3100.0))
            TrafficFixtureScenario.ACCIDENT -> listOf(event("accident", TrafficEventKind.ACCIDENT, 3000.0, 3150.0))
            TrafficFixtureScenario.ROADWORK -> listOf(event("work", TrafficEventKind.ROADWORK, 3500.0, 3900.0))
            TrafficFixtureScenario.CONGESTION -> listOf(event("queue", TrafficEventKind.CONGESTION, 4000.0, 5500.0))
            TrafficFixtureScenario.FUTURE -> listOf(closure.copy(id = "future", validFromEpochMillis = time + 600_000))
            TrafficFixtureScenario.EXPIRED -> listOf(closure.copy(id = "expired", validUntilEpochMillis = time - 1))
            TrafficFixtureScenario.PARALLEL -> listOf(event("parallel", TrafficEventKind.ROAD_CLOSURE, 1500.0, 2400.0, 22.0))
            TrafficFixtureScenario.MIXED -> listOf(closure,
                event("entry", TrafficEventKind.ENTRY_CLOSURE, 3000.0, 3100.0),
                event("accident", TrafficEventKind.ACCIDENT, 3200.0, 3300.0).copy(geometry = TrafficGeometry.Point(point(3200.0)), bearingDegrees = 90.0),
                event("work", TrafficEventKind.ROADWORK, 3500.0, 3900.0),
                event("queue", TrafficEventKind.CONGESTION, 4000.0, 5500.0),
                closure.copy(id = "future", validFromEpochMillis = time + 600_000),
                closure.copy(id = "expired", validUntilEpochMillis = time - 1),
                event("parallel", TrafficEventKind.ROAD_CLOSURE, 1500.0, 2400.0, 22.0),
                event("weather", TrafficEventKind.WEATHER_HAZARD, 6000.0, 6200.0).copy(title = "! 気象影響（合成）",
                    geometry = TrafficGeometry.Polygon(listOf(point(6000.0, -100.0), point(6500.0, -100.0), point(6500.0, 100.0), point(6000.0, 100.0)))))
        }
        stream.value = TrafficSnapshot(fixtureSource, TrafficProviderStatus.AVAILABLE, events, time, time)
    }
}
fun createTrafficProvider(): TrafficInformationProvider = DebugFixtureTrafficInformationProvider()
@Composable fun TrafficDeveloperControls(provider: TrafficInformationProvider) {
    val fixture = provider as? DebugFixtureTrafficInformationProvider ?: return
    val snapshot by fixture.observeTraffic().collectAsState()
    Column {
        Text("Developer tools • Traffic source")
        Row {
            TextButton(onClick = fixture::disable, modifier = Modifier.testTag("traffic_source_none")) { Text("None") }
            TextButton(onClick = { fixture.select(fixture.scenario) }, modifier = Modifier.testTag("traffic_source_fixture")) { Text("Debug fixture") }
        }
        if (snapshot.status != TrafficProviderStatus.NOT_CONFIGURED) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.testTag("traffic_scenario")) { Text("Fixture: ${fixture.scenario}") }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    TrafficFixtureScenario.entries.forEach { scenario -> DropdownMenuItem(text = { Text(scenario.name) },
                        onClick = { fixture.select(scenario); expanded = false }, modifier = Modifier.testTag("traffic_fixture_${scenario.name}")) }
                }
            }
        }
    }
}
