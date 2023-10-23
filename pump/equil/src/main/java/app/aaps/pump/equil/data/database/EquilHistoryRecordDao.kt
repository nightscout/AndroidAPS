package app.aaps.pump.equil.data.database

import androidx.room.*
import io.reactivex.rxjava3.core.Single

@Dao
abstract class EquilHistoryRecordDao {

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
    abstract fun insert(danaHistoryRecord: EquilHistoryPump): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(equilHistoryrecord: EquilHistoryRecord): Long

    @Update
    abstract fun update(equilHistoryrecord: EquilHistoryRecord): Int

    @Query("SELECT * FROM $TABLE_EQUIL_HISTORY_RECORD WHERE id = :id")
    abstract fun getEquilHistoryRecordById(id: Long): EquilHistoryRecord?

    @Transaction
    @Query("UPDATE $TABLE_EQUIL_HISTORY_RECORD SET resolvedStatus = :resolvedResult, resolvedAt = :resolvedAt WHERE id = :id ")
    abstract fun markResolved(id: Long, resolvedResult: ResolvedResult, resolvedAt: Long)

    @Query("SELECT * from $TABLE_EQUIL_HISTORY_RECORD WHERE type=:eventType ORDER BY timestamp DESC LIMIT 0,1")
    abstract fun lastRecord(eventType: EquilHistoryRecord.EventType): EquilHistoryRecord

    @Query("SELECT * from $TABLE_EQUIL_HISTORY_RECORD WHERE  timestamp >= :startTime and timestamp<=:endTime ORDER BY timestamp DESC")
    abstract fun allSince(startTime: Long, endTime: Long): Single<List<EquilHistoryRecord>>

}
