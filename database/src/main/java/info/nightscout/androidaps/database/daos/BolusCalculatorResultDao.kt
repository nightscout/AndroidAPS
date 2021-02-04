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
}