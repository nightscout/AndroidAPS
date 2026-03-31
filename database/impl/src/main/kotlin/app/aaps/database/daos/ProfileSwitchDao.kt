package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.daos.workaround.ProfileSwitchDaoWorkaround
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.TABLE_PROFILE_SWITCHES
import app.aaps.database.entities.data.checkSanity

@Dao
internal interface ProfileSwitchDao : ProfileSwitchDaoWorkaround {

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE id = :id")
    override fun findById(id: Long): ProfileSwitch?

    @Query("DELETE FROM $TABLE_PROFILE_SWITCHES")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_PROFILE_SWITCHES WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_PROFILE_SWITCHES WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT id FROM $TABLE_PROFILE_SWITCHES ORDER BY id DESC limit 1")
    suspend fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE timestamp = :timestamp AND referenceId IS NULL")
    suspend fun findByTimestamp(timestamp: Long): ProfileSwitch?

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE (nightscoutId = :nsId) AND (referenceId IS NULL)")
    suspend fun findByNSId(nsId: String): ProfileSwitch?

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE (timestamp <= :timestamp) AND ((timestamp + duration) > :timestamp) AND (referenceId IS NULL) AND (isValid = 1) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getTemporaryProfileSwitchActiveAt(timestamp: Long): ProfileSwitch?

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE (timestamp <= :timestamp) AND (duration = 0) AND (referenceId IS NULL) AND (isValid = 1) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getPermanentProfileSwitchActiveAt(timestamp: Long): ProfileSwitch?

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getAllProfileSwitches(): List<ProfileSwitch>

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE (timestamp >= :timestamp) AND (referenceId IS NULL) ORDER BY timestamp ASC")
    suspend fun getProfileSwitchDataIncludingInvalidFromTime(timestamp: Long): List<ProfileSwitch>

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE (timestamp >= :timestamp) AND (isValid = 1) AND (referenceId IS NULL) ORDER BY timestamp ASC")
    suspend fun getProfileSwitchDataFromTime(timestamp: Long): List<ProfileSwitch>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE id > :id ORDER BY id ASC limit 1")
    suspend fun getNextModifiedOrNewAfter(id: Long): ProfileSwitch?

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE id = :referenceId")
    suspend fun getCurrentFromHistoric(referenceId: Long): ProfileSwitch?

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<ProfileSwitch>
}

internal fun ProfileSwitchDao.insertNewEntryImpl(entry: ProfileSwitch): Long {
    if (!entry.basalBlocks.checkSanity()) throw IllegalArgumentException("Sanity check failed for basal blocks.")
    if (!entry.icBlocks.checkSanity()) throw IllegalArgumentException("Sanity check failed for IC blocks.")
    if (!entry.isfBlocks.checkSanity()) throw IllegalArgumentException("Sanity check failed for ISF blocks.")
    if (!entry.targetBlocks.checkSanity()) throw IllegalArgumentException("Sanity check failed for target blocks.")
    return (this as TraceableDao<ProfileSwitch>).insertNewEntryImpl(entry)
}

internal fun ProfileSwitchDao.updateExistingEntryImpl(entry: ProfileSwitch): Long {
    if (!entry.basalBlocks.checkSanity()) throw IllegalArgumentException("Sanity check failed for basal blocks.")
    if (!entry.icBlocks.checkSanity()) throw IllegalArgumentException("Sanity check failed for IC blocks.")
    if (!entry.isfBlocks.checkSanity()) throw IllegalArgumentException("Sanity check failed for ISF blocks.")
    if (!entry.targetBlocks.checkSanity()) throw IllegalArgumentException("Sanity check failed for target blocks.")
    return (this as TraceableDao<ProfileSwitch>).updateExistingEntryImpl(entry)
}
