package info.nightscout.pump.dana.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.rxjava3.core.Single

@Dao
abstract class DanaHistoryRecordDao {

    @Query("SELECT * from $TABLE_DANA_HISTORY WHERE timestamp >= :timestamp AND code = :type ORDER BY timestamp DESC")
    abstract fun allFromByType(timestamp: Long, type: Byte): Single<List<DanaHistoryRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun createOrUpdate(danaHistoryRecord: DanaHistoryRecord)
}
