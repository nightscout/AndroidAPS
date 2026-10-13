package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.TABLE_THERAPY_EVENTS
import app.aaps.database.entities.TherapyEvent
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

@Dao
internal interface TherapyEventDao : TraceableDao<TherapyEvent> {

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE id = :id")
    override fun findById(id: Long): TherapyEvent?

    @Query("DELETE FROM $TABLE_THERAPY_EVENTS")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_THERAPY_EVENTS WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_THERAPY_EVENTS WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT id FROM $TABLE_THERAPY_EVENTS ORDER BY id DESC limit 1")
    fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE likely(type = :type) AND unlikely(timestamp = :timestamp) AND likely(referenceId IS NULL)")
    fun findByTimestamp(type: TherapyEvent.Type, timestamp: Long): TherapyEvent?

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE unlikely(type = :type) AND likely(referenceId IS NULL)")
    fun getValidByType(type: TherapyEvent.Type): List<TherapyEvent>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE unlikely(nightscoutId = :nsId) AND likely(referenceId IS NULL)")
    fun findByNSId(nsId: String): TherapyEvent?

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE unlikely(timestamp >= :timestamp) AND likely(isValid = 1) AND likely(referenceId IS NULL) ORDER BY timestamp ASC")
    fun getTherapyEventDataFromTime(timestamp: Long): Single<List<TherapyEvent>>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE type = :type AND unlikely(timestamp >= :timestamp) AND likely(isValid = 1) AND likely(referenceId IS NULL) ORDER BY timestamp ASC")
    fun getTherapyEventDataFromTime(timestamp: Long, type: TherapyEvent.Type): Single<List<TherapyEvent>>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE unlikely(timestamp >= :timestamp) AND likely(referenceId IS NULL) ORDER BY timestamp ASC")
    fun getTherapyEventDataIncludingInvalidFromTime(timestamp: Long): Single<List<TherapyEvent>>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE type = :type AND likely(isValid = 1) AND unlikely(timestamp <= :now) AND likely(referenceId IS NULL) ORDER BY timestamp DESC LIMIT 1")
    fun getLastTherapyRecord(type: TherapyEvent.Type, now: Long): Maybe<TherapyEvent>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE unlikely(timestamp BETWEEN :from AND :to) AND likely(isValid = 1) AND likely(referenceId IS NULL) ORDER BY timestamp ASC")
    fun compatGetTherapyEventDataFromToTime(from: Long, to: Long): Single<List<TherapyEvent>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<TherapyEvent>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<TherapyEvent>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<TherapyEvent>
}