package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_OFFLINE_EVENTS
import info.nightscout.androidaps.database.entities.OfflineEvent
import io.reactivex.Maybe
import io.reactivex.Single

@Suppress("FunctionName")
@Dao
internal interface OfflineEventDao : TraceableDao<OfflineEvent> {

    @Query("SELECT * FROM $TABLE_OFFLINE_EVENTS WHERE id = :id")
    override fun findById(id: Long): OfflineEvent?

    @Query("DELETE FROM $TABLE_OFFLINE_EVENTS")
    override fun deleteAllEntries()

    @Query("SELECT id FROM $TABLE_OFFLINE_EVENTS ORDER BY id DESC limit 1")
    fun getLastId(): Maybe<Long>

    @Query("SELECT * FROM $TABLE_OFFLINE_EVENTS WHERE nightscoutId = :nsId AND referenceId IS NULL")
    fun findByNSId(nsId: String): OfflineEvent?

    @Query("SELECT * FROM $TABLE_OFFLINE_EVENTS WHERE timestamp <= :timestamp AND (timestamp + duration) > :timestamp AND referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getOfflineEventActiveAt(timestamp: Long): Maybe<OfflineEvent>

    @Query("SELECT * FROM $TABLE_OFFLINE_EVENTS WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getOfflineEventDataFromTime(timestamp: Long): Single<List<OfflineEvent>>

    @Query("SELECT * FROM $TABLE_OFFLINE_EVENTS WHERE timestamp >= :timestamp AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getOfflineEventDataIncludingInvalidFromTime(timestamp: Long): Single<List<OfflineEvent>>

    @Query("SELECT * FROM $TABLE_OFFLINE_EVENTS WHERE timestamp BETWEEN :start AND :end AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getOfflineEventDataFromTimeToTime(start: Long, end: Long): Single<List<OfflineEvent>>

    @Query("SELECT * FROM $TABLE_OFFLINE_EVENTS WHERE isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getOfflineEventData(): Single<List<OfflineEvent>>

    // This query will be used with v3 to get all changed records
    @Query("SELECT * FROM $TABLE_OFFLINE_EVENTS WHERE id > :id AND referenceId IS NULL OR id IN (SELECT DISTINCT referenceId FROM $TABLE_OFFLINE_EVENTS WHERE id > :id) ORDER BY id ASC")
    fun getModifiedFrom(id: Long): Single<List<OfflineEvent>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_OFFLINE_EVENTS WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<OfflineEvent>

    @Query("SELECT * FROM $TABLE_OFFLINE_EVENTS WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<OfflineEvent>
}