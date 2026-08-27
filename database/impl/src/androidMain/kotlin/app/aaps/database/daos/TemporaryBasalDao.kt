package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.TABLE_TEMPORARY_BASALS
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.embedments.InterfaceIDs

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
    suspend fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE temporaryId = :temporaryId")
    suspend fun findByTempId(temporaryId: Long): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE (timestamp = :timestamp) AND (referenceId IS NULL)")
    suspend fun findByTimestamp(timestamp: Long): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE (pumpId = :pumpId) AND (pumpType = :pumpType) AND (pumpSerial = :pumpSerial) AND (referenceId IS NULL)")
    suspend fun findByPumpIds(pumpId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE endId = :endPumpId AND pumpType = :pumpType AND pumpSerial = :pumpSerial AND (referenceId IS NULL)")
    suspend fun findByPumpEndIds(endPumpId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE (nightscoutId = :nsId) AND (referenceId IS NULL)")
    suspend fun findByNSId(nsId: String): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE (temporaryId = :temporaryId) AND (pumpType = :pumpType) AND (pumpSerial = :pumpSerial) AND (referenceId IS NULL)")
    suspend fun findByPumpTempIds(temporaryId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE (timestamp <= :timestamp) AND ((timestamp + duration) > :timestamp) AND (referenceId IS NULL) AND (isValid = 1) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getTemporaryBasalActiveAtLegacy(timestamp: Long): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE (timestamp <= :timestamp) AND ((timestamp + duration) > :timestamp) AND (referenceId IS NULL) AND (isValid = 1) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getTemporaryBasalActiveAt(timestamp: Long): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE (timestamp <= :to) AND ((timestamp + duration) > :from) AND (referenceId IS NULL) AND (isValid = 1) ORDER BY timestamp DESC")
    suspend fun getTemporaryBasalActiveBetweenTimeAndTime(from: Long, to: Long): List<TemporaryBasal>

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE (timestamp >= :timestamp) AND (isValid = 1) AND (referenceId IS NULL) ORDER BY timestamp ASC")
    suspend fun getTemporaryBasalDataFromTime(timestamp: Long): List<TemporaryBasal>

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE (timestamp BETWEEN :from and :to) AND (isValid = 1) AND (referenceId IS NULL) ORDER BY timestamp ASC")
    suspend fun getTemporaryBasalStartingFromTimeToTime(from: Long, to: Long): List<TemporaryBasal>

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE (timestamp >= :timestamp) AND (referenceId IS NULL) ORDER BY timestamp ASC")
    suspend fun getTemporaryBasalDataIncludingInvalidFromTime(timestamp: Long): List<TemporaryBasal>

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE (id > :id) AND (pumpId IS NOT NULL) ORDER BY id ASC limit 1")
    suspend fun getNextModifiedOrNewAfter(id: Long): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE id = :referenceId")
    suspend fun getCurrentFromHistoric(referenceId: Long): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE isValid = 1 AND referenceId IS NULL ORDER BY id ASC LIMIT 1")
    suspend fun getOldestRecord(): TemporaryBasal?

    @Query("SELECT * FROM $TABLE_TEMPORARY_BASALS WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<TemporaryBasal>
}
