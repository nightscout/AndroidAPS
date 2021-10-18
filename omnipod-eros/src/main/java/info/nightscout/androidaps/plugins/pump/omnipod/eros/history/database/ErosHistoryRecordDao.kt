package info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ErosHistoryRecordDao {

    @Query("SELECT * from historyrecords WHERE date >= :since order by date asc")
    fun allSinceAsc(since: Long): List<ErosHistoryRecordEntity>

    @Query("SELECT * from historyrecords WHERE date >= :since order by date desc")
    fun allSinceDesc(since: Long): List<ErosHistoryRecordEntity>

    @Query("SELECT * FROM historyrecords WHERE pumpId = :id LIMIT 1")
    fun byId(id: Long): ErosHistoryRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(ErosHistoryRecordEntity: ErosHistoryRecordEntity)

}
