package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.StepsCount
import app.aaps.database.entities.TABLE_STEPS_COUNT

@Dao
internal interface StepsCountDao : TraceableDao<StepsCount> {

    @Query("SELECT * FROM $TABLE_STEPS_COUNT WHERE id = :id")
    override fun findById(id: Long): StepsCount?

    @Query("DELETE FROM $TABLE_STEPS_COUNT")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_STEPS_COUNT WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_STEPS_COUNT WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT * FROM $TABLE_STEPS_COUNT WHERE timestamp >= :timestamp ORDER BY timestamp")
    suspend fun getFromTime(timestamp: Long): List<StepsCount>

    @Query("SELECT * FROM $TABLE_STEPS_COUNT WHERE timestamp BETWEEN :startMillis AND :endMillis ORDER BY timestamp")
    suspend fun getFromTimeToTime(startMillis: Long, endMillis: Long): List<StepsCount>

    @Query("SELECT * FROM $TABLE_STEPS_COUNT WHERE timestamp > :since AND timestamp <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<StepsCount>

    @Query("SELECT * FROM $TABLE_STEPS_COUNT WHERE timestamp >= :timestamp ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastStepsCountFromTime(timestamp: Long): StepsCount?

    @Query("SELECT * FROM $TABLE_STEPS_COUNT WHERE timestamp BETWEEN :startMillis AND :endMillis ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastStepsCountFromTimeToTime(startMillis: Long, endMillis: Long): StepsCount?
}
