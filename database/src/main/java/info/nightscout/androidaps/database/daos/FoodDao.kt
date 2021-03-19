package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_FOODS
import info.nightscout.androidaps.database.entities.Food
import io.reactivex.Single

@Suppress("FunctionName")
@Dao
internal interface FoodDao : TraceableDao<Food> {

    @Query("SELECT * FROM $TABLE_FOODS WHERE id = :id")
    override fun findById(id: Long): Food?

    @Query("DELETE FROM $TABLE_FOODS")
    override fun deleteAllEntries()

    @Query("SELECT * FROM $TABLE_FOODS WHERE nightscoutId = :nsId AND referenceId IS NULL")
    fun findByNSId(nsId: String): Food?

    @Query("SELECT * FROM $TABLE_FOODS WHERE isValid = 1 AND referenceId IS NULL ORDER BY id DESC")
    fun getFoodData(): Single<List<Food>>

}