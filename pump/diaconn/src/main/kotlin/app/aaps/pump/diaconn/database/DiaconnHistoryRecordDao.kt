package app.aaps.pump.diaconn.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.rxjava3.core.Single

@Dao
abstract class DiaconnHistoryRecordDao {

    @Query("SELECT * from $TABLE_DIACONN_HISTORY WHERE timestamp >= :timestamp AND code = :type")
    abstract fun allFromByType(timestamp: Long, type: Byte): Single<List<DiaconnHistoryRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun createOrUpdate(diaconnHistoryRecord: DiaconnHistoryRecord)

    @Query( "SELECT * from $TABLE_DIACONN_HISTORY WHERE pumpUid = :pumpUid ORDER BY timestamp DESC LIMIT 1" )
    abstract fun getLastRecord(pumpUid: String): DiaconnHistoryRecord?
}
