package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_BOLUS_CALCULATOR_RESULTS
import info.nightscout.androidaps.database.entities.BolusCalculatorResult
import io.reactivex.Single

@Suppress("FunctionName")
@Dao
internal interface BolusCalculatorResultDao : TraceableDao<BolusCalculatorResult> {

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE id = :id")
    override fun findById(id: Long): BolusCalculatorResult?

    @Query("DELETE FROM $TABLE_BOLUS_CALCULATOR_RESULTS")

    override fun deleteAllEntries()

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE isValid = 1 AND timestamp >= :timestamp AND referenceId IS NULL ORDER BY id DESC")
    fun getBolusCalculatorResultsFromTime(timestamp: Long): Single<List<BolusCalculatorResult>>

    @Query("SELECT * FROM $TABLE_BOLUS_CALCULATOR_RESULTS WHERE timestamp >= :timestamp AND referenceId IS NULL ORDER BY id DESC")
    fun getBolusCalculatorResultsIncludingInvalidFromTime(timestamp: Long): Single<List<BolusCalculatorResult>>
}