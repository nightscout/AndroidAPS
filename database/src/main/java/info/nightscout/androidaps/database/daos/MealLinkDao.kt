package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_MEAL_LINKS
import info.nightscout.androidaps.database.entities.MealLink
import io.reactivex.Single

@Suppress("FunctionName")
@Dao
internal interface MealLinkDao : TraceableDao<MealLink> {

    @Query("SELECT * FROM $TABLE_MEAL_LINKS WHERE id = :id")
    override fun findById(id: Long): MealLink?

    @Query("DELETE FROM $TABLE_MEAL_LINKS")
    override fun deleteAllEntries()
}