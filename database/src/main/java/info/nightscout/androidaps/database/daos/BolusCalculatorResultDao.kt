package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_BOLUS_CALCULATOR_RESULTS
import info.nightscout.androidaps.database.entities.BolusCalculatorResult
import io.reactivex.Maybe
import io.reactivex.Single

@Suppress("FunctionName")
@Dao
internal interface BolusCalculatorResultDao : TraceableDao<BolusCalculatorResult> {

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE id = :id")
    override fun findById(id: Long): BolusCalculatorResult?

    @Query("DELETE FROM $TABLE_BOLUS_CALCULATOR_RESULTS")

    override fun deleteAllEntries()

    @Query("SELECT id FROM $TABLE_BOLUS_CALCULATOR_RESULTS ORDER BY id DESC limit 1")
    fun getLastId(): Maybe<Long>

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE isValid = 1 AND timestamp >= :timestamp AND referenceId IS NULL ORDER BY id DESC")
    fun getBolusCalculatorResultsFromTime(timestamp: Long): Single<List<BolusCalculatorResult>>

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE timestamp >= :timestamp AND referenceId IS NULL ORDER BY id DESC")
    fun getBolusCalculatorResultsIncludingInvalidFromTime(timestamp: Long): Single<List<BolusCalculatorResult>>

    // This query will be used with v3 to get all changed records
    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE id > :id AND referenceId IS NULL OR id IN (SELECT DISTINCT referenceId FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE id > :id) ORDER BY id ASC")
    fun getModifiedFrom(id: Long): Single<List<BolusCalculatorResult>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<BolusCalculatorResult>

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<BolusCalculatorResult>
}