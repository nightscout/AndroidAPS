package app.aaps.pump.omnipod.eros.history.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

@Dao
interface ErosHistoryRecordDao {

    @Query("SELECT * from historyrecords WHERE date >= :since order by date asc")
    fun allSinceAsc(since: Long): Single<List<ErosHistoryRecordEntity>>

    @Query("SELECT * FROM historyrecords WHERE pumpId = :id LIMIT 1")
    fun byId(id: Long): Maybe<ErosHistoryRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(erosHistoryRecordEntity: ErosHistoryRecordEntity): Long

}
