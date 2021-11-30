package info.nightscout.androidaps.plugins.pump.omnipod.dash.history.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.InitialResult
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.ResolvedResult
import io.reactivex.Completable
import io.reactivex.Single

@Dao
abstract class HistoryRecordDao {

    @Query("SELECT * from historyrecords")
    abstract fun all(): Single<List<HistoryRecordEntity>>

    @Query("SELECT * from historyrecords")
    abstract fun allBlocking(): List<HistoryRecordEntity>

    @Query("SELECT * from historyrecords WHERE createdAt <= :since")
    abstract fun allSince(since: Long): Single<List<HistoryRecordEntity>>

    @Query("SELECT * FROM historyrecords WHERE id = :id LIMIT 1")
    abstract fun byIdBlocking(id: String): HistoryRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun saveBlocking(historyRecordEntity: HistoryRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun save(historyRecordEntity: HistoryRecordEntity): Completable

    @Delete
    abstract fun delete(historyRecordEntity: HistoryRecordEntity): Completable

    @Query("UPDATE historyrecords SET resolvedResult = :resolvedResult, resolvedAt = :resolvedAt WHERE id = :id ")
    abstract fun markResolved(id: String, resolvedResult: ResolvedResult, resolvedAt: Long): Completable

    @Query("UPDATE historyrecords SET initialResult = :initialResult  WHERE id = :id ")
    abstract fun setInitialResult(id: String, initialResult: InitialResult): Completable
}
