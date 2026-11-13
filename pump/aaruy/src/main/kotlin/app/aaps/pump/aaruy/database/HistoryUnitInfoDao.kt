package app.aaps.pump.aaruy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HistoryUnitInfoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(historyUnitInfo: HistoryUnitInfo)

    /** The pump sends its last entry again at each read, and `reserved` is what tells two entries of one second apart. */
    @Query("SELECT COUNT(*) FROM history_unit_info WHERE time=:time AND type=:type AND reserved=:reserved")
    fun count(time: Long, type: Int, reserved: Int): Int

    /** For the history screen, newest first. */
    @Query("SELECT * FROM history_unit_info WHERE time>=:since ORDER BY time DESC")
    fun querySince(since: Long): List<HistoryUnitInfo>

    /** Rows get a higher id as they are stored, so a row with a higher id was stored later. 0 for an empty table. */
    @Query("SELECT COALESCE(MAX(id), 0) FROM history_unit_info")
    fun maxId(): Int

    /** The newest entry of this type, as the last bolus after an app restart. */
    @Query("SELECT * FROM history_unit_info WHERE type=:type ORDER BY time DESC LIMIT 1")
    fun newest(type: Int): HistoryUnitInfo?
}
