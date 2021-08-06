package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_CARBS
import info.nightscout.androidaps.database.entities.Carbs
import io.reactivex.Maybe
import io.reactivex.Single

@Suppress("FunctionName")
@Dao
internal interface CarbsDao : TraceableDao<Carbs> {

    @Query("SELECT * FROM $TABLE_CARBS WHERE id = :id")
    override fun findById(id: Long): Carbs?

    @Query("DELETE FROM $TABLE_CARBS")
    override fun deleteAllEntries()

    @Query("SELECT id FROM $TABLE_CARBS ORDER BY id DESC limit 1")
    fun getLastId(): Maybe<Long>

    @Query("SELECT * FROM $TABLE_CARBS WHERE nightscoutId = :nsId AND referenceId IS NULL")
    fun findByNSId(nsId: String): Carbs?

    @Query("SELECT * FROM $TABLE_CARBS WHERE timestamp = :timestamp AND referenceId IS NULL")
    fun findByTimestamp(timestamp: Long): Carbs?

    @Query("SELECT * FROM $TABLE_CARBS WHERE isValid = 1 AND referenceId IS NULL ORDER BY id DESC LIMIT 1")
    fun getLastCarbsRecord(): Carbs?

    @Query("SELECT * FROM $TABLE_CARBS WHERE isValid = 1 AND referenceId IS NULL ORDER BY id DESC LIMIT 1")
    fun getLastCarbsRecordMaybe(): Maybe<Carbs>

    @Query("SELECT * FROM $TABLE_CARBS WHERE isValid = 1 AND referenceId IS NULL ORDER BY id ASC LIMIT 1")
    fun getOldestCarbsRecord(): Carbs?

    @Query("SELECT * FROM $TABLE_CARBS WHERE isValid = 1 AND timestamp >= :timestamp AND referenceId IS NULL ORDER BY id DESC")
    fun getCarbsFromTime(timestamp: Long): Single<List<Carbs>>

    @Query("SELECT * FROM $TABLE_CARBS WHERE isValid = 1 AND timestamp + duration >= :timestamp AND referenceId IS NULL ORDER BY id DESC")
    fun getCarbsFromTimeExpandable(timestamp: Long): Single<List<Carbs>>

    @Query("SELECT * FROM $TABLE_CARBS WHERE isValid = 1 AND timestamp >= :from AND timestamp <= :to AND referenceId IS NULL ORDER BY id DESC")
    fun getCarbsFromTimeToTime(from: Long, to: Long): Single<List<Carbs>>

    @Query("SELECT * FROM $TABLE_CARBS WHERE isValid = 1 AND timestamp + duration >= :from AND timestamp <= :to AND referenceId IS NULL ORDER BY id DESC")
    fun getCarbsFromTimeToTimeExpandable(from: Long, to: Long): Single<List<Carbs>>

    @Query("SELECT * FROM $TABLE_CARBS WHERE timestamp >= :timestamp AND referenceId IS NULL ORDER BY id DESC")
    fun getCarbsIncludingInvalidFromTime(timestamp: Long): Single<List<Carbs>>

    @Query("SELECT * FROM $TABLE_CARBS WHERE timestamp + duration >= :timestamp AND referenceId IS NULL ORDER BY id DESC")
    fun getCarbsIncludingInvalidFromTimeExpandable(timestamp: Long): Single<List<Carbs>>

    @Query("SELECT * FROM $TABLE_CARBS WHERE timestamp >= :from AND timestamp <= :to AND referenceId IS NULL ORDER BY id DESC")
    fun getCarbsIncludingInvalidFromTimeToTime(from: Long, to: Long): Single<List<Carbs>>

    @Query("SELECT * FROM $TABLE_CARBS WHERE timestamp + duration >= :from AND timestamp <= :to AND referenceId IS NULL ORDER BY id DESC")
    fun getCarbsIncludingInvalidFromTimeToTimeExpandable(from: Long, to: Long): Single<List<Carbs>>

    // This query will be used with v3 to get all changed records
    @Query("SELECT * FROM $TABLE_CARBS WHERE id > :id AND referenceId IS NULL OR id IN (SELECT DISTINCT referenceId FROM $TABLE_CARBS WHERE id > :id) ORDER BY id ASC")
    fun getModifiedFrom(id: Long): Single<List<Carbs>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_CARBS WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<Carbs>

    @Query("SELECT * FROM $TABLE_CARBS WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<Carbs>
}