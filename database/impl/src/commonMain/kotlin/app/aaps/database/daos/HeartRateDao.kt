package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.HeartRate
import app.aaps.database.entities.TABLE_HEART_RATE

@Dao
internal interface HeartRateDao : TraceableDao<HeartRate> {

    @Query("SELECT * FROM $TABLE_HEART_RATE WHERE id = :id")
    override fun findById(id: Long): HeartRate?

    @Query("DELETE FROM $TABLE_HEART_RATE")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_HEART_RATE WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_HEART_RATE WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT * FROM $TABLE_HEART_RATE WHERE timestamp >= :timestamp ORDER BY timestamp")
    suspend fun getFromTime(timestamp: Long): List<HeartRate>

    @Query("SELECT * FROM $TABLE_HEART_RATE WHERE timestamp BETWEEN :startMillis AND :endMillis ORDER BY timestamp")
    suspend fun getFromTimeToTime(startMillis: Long, endMillis: Long): List<HeartRate>

    @Query("SELECT * FROM $TABLE_HEART_RATE WHERE timestamp > :since AND timestamp <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<HeartRate>
}
