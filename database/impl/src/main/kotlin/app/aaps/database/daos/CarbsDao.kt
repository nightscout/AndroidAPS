package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.TABLE_CARBS
import app.aaps.database.entities.embedments.InterfaceIDs

@Dao
internal interface CarbsDao : TraceableDao<Carbs> {

    @Query("SELECT * FROM $TABLE_CARBS WHERE id = :id")
    override fun findById(id: Long): Carbs?

    @Query("DELETE FROM $TABLE_CARBS")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_CARBS WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_CARBS WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT id FROM $TABLE_CARBS ORDER BY id DESC limit 1")
    suspend fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_CARBS WHERE (nightscoutId = :nsId) AND (referenceId IS NULL)")
    suspend fun getByNSId(nsId: String): Carbs?

    @Query("SELECT * FROM $TABLE_CARBS WHERE (timestamp = :timestamp) AND (referenceId IS NULL)")
    suspend fun findByTimestamp(timestamp: Long): Carbs?

    @Query("SELECT * FROM $TABLE_CARBS WHERE (pumpId = :pumpId) AND (pumpType = :pumpType) AND (pumpSerial = :pumpSerial) AND (referenceId IS NULL)")
    fun findByPumpIds(pumpId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): Carbs?

    @Query("SELECT * FROM $TABLE_CARBS WHERE isValid = 1 AND referenceId IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun getLastCarbsRecord(): Carbs?

    @Query("SELECT * FROM $TABLE_CARBS WHERE isValid = 1 AND referenceId IS NULL ORDER BY id ASC LIMIT 1")
    suspend fun getOldestCarbsRecord(): Carbs?

    @Query("SELECT * FROM $TABLE_CARBS WHERE (isValid = 1) AND (timestamp >= :timestamp) AND (referenceId IS NULL) ORDER BY id DESC")
    suspend fun getCarbsFromTime(timestamp: Long): List<Carbs>

    @Query("SELECT * FROM $TABLE_CARBS WHERE (isValid = 1) AND ((timestamp + duration) >= :timestamp) AND (referenceId IS NULL) ORDER BY id DESC")
    suspend fun getCarbsFromTimeExpandable(timestamp: Long): List<Carbs>

    @Query("SELECT * FROM $TABLE_CARBS WHERE (isValid = 1) AND ((timestamp + duration) > :from) AND (timestamp <= :to) AND (referenceId IS NULL) ORDER BY id DESC")
    suspend fun getCarbsFromTimeToTimeExpandable(from: Long, to: Long): List<Carbs>

    @Query("SELECT * FROM $TABLE_CARBS WHERE (timestamp >= :timestamp) AND (referenceId IS NULL) ORDER BY id DESC")
    suspend fun getCarbsIncludingInvalidFromTime(timestamp: Long): List<Carbs>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_CARBS WHERE id > :id ORDER BY id ASC limit 1")
    suspend fun getNextModifiedOrNewAfter(id: Long): Carbs?

    @Query("SELECT * FROM $TABLE_CARBS WHERE id = :referenceId")
    suspend fun getCurrentFromHistoric(referenceId: Long): Carbs?

    @Query("SELECT * FROM $TABLE_CARBS WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<Carbs>
}
