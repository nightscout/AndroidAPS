package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.TABLE_CARBS
import app.aaps.database.entities.embedments.InterfaceIDs
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

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
    fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_CARBS WHERE unlikely(nightscoutId = :nsId) AND likely(referenceId IS NULL)")
    fun getByNSId(nsId: String): Carbs?

    @Query("SELECT * FROM $TABLE_CARBS WHERE unlikely(timestamp = :timestamp) AND likely(referenceId IS NULL)")
    fun findByTimestamp(timestamp: Long): Carbs?

    @Query("SELECT * FROM $TABLE_CARBS WHERE unlikely(pumpId = :pumpId) AND likely(pumpType = :pumpType) AND likely(pumpSerial = :pumpSerial) AND likely(referenceId IS NULL)")
    fun findByPumpIds(pumpId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): Carbs?

    @Query("SELECT * FROM $TABLE_CARBS WHERE isValid = 1 AND referenceId IS NULL ORDER BY id DESC LIMIT 1")
    fun getLastCarbsRecordMaybe(): Maybe<Carbs>

    @Query("SELECT * FROM $TABLE_CARBS WHERE isValid = 1 AND referenceId IS NULL ORDER BY id ASC LIMIT 1")
    fun getOldestCarbsRecord(): Maybe<Carbs>

    @Query("SELECT * FROM $TABLE_CARBS WHERE likely(isValid = 1) AND unlikely(timestamp >= :timestamp) AND likely(referenceId IS NULL) ORDER BY id DESC")
    fun getCarbsFromTime(timestamp: Long): Single<List<Carbs>>

    @Query("SELECT * FROM $TABLE_CARBS WHERE likely(isValid = 1) AND unlikely((timestamp + duration) >= :timestamp) AND likely(referenceId IS NULL) ORDER BY id DESC")
    fun getCarbsFromTimeExpandable(timestamp: Long): Single<List<Carbs>>

    @Query("SELECT * FROM $TABLE_CARBS WHERE likely(isValid = 1) AND unlikely((timestamp + duration) > :from) AND unlikely(timestamp <= :to) AND likely(referenceId IS NULL) ORDER BY id DESC")
    fun getCarbsFromTimeToTimeExpandable(from: Long, to: Long): Single<List<Carbs>>

    @Query("SELECT * FROM $TABLE_CARBS WHERE unlikely(timestamp >= :timestamp) AND likely(referenceId IS NULL) ORDER BY id DESC")
    fun getCarbsIncludingInvalidFromTime(timestamp: Long): Single<List<Carbs>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_CARBS WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<Carbs>

    @Query("SELECT * FROM $TABLE_CARBS WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<Carbs>

    @Query("SELECT * FROM $TABLE_CARBS WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<Carbs>
}