package info.nightscout.database.impl.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.TABLE_GLUCOSE_VALUES
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

@Dao
internal interface GlucoseValueDao : TraceableDao<GlucoseValue> {

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id = :id")
    override fun findById(id: Long): GlucoseValue?

    @Query("DELETE FROM $TABLE_GLUCOSE_VALUES")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_GLUCOSE_VALUES WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_GLUCOSE_VALUES WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE isValid = 1 AND referenceId IS NULL ORDER BY timestamp DESC limit 1")
    fun getLast(): Maybe<GlucoseValue>

    @Query("SELECT id FROM $TABLE_GLUCOSE_VALUES ORDER BY id DESC limit 1")
    fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE nightscoutId = :nsId AND referenceId IS NULL")
    fun findByNSId(nsId: String): GlucoseValue?

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE timestamp = :timestamp AND sourceSensor = :sourceSensor AND referenceId IS NULL")
    fun findByTimestampAndSensor(timestamp: Long, sourceSensor: GlucoseValue.SourceSensor): GlucoseValue?

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL AND value >= 39 ORDER BY timestamp ASC")
    fun compatGetBgReadingsDataFromTime(timestamp: Long): Single<List<GlucoseValue>>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE timestamp BETWEEN :start AND :end AND isValid = 1 AND referenceId IS NULL AND value >= 39 ORDER BY timestamp ASC")
    fun compatGetBgReadingsDataFromTime(start: Long, end: Long): Single<List<GlucoseValue>>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id > :lastId AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getDataFromId(lastId: Long): Single<List<GlucoseValue>>

    // This query will be used with v3 to get all changed records
    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id > :id AND referenceId IS NULL OR id IN (SELECT DISTINCT referenceId FROM $TABLE_GLUCOSE_VALUES WHERE id > :id) ORDER BY id ASC")
    fun getModifiedFrom(id: Long): Single<List<GlucoseValue>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<GlucoseValue>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<GlucoseValue>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<GlucoseValue>
}