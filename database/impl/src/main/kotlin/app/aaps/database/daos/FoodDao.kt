package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.Food
import app.aaps.database.entities.TABLE_FOODS

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
    suspend fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_FOODS WHERE nightscoutId = :nsId AND referenceId IS NULL")
    suspend fun findByNSId(nsId: String): Food?

    @Query("SELECT * FROM $TABLE_FOODS WHERE isValid = 1 AND referenceId IS NULL ORDER BY id DESC")
    suspend fun getFoodData(): List<Food>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_FOODS WHERE id > :id ORDER BY id ASC limit 1")
    suspend fun getNextModifiedOrNewAfter(id: Long): Food?

    @Query("SELECT * FROM $TABLE_FOODS WHERE id = :referenceId")
    suspend fun getCurrentFromHistoric(referenceId: Long): Food?
}