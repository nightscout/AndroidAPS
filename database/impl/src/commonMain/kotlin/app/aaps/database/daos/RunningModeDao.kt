package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.RunningMode
import app.aaps.database.entities.TABLE_RUNNING_MODE

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
    suspend fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE timestamp = :timestamp AND referenceId IS NULL")
    suspend fun findByTimestamp(timestamp: Long): RunningMode?

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE (nightscoutId = :nsId) AND (referenceId IS NULL)")
    suspend fun findByNSId(nsId: String): RunningMode?

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE (timestamp <= :timestamp) AND ((timestamp + duration) > :timestamp) AND (referenceId IS NULL) AND (isValid = 1) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getTemporaryRunningModeActiveAt(timestamp: Long): RunningMode?

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE (timestamp <= :timestamp) AND (duration = 0) AND (referenceId IS NULL) AND (isValid = 1) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getPermanentRunningModeActiveAt(timestamp: Long): RunningMode?

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getAllRunningModes(): List<RunningMode>

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE (timestamp >= :timestamp) AND (referenceId IS NULL) ORDER BY timestamp ASC")
    suspend fun getRunningModeDataIncludingInvalidFromTime(timestamp: Long): List<RunningMode>

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE (timestamp >= :timestamp) AND (isValid = 1) AND (referenceId IS NULL) ORDER BY timestamp ASC")
    suspend fun getRunningModeDataFromTime(timestamp: Long): List<RunningMode>

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE (timestamp >= :startTime) AND (timestamp <= :endTime) AND (isValid = 1) AND (referenceId IS NULL) ORDER BY timestamp ASC")
    suspend fun getRunningModeDataFromTimeToTime(startTime: Long, endTime: Long): List<RunningMode>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE id > :id ORDER BY id ASC limit 1")
    suspend fun getNextModifiedOrNewAfter(id: Long): RunningMode?

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE id = :referenceId")
    suspend fun getCurrentFromHistoric(referenceId: Long): RunningMode?

    @Query("SELECT * FROM $TABLE_RUNNING_MODE WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<RunningMode>
}
