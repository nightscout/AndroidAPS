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

    @Query("SELECT * FROM history_unit_info WHERE id BETWEEN :startId AND (SELECT MAX(id) FROM history_unit_info) ORDER BY id ASC")
    suspend fun getItemsFromIdToMax(startId: Int): List<HistoryUnitInfo>

    @Query("SELECT * FROM history_unit_info WHERE isUploaded=:isUploaded ORDER BY id DESC")
    fun queryAllDataByIsUploaded(isUploaded:Boolean): List<HistoryUnitInfo>

    @Query("SELECT * FROM history_unit_info ORDER BY id DESC")
    fun queryLiveDataList(): LiveData<List<HistoryUnitInfo>>

    @Query("SELECT * FROM history_unit_info WHERE type=:type ORDER BY id DESC")
    fun queryAllLiveDataByType(type: Int): LiveData<List<HistoryUnitInfo>>

    @Query("SELECT * FROM history_unit_info WHERE blueName=:name ORDER BY id DESC")
    fun queryAllLiveDataByBlueName(name: String): LiveData<List<HistoryUnitInfo>>

    @Query("SELECT * FROM history_unit_info WHERE time>=:since ORDER BY time DESC")
    fun querySince(since: Long): List<HistoryUnitInfo>

    /** The pump sends its last entry again at each read, and `reserved` is what tells two entries of one second apart. */
    @Query("SELECT COUNT(*) FROM history_unit_info WHERE time=:time AND type=:type AND reserved=:reserved")
    fun count(time: Long, type: Int, reserved: Int): Int

    /** Rows get a higher id as they are stored, so a row with a higher id was stored later. 0 for an empty table. */
    @Query("SELECT COALESCE(MAX(id), 0) FROM history_unit_info")
    fun maxId(): Int

    /** The newest entry of this type, as the last bolus after an app restart. */
    @Query("SELECT * FROM history_unit_info WHERE type=:type ORDER BY time DESC LIMIT 1")
    fun newest(type: Int): HistoryUnitInfo?

    /**
     * 删除掉多存的历史记录
     */
    @Query("""
    DELETE FROM history_unit_info
    WHERE id NOT IN (
        SELECT MIN(id)
        FROM history_unit_info
        GROUP BY time, type,data,injectTime,delayBolus,reserved
    )
""")
    fun deleteDuplicateHistoryUnitInfo()
}