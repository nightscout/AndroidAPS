package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.RunningMode
import app.aaps.database.entities.TABLE_RUNNING_MODE
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

@Dao
internal interface RunningModeDao : TraceableDao<RunningMode> {

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE id = :id")
    override fun findById(id: Long): RunningMode?

    @Query("DELETE FROM $TABLE_RUNNING_MODE")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_RUNNING_MODE WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_RUNNING_MODE WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT id FROM $TABLE_RUNNING_MODE ORDER BY id DESC limit 1")
    fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE timestamp = :timestamp AND referenceId IS NULL")
    fun findByTimestamp(timestamp: Long): RunningMode?

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE unlikely(nightscoutId = :nsId) AND likely(referenceId IS NULL)")
    fun findByNSId(nsId: String): RunningMode?

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE unlikely(timestamp <= :timestamp) AND unlikely((timestamp + duration) > :timestamp) AND likely(referenceId IS NULL) AND likely(isValid = 1) ORDER BY timestamp DESC LIMIT 1")
    fun getTemporaryRunningModeActiveAt(timestamp: Long): Maybe<RunningMode>

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE unlikely(timestamp <= :timestamp) AND unlikely(duration = 0) AND likely(referenceId IS NULL) AND likely(isValid = 1) ORDER BY timestamp DESC LIMIT 1")
    fun getPermanentRunningModeActiveAt(timestamp: Long): Maybe<RunningMode>

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getAllRunningModes(): Single<List<RunningMode>>

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE unlikely(timestamp >= :timestamp) AND likely(referenceId IS NULL) ORDER BY timestamp ASC")
    fun getRunningModeDataIncludingInvalidFromTime(timestamp: Long): Single<List<RunningMode>>

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE unlikely(timestamp >= :timestamp) AND likely(isValid = 1) AND likely(referenceId IS NULL) ORDER BY timestamp ASC")
    fun getRunningModeDataFromTime(timestamp: Long): Single<List<RunningMode>>

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE unlikely(timestamp >= :startTime) AND unlikely(timestamp <= :endTime) AND likely(isValid = 1) AND likely(referenceId IS NULL) ORDER BY timestamp ASC")
    fun getRunningModeDataFromTimeToTime(startTime: Long, endTime: Long): Single<List<RunningMode>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<RunningMode>

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<RunningMode>

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<RunningMode>
}