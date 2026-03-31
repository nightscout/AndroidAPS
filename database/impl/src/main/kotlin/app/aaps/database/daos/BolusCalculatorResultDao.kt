package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.TABLE_BOLUS_CALCULATOR_RESULTS

@Dao
internal interface BolusCalculatorResultDao : TraceableDao<BolusCalculatorResult> {

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE id = :id")
    override fun findById(id: Long): BolusCalculatorResult?

    @Query("DELETE FROM $TABLE_BOLUS_CALCULATOR_RESULTS")

    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT id FROM $TABLE_BOLUS_CALCULATOR_RESULTS ORDER BY id DESC limit 1")
    suspend fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE (timestamp = :timestamp) AND (referenceId IS NULL)")
    suspend fun findByTimestamp(timestamp: Long): BolusCalculatorResult?

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE (nightscoutId = :nsId) AND (referenceId IS NULL)")
    suspend fun findByNSId(nsId: String): BolusCalculatorResult?

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE (isValid = 1) AND (timestamp >= :timestamp) AND (referenceId IS NULL) ORDER BY id DESC")
    suspend fun getBolusCalculatorResultsFromTime(timestamp: Long): List<BolusCalculatorResult>

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE (timestamp >= :timestamp) AND (referenceId IS NULL) ORDER BY id DESC")
    suspend fun getBolusCalculatorResultsIncludingInvalidFromTime(timestamp: Long): List<BolusCalculatorResult>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE id > :id ORDER BY id ASC limit 1")
    suspend fun getNextModifiedOrNewAfter(id: Long): BolusCalculatorResult?

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE id = :referenceId")
    suspend fun getCurrentFromHistoric(referenceId: Long): BolusCalculatorResult?

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<BolusCalculatorResult>
}