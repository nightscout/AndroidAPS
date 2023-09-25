package info.nightscout.database.impl.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.Food
import app.aaps.database.entities.TABLE_FOODS
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

@Dao
internal interface FoodDao : TraceableDao<Food> {

    @Query("SELECT * FROM $TABLE_FOODS WHERE id = :id")
    override fun findById(id: Long): Food?

    @Query("DELETE FROM $TABLE_FOODS")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_FOODS WHERE dateCreated < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_FOODS WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT id FROM $TABLE_FOODS ORDER BY id DESC limit 1")
    fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_FOODS WHERE nightscoutId = :nsId AND referenceId IS NULL")
    fun findByNSId(nsId: String): Food?

    @Query("SELECT * FROM $TABLE_FOODS WHERE isValid = 1 AND referenceId IS NULL ORDER BY id DESC")
    fun getFoodData(): Single<List<Food>>

    // This query will be used with v3 to get all changed records
    @Query("SELECT * FROM $TABLE_FOODS WHERE id > :id AND referenceId IS NULL OR id IN (SELECT DISTINCT referenceId FROM $TABLE_FOODS WHERE id > :id) ORDER BY id ASC")
    fun getModifiedFrom(id: Long): Single<List<Food>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_FOODS WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<Food>

    @Query("SELECT * FROM $TABLE_FOODS WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<Food>
}