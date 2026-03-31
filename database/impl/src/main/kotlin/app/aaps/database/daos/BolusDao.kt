package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.TABLE_BOLUSES
import app.aaps.database.entities.embedments.InterfaceIDs

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
    suspend fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE (timestamp = :timestamp) AND (referenceId IS NULL)")
    suspend fun findByTimestamp(timestamp: Long): Bolus?

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE (nightscoutId = :nsId) AND (referenceId IS NULL)")
    suspend fun getByNSId(nsId: String): Bolus?

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE (pumpId = :pumpId) AND (pumpType = :pumpType) AND (pumpSerial = :pumpSerial) AND (referenceId IS NULL)")
    suspend fun findByPumpIds(pumpId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): Bolus?

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE (temporaryId = :temporaryId) AND (pumpType = :pumpType) AND (pumpSerial = :pumpSerial) AND (referenceId IS NULL)")
    suspend fun findByPumpTempIds(temporaryId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): Bolus?

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE (isValid = 1) AND type <> :exclude AND (referenceId IS NULL) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastBolusRecord(exclude: Bolus.Type = Bolus.Type.PRIMING): Bolus?

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE (isValid = 1) AND type == :only AND (referenceId IS NULL) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastBolusRecordOfType(only: Bolus.Type): Bolus?

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE (isValid = 1) AND (type <> :exclude) AND (referenceId IS NULL) ORDER BY timestamp ASC LIMIT 1")
    suspend fun getOldestBolusRecord(exclude: Bolus.Type = Bolus.Type.PRIMING): Bolus?

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE (isValid = 1) AND (timestamp >= :timestamp) AND (referenceId IS NULL) ORDER BY id DESC")
    suspend fun getBolusesFromTime(timestamp: Long): List<Bolus>

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE (isValid = 1) AND (timestamp BETWEEN :start AND :end) AND (referenceId IS NULL) ORDER BY id DESC")
    suspend fun getBolusesFromTime(start: Long, end: Long): List<Bolus>

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE (isValid = 1) AND (referenceId IS NULL) ORDER BY timestamp ASC")
    suspend fun getAllBoluses(): List<Bolus>

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE (timestamp >= :timestamp) AND (referenceId IS NULL) ORDER BY id DESC")
    suspend fun getBolusesIncludingInvalidFromTime(timestamp: Long): List<Bolus>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_BOLUSES WHERE (id > :id) AND (pumpId IS NOT NULL) AND (type <> :exclude) ORDER BY id ASC limit 1")
    suspend fun getNextModifiedOrNewAfterExclude(id: Long, exclude: Bolus.Type = Bolus.Type.PRIMING): Bolus?

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE id = :referenceId")
    suspend fun getCurrentFromHistoric(referenceId: Long): Bolus?

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<Bolus>
}