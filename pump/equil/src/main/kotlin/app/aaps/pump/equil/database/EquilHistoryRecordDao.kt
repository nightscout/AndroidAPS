package app.aaps.pump.equil.database

import androidx.room.*
import io.reactivex.rxjava3.core.Single

@Dao
abstract class EquilHistoryRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(equilHistoryRecord: EquilHistoryRecord): Long

    @Update
    abstract fun update(equilHistoryRecord: EquilHistoryRecord): Int

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
