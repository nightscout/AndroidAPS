package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import info.nightscout.androidaps.database.TABLE_MEAL_LINKS
import info.nightscout.androidaps.database.entities.MealLink
import info.nightscout.androidaps.database.entities.MealLinkLoaded
import io.reactivex.Single

@Suppress("FunctionName")
@Dao
internal interface MealLinkDao : TraceableDao<MealLink> {

    @Query("SELECT * FROM $TABLE_MEAL_LINKS WHERE id = :id")
    override fun findById(id: Long): MealLink?

    @Query("DELETE FROM $TABLE_MEAL_LINKS")
    override fun deleteAllEntries()

    @Transaction
    @Query("SELECT * FROM $TABLE_MEAL_LINKS WHERE isValid = 1 AND timestamp >= :timestamp AND referenceId IS NULL ORDER BY id DESC")
    fun getMealLinkLoadedFromTime(timestamp: Long): Single<List<MealLinkLoaded>>

    @Transaction
    @Query("SELECT * FROM $TABLE_MEAL_LINKS WHERE timestamp >= :timestamp AND referenceId IS NULL ORDER BY id DESC")
    fun getMealLinkLoadedIncludingInvalidFromTime(timestamp: Long): Single<List<MealLinkLoaded>>
}