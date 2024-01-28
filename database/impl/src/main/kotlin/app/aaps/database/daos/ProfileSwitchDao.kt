package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.daos.workaround.ProfileSwitchDaoWorkaround
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.TABLE_PROFILE_SWITCHES
import app.aaps.database.entities.data.checkSanity
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

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
    fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE timestamp = :timestamp AND referenceId IS NULL")
    fun findByTimestamp(timestamp: Long): ProfileSwitch?

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE unlikely(nightscoutId = :nsId) AND likely(referenceId IS NULL)")
    fun findByNSId(nsId: String): ProfileSwitch?

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE unlikely(timestamp <= :timestamp) AND unlikely((timestamp + duration) > :timestamp) AND likely(referenceId IS NULL) AND likely(isValid = 1) ORDER BY timestamp DESC LIMIT 1")
    fun getTemporaryProfileSwitchActiveAt(timestamp: Long): Maybe<ProfileSwitch>

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE unlikely(timestamp <= :timestamp) AND unlikely(duration = 0) AND likely(referenceId IS NULL) AND likely(isValid = 1) ORDER BY timestamp DESC LIMIT 1")
    fun getPermanentProfileSwitchActiveAt(timestamp: Long): Maybe<ProfileSwitch>

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getAllProfileSwitches(): Single<List<ProfileSwitch>>

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE unlikely(timestamp >= :timestamp) AND likely(referenceId IS NULL) ORDER BY timestamp ASC")
    fun getProfileSwitchDataIncludingInvalidFromTime(timestamp: Long): Single<List<ProfileSwitch>>

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE unlikely(timestamp >= :timestamp) AND likely(isValid = 1) AND likely(referenceId IS NULL) ORDER BY timestamp ASC")
    fun getProfileSwitchDataFromTime(timestamp: Long): Single<List<ProfileSwitch>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<ProfileSwitch>

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<ProfileSwitch>

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<ProfileSwitch>
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