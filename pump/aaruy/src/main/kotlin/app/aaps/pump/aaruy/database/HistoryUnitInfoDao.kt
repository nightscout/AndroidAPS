package app.aaps.pump.aaruy.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface HistoryUnitInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(historyUnitInfo: HistoryUnitInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertList(list: List<HistoryUnitInfo>)

    @Delete
    fun delete(historyUnitInfo: HistoryUnitInfo)

    /** The pump sends its last entry again at each read, and `reserved` is what tells two entries of one second apart. */
    @Query("SELECT COUNT(*) FROM history_unit_info WHERE time=:time AND type=:type AND reserved=:reserved")
    fun count(time: Long, type: Int, reserved: Int): Int

    @Query("SELECT * FROM history_unit_info WHERE time>=:since ORDER BY time DESC")
    fun querySince(since: Long): List<HistoryUnitInfo>

    @Query("DELETE FROM history_unit_info")
    fun deleteAll()

    @Update
    fun update(historyUnitInfo: HistoryUnitInfo)

    @Query("SELECT * FROM history_unit_info ORDER BY id DESC")
    fun queryAllByDesc(): List<HistoryUnitInfo>

    @Query("SELECT * FROM history_unit_info ORDER BY id ASC")
    fun queryAllByAsc(): List<HistoryUnitInfo>

    @Query("SELECT * FROM history_unit_info ORDER BY id DESC")
    fun queryAll(): List<HistoryUnitInfo>



    @Query("SELECT * FROM history_unit_info ORDER BY id DESC")
    fun queryLiveDataList(): LiveData<List<HistoryUnitInfo>>

    @Query("SELECT * FROM history_unit_info WHERE type=:type ORDER BY id DESC")
    fun queryAllLiveDataByType(type: Int): LiveData<List<HistoryUnitInfo>>

    @Query("SELECT * FROM history_unit_info WHERE blueName=:name ORDER BY id DESC")
    fun queryAllLiveDataByBlueName(name: String): LiveData<List<HistoryUnitInfo>>
}