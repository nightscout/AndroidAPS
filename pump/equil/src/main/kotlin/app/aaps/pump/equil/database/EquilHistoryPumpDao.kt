package app.aaps.pump.equil.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class EquilHistoryPumpDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(equilHistoryRecord: EquilHistoryPump): Long

    @Query("SELECT * from $TABLE_EQUIL_HISTORY_PUMP WHERE serialNumber=:serialNumber and eventTimestamp >= :startTime and eventTimestamp<=:endTime ORDER BY eventTimestamp DESC")
    abstract suspend fun allFromByType(startTime: Long, endTime: Long, serialNumber: String): List<EquilHistoryPump>
}
