package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.TABLE_EFFECTIVE_PROFILE_SWITCHES

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
    suspend fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE timestamp = :timestamp AND referenceId IS NULL")
    suspend fun findByTimestamp(timestamp: Long): EffectiveProfileSwitch?

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE nightscoutId = :nsId AND referenceId IS NULL")
    suspend fun findByNSId(nsId: String): EffectiveProfileSwitch?

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC LIMIT 1")
    suspend fun getOldestEffectiveProfileSwitchRecord(): EffectiveProfileSwitch?

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE :timestamp >= timestamp AND referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getEffectiveProfileSwitchActiveAt(timestamp: Long): EffectiveProfileSwitch?

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    suspend fun getEffectiveProfileSwitchDataFromTime(timestamp: Long): List<EffectiveProfileSwitch>

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE timestamp BETWEEN :start AND :end AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    suspend fun getEffectiveProfileSwitchDataFromTimeToTime(start: Long, end: Long): List<EffectiveProfileSwitch>

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE timestamp >= :timestamp AND referenceId IS NULL ORDER BY timestamp ASC")
    suspend fun getEffectiveProfileSwitchDataIncludingInvalidFromTime(timestamp: Long): List<EffectiveProfileSwitch>

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE referenceId IS NULL AND isValid = 1 ORDER BY timestamp ASC")
    suspend fun getAllEffectiveProfileSwitches(): List<EffectiveProfileSwitch>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE id > :id ORDER BY id ASC limit 1")
    suspend fun getNextModifiedOrNewAfter(id: Long): EffectiveProfileSwitch?

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE id = :referenceId")
    suspend fun getCurrentFromHistoric(referenceId: Long): EffectiveProfileSwitch?

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<EffectiveProfileSwitch>
}
