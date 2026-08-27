package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.TABLE_EXTENDED_BOLUSES
import app.aaps.database.entities.embedments.InterfaceIDs

@Dao
internal interface ExtendedBolusDao : TraceableDao<ExtendedBolus> {

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE id = :id")
    override fun findById(id: Long): ExtendedBolus?

    @Query("DELETE FROM $TABLE_EXTENDED_BOLUSES")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_EXTENDED_BOLUSES WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_EXTENDED_BOLUSES WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT id FROM $TABLE_EXTENDED_BOLUSES ORDER BY id DESC limit 1")
    suspend fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE (timestamp = :timestamp) AND (referenceId IS NULL)")
    suspend fun findByTimestamp(timestamp: Long): ExtendedBolus?

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE (nightscoutId = :nsId) AND (referenceId IS NULL)")
    suspend fun findByNSId(nsId: String): ExtendedBolus?

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE (pumpId = :pumpId) AND (pumpType = :pumpType) AND (pumpSerial = :pumpSerial) AND (referenceId IS NULL)")
    suspend fun findByPumpIds(pumpId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): ExtendedBolus?

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE (endId = :endPumpId) AND (pumpType = :pumpType) AND (pumpSerial = :pumpSerial) AND (referenceId IS NULL)")
    suspend fun findByPumpEndIds(endPumpId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): ExtendedBolus?

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE (timestamp <= :timestamp) AND ((timestamp + duration) > :timestamp) AND (referenceId IS NULL) AND (isValid = 1) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getExtendedBolusActiveAtLegacy(timestamp: Long): ExtendedBolus?

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE (timestamp <= :timestamp) AND ((timestamp + duration) > :timestamp) AND (referenceId IS NULL) AND (isValid = 1) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getExtendedBolusActiveAt(timestamp: Long): ExtendedBolus?

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE (timestamp >= :timestamp) AND (isValid = 1) AND (referenceId IS NULL) ORDER BY timestamp ASC")
    suspend fun getExtendedBolusesStartingFromTime(timestamp: Long): List<ExtendedBolus>

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE (timestamp BETWEEN :from AND :to) AND (isValid = 1) AND (referenceId IS NULL) ORDER BY timestamp ASC")
    suspend fun getExtendedBolusDataFromTimeToTime(from: Long, to: Long): List<ExtendedBolus>

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE (timestamp >= :timestamp) AND (referenceId IS NULL) ORDER BY timestamp ASC")
    suspend fun getExtendedBolusDataIncludingInvalidFromTime(timestamp: Long): List<ExtendedBolus>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE id > :id ORDER BY id ASC limit 1")
    suspend fun getNextModifiedOrNewAfter(id: Long): ExtendedBolus?

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE id = :referenceId")
    suspend fun getCurrentFromHistoric(referenceId: Long): ExtendedBolus?

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE isValid = 1 AND referenceId IS NULL ORDER BY id ASC LIMIT 1")
    suspend fun getOldestRecord(): ExtendedBolus?

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<ExtendedBolus>
}
