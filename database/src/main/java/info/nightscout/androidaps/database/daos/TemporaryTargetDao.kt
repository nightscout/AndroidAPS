package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_TEMPORARY_TARGETS
import info.nightscout.androidaps.database.entities.TemporaryTarget
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single

@Suppress("FunctionName")
@Dao
internal interface TemporaryTargetDao : TraceableDao<TemporaryTarget> {

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE id = :id")
    override fun findById(id: Long): TemporaryTarget?

    @Query("DELETE FROM $TABLE_TEMPORARY_TARGETS")
    override fun deleteAllEntries()

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE nightscoutId = :nsId AND referenceId IS NULL")
    fun findByNSIdMaybe(nsId: String): Maybe<TemporaryTarget>

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE timestamp <= :timestamp AND (timestamp + duration) > :timestamp AND referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getTemporaryTargetActiveAt(timestamp: Long): TemporaryTarget?

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun compatGetTemporaryTargetDataFromTime(timestamp: Long): Single<List<TemporaryTarget>>

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun compatGetTemporaryTargetData(): List<TemporaryTarget>

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE referenceId = :id ORDER BY id DESC LIMIT 1")
    fun getLastHistoryRecord(id: Long): TemporaryTarget?

    @Query("SELECT * FROM $TABLE_TEMPORARY_TARGETS WHERE id > :id AND referenceId IS NULL OR id IN (SELECT DISTINCT referenceId FROM $TABLE_TEMPORARY_TARGETS WHERE id > :id) ORDER BY id ASC")
    fun getModifiedFrom(id: Long): Single<List<TemporaryTarget>>
}