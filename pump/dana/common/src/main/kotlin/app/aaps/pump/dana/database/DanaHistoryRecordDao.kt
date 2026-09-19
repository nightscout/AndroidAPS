package app.aaps.pump.dana.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class DanaHistoryRecordDao {

    @Query("SELECT * from $TABLE_DANA_HISTORY WHERE timestamp >= :timestamp AND code = :type ORDER BY timestamp DESC")
    abstract suspend fun allFromByType(timestamp: Long, type: Byte): List<DanaHistoryRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun createOrUpdate(danaHistoryRecord: DanaHistoryRecord)
}
