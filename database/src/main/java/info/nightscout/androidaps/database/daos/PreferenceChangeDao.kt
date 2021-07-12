package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_PREFERENCE_CHANGES
import info.nightscout.androidaps.database.entities.PreferenceChange
import io.reactivex.Single

@Dao
interface PreferenceChangeDao {

    @Insert
    fun insert(preferenceChange: PreferenceChange)

}