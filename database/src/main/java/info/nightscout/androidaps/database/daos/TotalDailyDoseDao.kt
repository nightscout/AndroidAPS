package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_TOTAL_DAILY_DOSES
import info.nightscout.androidaps.database.entities.TotalDailyDose
import io.reactivex.Single

@Suppress("FunctionName")
@Dao
internal interface TotalDailyDoseDao : TraceableDao<TotalDailyDose> {

    @Query("SELECT * FROM $TABLE_TOTAL_DAILY_DOSES WHERE id = :id")
    override fun findById(id: Long): TotalDailyDose?

    @Query("DELETE FROM $TABLE_TOTAL_DAILY_DOSES")
    override fun deleteAllEntries()
}