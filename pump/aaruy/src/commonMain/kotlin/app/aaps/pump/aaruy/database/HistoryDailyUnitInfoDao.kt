package app.aaps.pump.aaruy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/** Suspend functions only: a multiplatform database allows nothing else outside Android. */
@Dao
interface HistoryDailyUnitInfoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(historyUnitInfo: HistoryDailyUnitInfo)

    @Update
    suspend fun update(historyUnitInfo: HistoryDailyUnitInfo)

    /** For the history screen, newest first. */
    @Query("SELECT * FROM history_daily_unit_info ORDER BY id DESC")
    suspend fun queryAll(): List<HistoryDailyUnitInfo>

    /** The pump sends today again and again while the day grows, so one day of one pump is one row. */
    @Query("SELECT * FROM history_daily_unit_info WHERE dateTimestamp = :date AND pumpName = :pumpName LIMIT 1")
    suspend fun find(date: Long, pumpName: String): HistoryDailyUnitInfo?

    @Query("DELETE FROM history_daily_unit_info")
    suspend fun deleteAll()
}
