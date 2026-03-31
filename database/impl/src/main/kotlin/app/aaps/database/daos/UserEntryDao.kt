package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.aaps.database.entities.TABLE_USER_ENTRY
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.UserEntry.Sources

@Dao
interface UserEntryDao {

    @Insert
    fun insert(userEntry: UserEntry)

    @Query("DELETE FROM $TABLE_USER_ENTRY WHERE timestamp < :than")
    fun deleteOlderThan(than: Long): Int

    @Query("SELECT * FROM $TABLE_USER_ENTRY WHERE timestamp >= :timestamp ORDER BY timestamp DESC")
    suspend fun getUserEntryDataFromTime(timestamp: Long): List<UserEntry>

    @Query("SELECT * FROM $TABLE_USER_ENTRY WHERE (timestamp >= :timestamp) AND (source != :excludeSource) ORDER BY timestamp DESC")
    suspend fun getUserEntryFilteredDataFromTime(excludeSource: Sources, timestamp: Long): List<UserEntry>
}
