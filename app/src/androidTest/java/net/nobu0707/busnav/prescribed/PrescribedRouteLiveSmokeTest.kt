package net.nobu0707.busnav.prescribed

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.nobu0707.busnav.data.storage.prescribed.*
import net.nobu0707.busnav.data.routing.valhalla.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.prescribed.*
import net.nobu0707.busnav.domain.routeplan.*
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.test.LocalValhallaAssumptions
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PrescribedRouteLiveSmokeTest {
    @Test fun publicKantoRoutePersistsExactlyAfterReopenWithoutServer() = runBlocking {
        if (!LocalValhallaAssumptions.available()) return@runBlocking
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "isolated-live-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, PrescribedRouteDatabase::class.java, dbName).build()
        try {
            val plan = RoutePlan("public-kanto-plan", "公開地点の関東試験経路", listOf(
                RoutePlanPoint("s", RoutePlanPointType.START, GeoPoint(35.6812,139.7671), "東京駅付近"),
                RoutePlanPoint("d", RoutePlanPointType.DESTINATION, GeoPoint(35.7138,139.7773), "上野駅付近")))
            val engine = ValhallaRoutingEngine(RoutingConfig(LocalValhallaAssumptions.BASE_URL))
            val result = engine.calculateRoute((plan.toRoutingRequest() as RoutingRequestResult.Ready).request)
            assertTrue(result is RoutingResult.Success)
            val route = (result as RoutingResult.Success).route
            assertTrue(route.guidance!!.maneuvers.isNotEmpty())
            val record = PrescribedRouteRecord(UUID.randomUUID().toString(), plan.name!!, null, plan, route,
                VehicleProfile.DEVELOPMENT_LARGE_BUS, 1000, 1000)
            RoomPrescribedRouteRepository(db).save(record)
            db.close()
            db = Room.databaseBuilder(context, PrescribedRouteDatabase::class.java, dbName).build()
            // No RoutingEngine is supplied to the load path; the original engine is never called again.
            val repo = RoomPrescribedRouteRepository(db)
            assertEquals(record, (repo.getById(record.id) as PrescribedRouteLoad.Found).record)
            assertEquals(record.id, repo.observeAll().first().single().id)
            repo.rename(record.id, "公開経路の改名")
            assertEquals(route, (repo.getById(record.id) as PrescribedRouteLoad.Found).record.route)
            repo.delete(record.id)
            assertTrue(repo.observeAll().first().isEmpty())
        } finally { db.close(); context.deleteDatabase(dbName) }
    }
}
