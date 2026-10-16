package app.aaps.pump.omnipod.eros.history.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ErosHistoryRecordDao {

    @Query("SELECT * from historyrecords WHERE date >= :since order by date asc")
    suspend fun allSinceAsc(since: Long): List<ErosHistoryRecordEntity>

    @Query("SELECT * FROM historyrecords WHERE pumpId = :id LIMIT 1")
    suspend fun byId(id: Long): ErosHistoryRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(erosHistoryRecordEntity: ErosHistoryRecordEntity): Long

}
