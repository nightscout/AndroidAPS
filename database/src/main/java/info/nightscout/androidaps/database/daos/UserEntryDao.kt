package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_USER_ENTRY
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import io.reactivex.Single

@Dao
interface UserEntryDao {

    @Insert
    fun insert(userEntry: UserEntry)

    @Query("SELECT * FROM $TABLE_USER_ENTRY ORDER BY id DESC")
    fun getAll(): Single<List<UserEntry>>

    @Query("SELECT * FROM $TABLE_USER_ENTRY WHERE timestamp >= :timestamp ORDER BY id DESC")
    fun getUserEntryDataFromTime(timestamp: Long): Single<List<UserEntry>>

    @Query("SELECT * FROM $TABLE_USER_ENTRY WHERE timestamp >= :timestamp AND source != :excludeSource ORDER BY id DESC")
    fun getUserEntryFilteredDataFromTime(excludeSource: Sources, timestamp: Long): Single<List<UserEntry>>

}