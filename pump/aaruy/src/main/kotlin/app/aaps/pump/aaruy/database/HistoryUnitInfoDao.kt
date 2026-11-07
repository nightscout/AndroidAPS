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
}
