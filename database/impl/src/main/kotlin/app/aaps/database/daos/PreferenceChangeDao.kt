package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.aaps.database.entities.PreferenceChange
import app.aaps.database.entities.TABLE_PREFERENCE_CHANGES

@Dao
interface PreferenceChangeDao {

    @Insert
    fun insert(preferenceChange: PreferenceChange)

    @Query("DELETE FROM $TABLE_PREFERENCE_CHANGES WHERE timestamp < :than")
    fun deleteOlderThan(than: Long): Int

    @Query("SELECT * FROM $TABLE_PREFERENCE_CHANGES WHERE timestamp > :since AND timestamp <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<PreferenceChange>

}