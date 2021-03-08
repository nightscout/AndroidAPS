package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_THERAPY_EVENTS
import info.nightscout.androidaps.database.entities.TherapyEvent
import io.reactivex.Maybe
import io.reactivex.Single

@Dao
internal interface TherapyEventDao : TraceableDao<TherapyEvent> {

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE id = :id")
    override fun findById(id: Long): TherapyEvent?

    @Query("DELETE FROM $TABLE_THERAPY_EVENTS")
    override fun deleteAllEntries()

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE type = :type AND timestamp = :timestamp AND referenceId IS NULL")
    fun findByTimestamp(type: TherapyEvent.Type, timestamp: Long): TherapyEvent?

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE type = :type AND referenceId IS NULL")
    fun getValidByType(type: TherapyEvent.Type): List<TherapyEvent>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE nightscoutId = :nsId AND referenceId IS NULL")
    fun findByNSId(nsId: String): TherapyEvent?

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getTherapyEventDataFromTime(timestamp: Long): Single<List<TherapyEvent>>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE type = :type AND timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getTherapyEventDataFromTime(timestamp: Long, type: TherapyEvent.Type): Single<List<TherapyEvent>>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE timestamp >= :timestamp AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getTherapyEventDataIncludingInvalidFromTime(timestamp: Long): Single<List<TherapyEvent>>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE type = :type AND isValid = 1 ORDER BY id DESC LIMIT 1")
    fun getLastTherapyRecord(type: TherapyEvent.Type): Maybe<TherapyEvent>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun compatGetTherapyEventDataFromTime(timestamp: Long): Single<List<TherapyEvent>>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE timestamp >= :from AND timestamp <= :to AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun compatGetTherapyEventDataFromToTime(from: Long, to: Long): Single<List<TherapyEvent>>
}