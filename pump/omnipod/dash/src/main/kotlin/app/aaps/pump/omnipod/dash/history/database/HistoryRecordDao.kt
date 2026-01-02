package app.aaps.pump.omnipod.dash.history.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.aaps.pump.omnipod.dash.history.data.InitialResult
import app.aaps.pump.omnipod.dash.history.data.ResolvedResult
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single

@Dao
abstract class HistoryRecordDao {

    @Query("SELECT * from historyrecords")
    abstract fun all(): Single<List<HistoryRecordEntity>>

    @Query("SELECT * from historyrecords ORDER BY id LIMIT 1")
    abstract fun first(): HistoryRecordEntity?

    @Query("SELECT * from historyrecords WHERE createdAt >= :since ORDER BY createdAt DESC")
    abstract fun allSince(since: Long): Single<List<HistoryRecordEntity>>

    @Query("SELECT * FROM historyrecords WHERE id = :id LIMIT 1")
    abstract fun byIdBlocking(id: Long): HistoryRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun save(historyRecordEntity: HistoryRecordEntity): Single<Long>

    @Delete
    abstract fun delete(historyRecordEntity: HistoryRecordEntity): Completable

    @Query("UPDATE historyrecords SET resolvedResult = :resolvedResult, resolvedAt = :resolvedAt WHERE id = :id ")
    abstract fun markResolved(id: Long, resolvedResult: ResolvedResult, resolvedAt: Long): Completable

    @Query("UPDATE historyrecords SET initialResult = :initialResult  WHERE id = :id ")
    abstract fun setInitialResult(id: Long, initialResult: InitialResult): Completable

    @Query("UPDATE historyrecords SET totalAmountDelivered = :totalAmountDelivered WHERE id = :id ")
    abstract fun setTotalAmountDelivered(id: Long, totalAmountDelivered: Double?): Completable
}
