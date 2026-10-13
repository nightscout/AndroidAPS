package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.TABLE_TEMPORARY_TARGETS
import app.aaps.database.entities.TemporaryTarget
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

@Dao
internal interface TemporaryTargetDao : TraceableDao<TemporaryTarget> {

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE id = :id")
    override fun findById(id: Long): TemporaryTarget?

    @Query("DELETE FROM $TABLE_TEMPORARY_TARGETS")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_TEMPORARY_TARGETS WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_TEMPORARY_TARGETS WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT id FROM $TABLE_TEMPORARY_TARGETS ORDER BY id DESC limit 1")
    fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE unlikely(nightscoutId = :nsId) AND likely(referenceId IS NULL)")
    fun findByNSId(nsId: String): TemporaryTarget?

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE unlikely(timestamp <= :timestamp) AND unlikely((timestamp + duration) > :timestamp) AND likely(referenceId IS NULL) AND likely(isValid = 1) ORDER BY timestamp DESC LIMIT 1")
    fun getTemporaryTargetActiveAtLegacy(timestamp: Long): TemporaryTarget?

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE unlikely(timestamp <= :timestamp) AND unlikely((timestamp + duration) > :timestamp) AND likely(referenceId IS NULL) AND likely(isValid = 1) ORDER BY timestamp DESC LIMIT 1")
    fun getTemporaryTargetActiveAt(timestamp: Long): Maybe<TemporaryTarget>

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE unlikely(timestamp >= :timestamp) AND likely(isValid = 1) AND likely(referenceId IS NULL) ORDER BY timestamp ASC")
    fun getTemporaryTargetDataFromTime(timestamp: Long): Single<List<TemporaryTarget>>

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE unlikely(timestamp >= :timestamp) AND likely(referenceId IS NULL) ORDER BY timestamp ASC")
    fun getTemporaryTargetDataIncludingInvalidFromTime(timestamp: Long): Single<List<TemporaryTarget>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<TemporaryTarget>

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<TemporaryTarget>

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<TemporaryTarget>
}