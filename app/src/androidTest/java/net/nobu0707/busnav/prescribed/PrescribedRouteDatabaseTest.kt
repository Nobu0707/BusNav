package net.nobu0707.busnav.prescribed

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.nobu0707.busnav.data.storage.prescribed.*
import net.nobu0707.busnav.domain.prescribed.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PrescribedRouteDatabaseTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun open(name: String) = Room.databaseBuilder(context, PrescribedRouteDatabase::class.java, name).build()
    @Test fun largeRouteSurvivesDatabaseRestartExactlyAndCanBeDeleted() = runBlocking {
        val name = "isolated-prescribed-${UUID.randomUUID()}.db"
        var db = open(name)
        try {
            val source = prescribedFixture()
            var repo = RoomPrescribedRouteRepository(db)
            val start = System.nanoTime()
            repo.save(source)
            val saveMs = (System.nanoTime() - start) / 1_000_000
            db.close(); db = open(name); repo = RoomPrescribedRouteRepository(db)
            val readStart = System.nanoTime()
            val loaded = repo.getById(source.id) as PrescribedRouteLoad.Found
            val loadMs = (System.nanoTime() - readStart) / 1_000_000
            assertEquals(source, loaded.record)
            val summary = repo.observeAll().first().single()
            assertEquals(source.route.metadata.distanceMeters, summary.distanceMeters)
            assertEquals(source.route.start.name, summary.startName)
            assertEquals(source.route.destination.name, summary.destinationName)
            android.util.Log.i("PrescribedRouteTest", "points=4001 maneuvers=100 saveMs=$saveMs loadMs=$loadMs payloadBytes=" +
                PrescribedRouteCodec.encode(source).toByteArray(Charsets.UTF_8).size)
            repo.delete(source.id)
            assertEquals(PrescribedRouteLoad.Missing, repo.getById(source.id))
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun renameUpdateSortDeleteAndSameNameIdentity() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PrescribedRouteDatabase::class.java).build()
        try {
            val repo = RoomPrescribedRouteRepository(db, now = { 9000 })
            val a = prescribedFixture("a", updated = 2000)
            val b = prescribedFixture("b", updated = 3000)
            repo.save(a); repo.save(b)
            assertEquals(listOf("b","a"), repo.observeAll().first().map { it.id })
            val before = db.routes().get("a")!!.payloadJson
            repo.rename("a", "renamed")
            assertEquals(before, db.routes().get("a")!!.payloadJson)
            assertEquals("renamed", (repo.getById("a") as PrescribedRouteLoad.Found).record.name)
            assertEquals(listOf("a","b"), repo.observeAll().first().map { it.id })
            repo.save(a.copy(description = "updated"), existingOnly = true)
            assertEquals("updated", (repo.getById("a") as PrescribedRouteLoad.Found).record.description)
            repo.delete("a")
            assertTrue(repo.getById("b") is PrescribedRouteLoad.Found)
        } finally { db.close() }
    }
    @Test fun malformedAndUnsupportedRecordsRemainInDatabaseWithoutCrash() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PrescribedRouteDatabase::class.java).build()
        try {
            val repo = RoomPrescribedRouteRepository(db)
            repo.save(prescribedFixture())
            val entity = db.routes().get("record")!!
            db.routes().put(entity.copy(payloadJson = "{bad"))
            assertEquals(PrescribedRouteLoad.Corrupt, repo.getById(entity.id))
            assertEquals(1, repo.observeAll().first().size)
            assertEquals("{bad", db.routes().get(entity.id)!!.payloadJson)
            db.routes().put(entity.copy(schemaVersion = 99))
            assertEquals(PrescribedRouteLoad.Unsupported, repo.getById(entity.id))
            assertEquals(99, db.routes().get(entity.id)!!.schemaVersion)
            db.routes().put(entity.copy(payloadJson = "{\"schemaVersion\":2,\"futureField\":true}"))
            assertEquals(PrescribedRouteLoad.Unsupported, repo.getById(entity.id))
        } finally { db.close() }
    }
    @Test fun concurrentRenameDeleteDoesNotResurrectAndStaleSaveFails() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PrescribedRouteDatabase::class.java).build()
        try {
            val repo = RoomPrescribedRouteRepository(db)
            val r = prescribedFixture()
            repo.save(r)
            coroutineScope {
                launch { repeat(10) { repo.rename(r.id, "rename-$it") } }
                launch { repo.delete(r.id) }
            }
            assertEquals(PrescribedRouteLoad.Missing, repo.getById(r.id))
            try { repo.save(r, existingOnly = true); fail("stale save") } catch (_: IllegalStateException) { }
            assertEquals(PrescribedRouteLoad.Missing, repo.getById(r.id))
        } finally { db.close() }
    }
}