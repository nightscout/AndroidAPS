package app.aaps.pump.equil.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.rxjava3.core.Single

@Dao
abstract class EquilHistoryPumpDao {

    @Query("SELECT * from $TABLE_EQUIL_HISTORY_PUMP WHERE timestamp >= :timestamp ORDER BY eventTimestamp DESC")
    abstract fun allFromByType(timestamp: Long): Single<List<EquilHistoryPump>>

    @Query("SELECT * from $TABLE_EQUIL_HISTORY_PUMP WHERE serialNumber=:serialNumber and eventTimestamp >= :startTime and eventTimestamp<=:endTime ORDER BY eventTimestamp DESC")
    abstract fun allFromByType(startTime: Long, endTime: Long, serialNumber: String): Single<List<EquilHistoryPump>>

    @Query("SELECT * from $TABLE_EQUIL_HISTORY_PUMP WHERE eventTimestamp = :timestamp AND eventIndex = :eventIndex LIMIT 0,1")
    abstract fun findByIndexAndEventTime(timestamp: Long, eventIndex: Int): EquilHistoryPump

    @Query("SELECT * from $TABLE_EQUIL_HISTORY_PUMP WHERE eventIndex = :eventIndex LIMIT 0,1")
    abstract fun findByEventIndex(eventIndex: Int): EquilHistoryPump

    @Query("SELECT * from $TABLE_EQUIL_HISTORY_PUMP WHERE serialNumber=:serialNumber ORDER BY eventTimestamp DESC LIMIT 0,1")
    abstract fun last(serialNumber: String): EquilHistoryPump

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun createOrUpdate(danaHistoryRecord: EquilHistoryPump)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(equilHistoryRecord: EquilHistoryPump): Long
}
