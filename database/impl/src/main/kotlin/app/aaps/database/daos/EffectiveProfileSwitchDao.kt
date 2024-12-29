package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.TABLE_EFFECTIVE_PROFILE_SWITCHES
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

@Dao
internal interface EffectiveProfileSwitchDao : TraceableDao<EffectiveProfileSwitch> {

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE id = :id")
    override fun findById(id: Long): EffectiveProfileSwitch?

    @Query("DELETE FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT id FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES ORDER BY id DESC limit 1")
    fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE unlikely(timestamp = :timestamp) AND likely(referenceId IS NULL)")
    fun findByTimestamp(timestamp: Long): EffectiveProfileSwitch?

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE unlikely(nightscoutId = :nsId) AND likely(referenceId IS NULL)")
    fun findByNSId(nsId: String): EffectiveProfileSwitch?

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC LIMIT 1")
    fun getOldestEffectiveProfileSwitchRecord(): Maybe<EffectiveProfileSwitch>

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE unlikely(:timestamp >= timestamp) AND likely(referenceId IS NULL) AND likely(isValid = 1) ORDER BY timestamp DESC LIMIT 1")
    fun getEffectiveProfileSwitchActiveAt(timestamp: Long): Maybe<EffectiveProfileSwitch>

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE unlikely(timestamp >= :timestamp) AND likely(isValid = 1) AND likely(referenceId IS NULL) ORDER BY timestamp ASC")
    fun getEffectiveProfileSwitchDataFromTime(timestamp: Long): Single<List<EffectiveProfileSwitch>>

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE unlikely(timestamp BETWEEN :start AND :end) AND likely(isValid = 1) AND likely(referenceId IS NULL) ORDER BY timestamp ASC")
    fun getEffectiveProfileSwitchDataFromTimeToTime(start: Long, end: Long): Single<List<EffectiveProfileSwitch>>

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE unlikely(timestamp >= :timestamp) AND likely(referenceId IS NULL) ORDER BY timestamp ASC")
    fun getEffectiveProfileSwitchDataIncludingInvalidFromTime(timestamp: Long): Single<List<EffectiveProfileSwitch>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<EffectiveProfileSwitch>

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<EffectiveProfileSwitch>

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<EffectiveProfileSwitch>

}