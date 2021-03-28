package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_BOLUSES
import info.nightscout.androidaps.database.entities.Bolus
import io.reactivex.Maybe
import io.reactivex.Single

@Suppress("FunctionName")
@Dao
internal interface BolusDao : TraceableDao<Bolus> {

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE id = :id")
    override fun findById(id: Long): Bolus?

    @Query("DELETE FROM $TABLE_BOLUSES")
    override fun deleteAllEntries()

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE isValid = 1 AND timestamp >= :timestamp AND referenceId IS NULL ORDER BY id DESC")
    fun getBolusesFromTime(timestamp: Long): Single<List<Bolus>>

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE timestamp >= :timestamp AND referenceId IS NULL ORDER BY id DESC")
    fun getBolusesIncludingInvalidFromTime(timestamp: Long): Single<List<Bolus>>

    // This query will be used with v3 to get all changed records
    @Query("SELECT * FROM $TABLE_BOLUSES WHERE id > :id AND referenceId IS NULL OR id IN (SELECT DISTINCT referenceId FROM $TABLE_BOLUSES WHERE id > :id) ORDER BY id ASC")
    fun getModifiedFrom(id: Long): Single<List<Bolus>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_BOLUSES WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<Bolus>

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<Bolus>
}