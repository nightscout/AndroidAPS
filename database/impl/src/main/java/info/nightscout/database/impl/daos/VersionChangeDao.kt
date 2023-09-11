package info.nightscout.database.impl.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import info.nightscout.database.entities.TABLE_VERSION_CHANGES
import info.nightscout.database.entities.VersionChange

@Dao
interface VersionChangeDao {

    @Insert
    fun insert(versionChange: VersionChange)

    @Query("DELETE FROM $TABLE_VERSION_CHANGES WHERE timestamp < :than")
    fun deleteOlderThan(than: Long): Int

    @Query("SELECT * FROM $TABLE_VERSION_CHANGES ORDER BY id DESC LIMIT 1")
    fun getMostRecentVersionChange(): VersionChange?

    @Query("SELECT * FROM $TABLE_VERSION_CHANGES WHERE timestamp > :since AND timestamp <= :until LIMIT :limit OFFSET :offset")
    fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<VersionChange>

}