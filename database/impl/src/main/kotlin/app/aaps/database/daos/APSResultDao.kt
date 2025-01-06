package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.APSResult
import app.aaps.database.entities.TABLE_APS_RESULTS
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

@Dao
internal interface APSResultDao : TraceableDao<APSResult> {

    @Query("SELECT * FROM $TABLE_APS_RESULTS WHERE id = :id")
    override fun findById(id: Long): APSResult?

    @Query("DELETE FROM $TABLE_APS_RESULTS")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_APS_RESULTS WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_APS_RESULTS WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT * FROM $TABLE_APS_RESULTS WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<APSResult>

    @Query("SELECT * FROM $TABLE_APS_RESULTS WHERE timestamp > :since AND timestamp <= :until ORDER BY timestamp DESC LIMIT 1")
    fun getApsResult(since: Long, until: Long): Maybe<APSResult>

    @Query("SELECT * FROM $TABLE_APS_RESULTS WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    fun getApsResults(start: Long, end: Long): Single<List<APSResult>>
}