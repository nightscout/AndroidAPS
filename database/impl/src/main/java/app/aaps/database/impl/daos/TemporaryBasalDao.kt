package app.aaps.database.impl.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.TABLE_TEMPORARY_BASALS
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.embedments.InterfaceIDs
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

@Dao
internal interface TemporaryBasalDao : TraceableDao<TemporaryBasal> {

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE id = :id")
    override fun findById(id: Long): TemporaryBasal?

    @Query("DELETE FROM $TABLE_TEMPORARY_BASALS")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_TEMPORARY_BASALS WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_TEMPORARY_BASALS WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT id FROM $TABLE_TEMPORARY_BASALS ORDER BY id DESC limit 1")
    fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE temporaryId = :temporaryId")
    fun findByTempId(temporaryId: Long): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE timestamp = :timestamp AND referenceId IS NULL")
    fun findByTimestamp(timestamp: Long): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE pumpId = :pumpId AND pumpType = :pumpType AND pumpSerial = :pumpSerial AND referenceId IS NULL")
    fun findByPumpIds(pumpId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE endId = :endPumpId AND pumpType = :pumpType AND pumpSerial = :pumpSerial AND referenceId IS NULL")
    fun findByPumpEndIds(endPumpId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE nightscoutId = :nsId AND referenceId IS NULL")
    fun findByNSId(nsId: String): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE temporaryId = :temporaryId AND pumpType = :pumpType AND pumpSerial = :pumpSerial AND referenceId IS NULL")
    fun findByPumpTempIds(temporaryId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE timestamp <= :timestamp AND (timestamp + duration) > :timestamp AND pumpType = :pumpType AND pumpSerial = :pumpSerial AND referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getTemporaryBasalActiveAt(timestamp: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): Maybe<TemporaryBasal>

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE timestamp <= :timestamp AND (timestamp + duration) > :timestamp AND referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getTemporaryBasalActiveAt(timestamp: Long): Maybe<TemporaryBasal>

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE timestamp <= :to AND (timestamp + duration) > :from AND referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC")
    fun getTemporaryBasalActiveBetweenTimeAndTime(from: Long, to: Long): Single<List<TemporaryBasal>>

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getTemporaryBasalDataFromTime(timestamp: Long): Single<List<TemporaryBasal>>

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE timestamp >= :from AND timestamp <= :to AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getTemporaryBasalDataFromTimeToTime(from: Long, to: Long): Single<List<TemporaryBasal>>

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE timestamp >= :timestamp AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getTemporaryBasalDataIncludingInvalidFromTime(timestamp: Long): Single<List<TemporaryBasal>>

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE referenceId = :id ORDER BY id DESC LIMIT 1")
    fun getLastHistoryRecord(id: Long): TemporaryBasal?

    // This query will be used with v3 to get all changed records
    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE id > :id AND pumpId IS NOT NULL AND referenceId IS NULL OR id IN (SELECT DISTINCT referenceId FROM $TABLE_TEMPORARY_BASALS WHERE id > :id) ORDER BY id ASC")
    fun getModifiedFrom(id: Long): Single<List<TemporaryBasal>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE id > :id AND pumpId IS NOT NULL ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<TemporaryBasal>

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<TemporaryBasal>

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE isValid = 1 AND referenceId IS NULL ORDER BY id ASC LIMIT 1")
    fun getOldestRecord(): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<TemporaryBasal>
}