package net.nobu0707.busnav.data.storage.prescribed

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.nobu0707.busnav.domain.prescribed.*

class RoomPrescribedRouteRepository(
    private val db: PrescribedRouteDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) : PrescribedRouteRepository {
    override fun observeAll() = db.routes().observeAll()
    override suspend fun getById(id: String): PrescribedRouteLoad = withContext(dispatcher) {
        val entity = db.routes().get(id) ?: return@withContext PrescribedRouteLoad.Missing
        if (entity.schemaVersion != 1) return@withContext PrescribedRouteLoad.Unsupported
        try {
            PrescribedRouteLoad.Found(PrescribedRouteCodec.record(entity.id, entity.name, entity.description,
                entity.createdAtEpochMillis, entity.updatedAtEpochMillis, PrescribedRouteCodec.decode(entity.payloadJson)))
        } catch (_: UnsupportedPrescribedRouteSchema) {
            PrescribedRouteLoad.Unsupported
        } catch (_: IllegalArgumentException) {
            PrescribedRouteLoad.Corrupt
        }
    }
    override suspend fun save(record: PrescribedRouteRecord, existingOnly: Boolean) = withContext(dispatcher) {
        record.validate()
        val entity = PrescribedRouteEntity(record.id, record.name.trim(), record.description, record.createdAtEpochMillis,
            record.updatedAtEpochMillis, record.schemaVersion, PrescribedRouteCodec.encode(record),
            record.route.metadata.distanceMeters, record.route.start.name, record.route.destination.name)
        db.withTransaction {
            if (existingOnly) check(db.routes().get(record.id) != null) { "この経路は削除されています" }
            db.routes().put(entity)
        }
    }
    override suspend fun delete(id: String) = withContext(dispatcher) { db.routes().delete(id) }
    // Record name is canonical. Historical plan/route names in the payload remain exact.
    override suspend fun rename(id: String, name: String) = withContext(dispatcher) {
        require(name.isNotBlank()) { "名前を入力してください" }
        db.routes().rename(id, name.trim(), now())
    }
}