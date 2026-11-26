package app.aaps.pump.aaruy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Suspend functions only: a multiplatform database allows nothing else outside Android. */
@Dao
interface HistoryUnitInfoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(historyUnitInfo: HistoryUnitInfo)

    /** The pump sends its last entry again at each read, and `reserved` is what tells two entries of one second apart. */
    @Query("SELECT COUNT(*) FROM history_unit_info WHERE time=:time AND type=:type AND reserved=:reserved")
    suspend fun count(time: Long, type: Int, reserved: Int): Int

    /** For the history screen, newest first. */
    @Query("SELECT * FROM history_unit_info WHERE time>=:since ORDER BY time DESC")
    suspend fun querySince(since: Long): List<HistoryUnitInfo>

    /** Rows get a higher id as they are stored, so a row with a higher id was stored later. 0 for an empty table. */
    @Query("SELECT COALESCE(MAX(id), 0) FROM history_unit_info")
    suspend fun maxId(): Int

    /** The newest entry of this type, as the last bolus after an app restart. */
    @Query("SELECT * FROM history_unit_info WHERE type=:type ORDER BY time DESC LIMIT 1")
    suspend fun newest(type: Int): HistoryUnitInfo?

    /** The entries not sent to the vendor server yet, oldest first. See `AaruyHistoryUpload`. */
    @Query("SELECT * FROM history_unit_info WHERE isUploaded = 0 ORDER BY id LIMIT :limit")
    suspend fun notUploaded(limit: Int): List<HistoryUnitInfo>

    @Query("UPDATE history_unit_info SET isUploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Int)

    @Query("DELETE FROM history_unit_info")
    suspend fun deleteAll()
}
