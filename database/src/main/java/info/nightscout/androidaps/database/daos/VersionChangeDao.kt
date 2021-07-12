package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_VERSION_CHANGES
import info.nightscout.androidaps.database.entities.VersionChange
import io.reactivex.Single

@Dao
interface VersionChangeDao {

    @Insert
    fun insert(versionChange: VersionChange)

    @Query("SELECT * FROM $TABLE_VERSION_CHANGES ORDER BY id DESC LIMIT 1")
    fun getMostRecentVersionChange(): VersionChange?

}