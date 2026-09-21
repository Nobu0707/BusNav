package net.nobu0707.busnav.data.storage.prescribed

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.nobu0707.busnav.domain.prescribed.PrescribedRouteSummary

@Entity(tableName = "prescribed_routes")
data class PrescribedRouteEntity(
    @PrimaryKey val id: String,
    val name: String, val description: String?,
    val createdAtEpochMillis: Long, val updatedAtEpochMillis: Long,
    val schemaVersion: Int, val payloadJson: String,
    val distanceMeters: Double?, val startName: String?, val destinationName: String?,
)

@Dao interface PrescribedRouteDao {
    @Query("SELECT id, name, description, distanceMeters, startName, destinationName, updatedAtEpochMillis, schemaVersion FROM prescribed_routes ORDER BY updatedAtEpochMillis DESC, id ASC")
    fun observeAll(): Flow<List<PrescribedRouteSummary>>
    @Query("SELECT * FROM prescribed_routes WHERE id = :id")
    suspend fun get(id: String): PrescribedRouteEntity?
    @Upsert suspend fun put(entity: PrescribedRouteEntity)
    @Query("DELETE FROM prescribed_routes WHERE id = :id")
    suspend fun delete(id: String)
    @Query("UPDATE prescribed_routes SET name = :name, updatedAtEpochMillis = :updated WHERE id = :id")
    suspend fun rename(id: String, name: String, updated: Long)
}

@Database(entities = [PrescribedRouteEntity::class], version = 1, exportSchema = true)
abstract class PrescribedRouteDatabase : RoomDatabase() {
    abstract fun routes(): PrescribedRouteDao
}