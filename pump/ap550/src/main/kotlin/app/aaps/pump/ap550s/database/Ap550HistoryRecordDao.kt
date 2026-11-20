package app.aaps.pump.ap550s.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class Ap550HistoryRecordDao {

    @Query("SELECT * from $TABLE_AP550_HISTORY WHERE timestamp >= :timestamp AND code = :type ORDER BY timestamp DESC")
    abstract suspend fun allFromByType(timestamp: Long, type: Byte): List<Ap550HistoryRecord>

    @Query("SELECT * from $TABLE_AP550_HISTORY WHERE timestamp > :timestamp AND code = :type ORDER BY timestamp DESC")
    abstract suspend fun allFromByTypeAfter(timestamp: Long, type: Byte): List<Ap550HistoryRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun createOrUpdate(ap550HistoryRecord: Ap550HistoryRecord)
}
