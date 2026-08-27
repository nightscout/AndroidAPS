package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.TABLE_TOTAL_DAILY_DOSES
import app.aaps.database.entities.TotalDailyDose
import app.aaps.database.entities.embedments.InterfaceIDs

@Dao
internal interface TotalDailyDoseDao : TraceableDao<TotalDailyDose> {

    @Query("SELECT * FROM $TABLE_TOTAL_DAILY_DOSES WHERE id = :id")
    override fun findById(id: Long): TotalDailyDose?

    @Query("DELETE FROM $TABLE_TOTAL_DAILY_DOSES")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_TOTAL_DAILY_DOSES WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_TOTAL_DAILY_DOSES WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT * FROM $TABLE_TOTAL_DAILY_DOSES WHERE (pumpId = :pumpId) AND (pumpType = :pumpType) AND (pumpSerial = :pumpSerial) AND (referenceId IS NULL)")
    suspend fun findByPumpIds(pumpId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): TotalDailyDose?

    @Query("SELECT * FROM $TABLE_TOTAL_DAILY_DOSES WHERE (timestamp = :timestamp) AND (pumpType = :pumpType) AND (pumpSerial = :pumpSerial) AND (referenceId IS NULL)")
    suspend fun findByPumpTimestamp(timestamp: Long, pumpType: InterfaceIDs.PumpType?, pumpSerial: String?): TotalDailyDose?

    @Query("SELECT * FROM $TABLE_TOTAL_DAILY_DOSES WHERE (timestamp = :timestamp) AND (pumpType = :pumpType) AND (referenceId IS NULL) ORDER BY id DESC")
    suspend fun findByPumpTimestamp(timestamp: Long, pumpType: InterfaceIDs.PumpType?): TotalDailyDose?

    @Query("SELECT * FROM $TABLE_TOTAL_DAILY_DOSES WHERE (timestamp = :timestamp) AND (pumpType = :pumpType) AND (referenceId IS NULL)")
    suspend fun findByTimestamp(timestamp: Long, pumpType: InterfaceIDs.PumpType): TotalDailyDose?

    @Query("SELECT * FROM $TABLE_TOTAL_DAILY_DOSES WHERE isValid = 1 AND referenceId IS NULL AND pumpType <> :exclude ORDER BY timestamp DESC LIMIT :count")
    suspend fun getLastTotalDailyDoses(count: Int, exclude: InterfaceIDs.PumpType = InterfaceIDs.PumpType.CACHE): List<TotalDailyDose>

    @Query("SELECT * FROM $TABLE_TOTAL_DAILY_DOSES WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<TotalDailyDose>

    @Query("DELETE FROM $TABLE_TOTAL_DAILY_DOSES WHERE timestamp >= :since AND pumpType = :pumpType")
    suspend fun deleteNewerThan(since: Long, pumpType: InterfaceIDs.PumpType)
}
