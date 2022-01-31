package info.nightscout.androidaps.diaconn.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Single

@Dao
abstract class DiaconnHistoryRecordDao {

    @Query("SELECT * from $TABLE_DIACONN_HISTORY WHERE timestamp >= :timestamp AND code = :type")
    abstract fun allFromByType(timestamp: Long, type: Byte): Single<List<DiaconnHistoryRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun createOrUpdate(diaconnHistoryRecord: DiaconnHistoryRecord)
}
