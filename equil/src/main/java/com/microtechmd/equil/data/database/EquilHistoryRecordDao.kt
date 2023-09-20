package com.microtechmd.equil.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.rxjava3.core.Single

@Dao
abstract class EquilHistoryRecordDao {

    @Query("SELECT * from $TABLE_DANA_HISTORY_PUMP WHERE timestamp >= :timestamp ORDER BY eventTimestamp DESC")
    abstract fun allFromByType(timestamp: Long): Single<List<EquilHistoryPump>>

    @Query("SELECT * from $TABLE_DANA_HISTORY_PUMP WHERE eventTimestamp >= :startTime and eventTimestamp<=:endTime ORDER BY eventTimestamp DESC")
    abstract fun allFromByType(startTime: Long, endTime: Long): Single<List<EquilHistoryPump>>

    @Query("SELECT * from $TABLE_DANA_HISTORY_PUMP WHERE eventTimestamp = :timestamp AND eventIndex = :eventIndex LIMIT 0,1")
    abstract fun findByIndexAndEventTime(timestamp: Long, eventIndex: Int): EquilHistoryPump

    @Query("SELECT * from $TABLE_DANA_HISTORY_PUMP WHERE eventIndex = :eventIndex LIMIT 0,1")
    abstract fun findByEventIndex(eventIndex: Int): EquilHistoryPump

    @Query("SELECT * from $TABLE_DANA_HISTORY_PUMP WHERE 1=1 ORDER BY eventTimestamp DESC LIMIT 0,1")
    abstract fun last(): EquilHistoryPump

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun createOrUpdate(danaHistoryRecord: EquilHistoryPump)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(danaHistoryRecord: EquilHistoryPump): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(danaHistoryRecord: EquilHistoryRecord): Long

    @Query("SELECT * from $TABLE_DANA_HISTORY_RECORD WHERE type=:eventType ORDER BY eventTimestamp DESC LIMIT 0,1")
    abstract fun lastRecord(eventType: EquilHistoryRecord.EventType): EquilHistoryRecord

    @Query("SELECT * from $TABLE_DANA_HISTORY_RECORD WHERE timestamp <= :timestamp ORDER BY timestamp DESC")
    abstract fun allSince(timestamp: Long): Single<List<EquilHistoryRecord>>
}
