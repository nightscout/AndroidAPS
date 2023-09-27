package app.aaps.database.impl.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.TABLE_EXTENDED_BOLUSES
import app.aaps.database.entities.embedments.InterfaceIDs
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

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
    fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE timestamp = :timestamp AND referenceId IS NULL")
    fun findByTimestamp(timestamp: Long): ExtendedBolus?

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE nightscoutId = :nsId AND referenceId IS NULL")
    fun findByNSId(nsId: String): ExtendedBolus?

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE pumpId = :pumpId AND pumpType = :pumpType AND pumpSerial = :pumpSerial AND referenceId IS NULL")
    fun findByPumpIds(pumpId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): ExtendedBolus?

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE endId = :endPumpId AND pumpType = :pumpType AND pumpSerial = :pumpSerial AND referenceId IS NULL")
    fun findByPumpEndIds(endPumpId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): ExtendedBolus?

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE timestamp <= :timestamp AND (timestamp + duration) > :timestamp AND pumpType = :pumpType AND pumpSerial = :pumpSerial AND referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getExtendedBolusActiveAt(timestamp: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): Maybe<ExtendedBolus>

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE timestamp <= :timestamp AND (timestamp + duration) > :timestamp AND referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getExtendedBolusActiveAt(timestamp: Long): Maybe<ExtendedBolus>

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getExtendedBolusDataFromTime(timestamp: Long): Single<List<ExtendedBolus>>

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE timestamp >= :from AND timestamp <= :to AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getExtendedBolusDataFromTimeToTime(from: Long, to: Long): Single<List<ExtendedBolus>>

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE timestamp >= :timestamp AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getExtendedBolusDataIncludingInvalidFromTime(timestamp: Long): Single<List<ExtendedBolus>>

    // This query will be used with v3 to get all changed records
    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE id > :id AND referenceId IS NULL OR id IN (SELECT DISTINCT referenceId FROM $TABLE_EXTENDED_BOLUSES WHERE id > :id) ORDER BY id ASC")
    fun getModifiedFrom(id: Long): Single<List<ExtendedBolus>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<ExtendedBolus>

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<ExtendedBolus>

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE isValid = 1 AND referenceId IS NULL ORDER BY id ASC LIMIT 1")
    fun getOldestRecord(): ExtendedBolus?

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<ExtendedBolus>

}