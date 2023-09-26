package app.aaps.database.impl.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.TABLE_BOLUSES
import app.aaps.database.entities.embedments.InterfaceIDs
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

@Dao
internal interface BolusDao : TraceableDao<Bolus> {

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE id = :id")
    override fun findById(id: Long): Bolus?

    @Query("DELETE FROM $TABLE_BOLUSES")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_BOLUSES WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_BOLUSES WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT id FROM $TABLE_BOLUSES ORDER BY id DESC limit 1")
    fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE timestamp = :timestamp AND referenceId IS NULL")
    fun findByTimestamp(timestamp: Long): Bolus?

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE nightscoutId = :nsId AND referenceId IS NULL")
    fun findByNSId(nsId: String): Bolus?

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE pumpId = :pumpId AND pumpType = :pumpType AND pumpSerial = :pumpSerial AND referenceId IS NULL")
    fun findByPumpIds(pumpId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): Bolus?

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE temporaryId = :temporaryId AND pumpType = :pumpType AND pumpSerial = :pumpSerial AND referenceId IS NULL")
    fun findByPumpTempIds(temporaryId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): Bolus?

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE isValid = 1 AND type <> :exclude AND referenceId IS NULL ORDER BY timestamp DESC LIMIT 1")
    fun getLastBolusRecord(exclude: Bolus.Type = Bolus.Type.PRIMING): Bolus?

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE isValid = 1 AND type <> :exclude AND referenceId IS NULL ORDER BY timestamp DESC LIMIT 1")
    fun getLastBolusRecordMaybe(exclude: Bolus.Type = Bolus.Type.PRIMING): Maybe<Bolus>

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE isValid = 1 AND type == :only AND referenceId IS NULL ORDER BY timestamp DESC LIMIT 1")
    fun getLastBolusRecordOfType(only: Bolus.Type): Maybe<Bolus>

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE isValid = 1 AND type <> :exclude AND referenceId IS NULL ORDER BY timestamp ASC LIMIT 1")
    fun getOldestBolusRecord(exclude: Bolus.Type = Bolus.Type.PRIMING): Bolus?

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE isValid = 1 AND timestamp >= :timestamp AND referenceId IS NULL ORDER BY id DESC")
    fun getBolusesFromTime(timestamp: Long): Single<List<Bolus>>

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE isValid = 1 AND timestamp >= :start AND timestamp <= :end AND referenceId IS NULL ORDER BY id DESC")
    fun getBolusesFromTime(start: Long, end: Long): Single<List<Bolus>>

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE timestamp >= :timestamp AND referenceId IS NULL ORDER BY id DESC")
    fun getBolusesIncludingInvalidFromTime(timestamp: Long): Single<List<Bolus>>

    // This query will be used with v3 to get all changed records
    @Query("SELECT * FROM $TABLE_BOLUSES WHERE id > :id AND pumpId IS NOT NULL AND type <> :exclude AND referenceId IS NULL OR id IN (SELECT DISTINCT referenceId FROM $TABLE_BOLUSES WHERE id > :id) ORDER BY id ASC")
    fun getModifiedFrom(id: Long, exclude: Bolus.Type = Bolus.Type.PRIMING): Single<List<Bolus>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_BOLUSES WHERE id > :id AND pumpId IS NOT NULL AND type <> :exclude ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfterExclude(id: Long, exclude: Bolus.Type = Bolus.Type.PRIMING): Maybe<Bolus>

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<Bolus>

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<Bolus>
}