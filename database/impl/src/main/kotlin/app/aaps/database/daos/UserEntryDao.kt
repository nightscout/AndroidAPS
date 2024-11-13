package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.aaps.database.entities.TABLE_USER_ENTRY
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.UserEntry.Sources
import io.reactivex.rxjava3.core.Single

@Dao
interface UserEntryDao {

    @Insert
    fun insert(userEntry: UserEntry)

    @Query("DELETE FROM $TABLE_USER_ENTRY WHERE timestamp < :than")
    fun deleteOlderThan(than: Long): Int

    @Query("SELECT * FROM $TABLE_USER_ENTRY WHERE timestamp >= :timestamp ORDER BY timestamp DESC")
    fun getUserEntryDataFromTime(timestamp: Long): Single<List<UserEntry>>

    @Query("SELECT * FROM $TABLE_USER_ENTRY WHERE unlikely(timestamp >= :timestamp) AND likely(source != :excludeSource) ORDER BY timestamp DESC")
    fun getUserEntryFilteredDataFromTime(excludeSource: Sources, timestamp: Long): Single<List<UserEntry>>

}