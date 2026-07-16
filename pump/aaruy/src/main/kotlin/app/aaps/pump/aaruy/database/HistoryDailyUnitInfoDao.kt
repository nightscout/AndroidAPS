package app.aaps.pump.aaruy.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface HistoryDailyUnitInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(historyUnitInfo: HistoryDailyUnitInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertList(list: MutableList<HistoryDailyUnitInfo>)

    @Delete
    fun delete(historyUnitInfo: HistoryDailyUnitInfo)

    @Query("DELETE FROM history_daily_unit_info")
    fun deleteAll()

    @Update
    fun update(historyUnitInfo: HistoryDailyUnitInfo)

    @Query("SELECT * FROM history_daily_unit_info ORDER BY id DESC")
    fun queryAll(): List<HistoryDailyUnitInfo>

    @Query("SELECT * FROM history_daily_unit_info WHERE isUploaded=:isUploaded ORDER BY id DESC")
    fun queryAllDataByIsUploaded(isUploaded:Boolean): List<HistoryDailyUnitInfo>

    @Query("SELECT * FROM history_daily_unit_info ORDER BY id DESC")
    fun queryLiveDataList(): LiveData<MutableList<HistoryDailyUnitInfo>>

}