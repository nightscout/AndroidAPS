package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_GLUCOSE_VALUES
import info.nightscout.androidaps.database.TABLE_PREFERENCE_CHANGES
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.PreferenceChange
import io.reactivex.rxjava3.core.Single

@Dao
interface PreferenceChangeDao {

    @Insert
    fun insert(preferenceChange: PreferenceChange)

    @Query("SELECT * FROM $TABLE_PREFERENCE_CHANGES WHERE timestamp > :since AND timestamp <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<PreferenceChange>

}