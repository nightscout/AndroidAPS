package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.TABLE_BOLUS_CALCULATOR_RESULTS
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

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
    fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE unlikely(timestamp = :timestamp) AND likely(referenceId IS NULL)")
    fun findByTimestamp(timestamp: Long): BolusCalculatorResult?

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE unlikely(nightscoutId = :nsId) AND likely(referenceId IS NULL)")
    fun findByNSId(nsId: String): BolusCalculatorResult?

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE likely(isValid = 1) AND unlikely(timestamp >= :timestamp) AND likely(referenceId IS NULL) ORDER BY id DESC")
    fun getBolusCalculatorResultsFromTime(timestamp: Long): Single<List<BolusCalculatorResult>>

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE unlikely(timestamp >= :timestamp) AND likely(referenceId IS NULL) ORDER BY id DESC")
    fun getBolusCalculatorResultsIncludingInvalidFromTime(timestamp: Long): Single<List<BolusCalculatorResult>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<BolusCalculatorResult>

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<BolusCalculatorResult>

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<BolusCalculatorResult>
}