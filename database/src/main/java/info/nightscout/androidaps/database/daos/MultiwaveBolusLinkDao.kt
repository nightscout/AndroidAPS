package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_GLUCOSE_VALUES
import info.nightscout.androidaps.database.TABLE_MULTIWAVE_BOLUS_LINKS
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.MultiwaveBolusLink

@Suppress("FunctionName")
@Dao
internal interface MultiwaveBolusLinkDao : TraceableDao<MultiwaveBolusLink> {

    @Query("SELECT * FROM $TABLE_MULTIWAVE_BOLUS_LINKS WHERE id = :id")
    override fun findById(id: Long): MultiwaveBolusLink?

    @Query("DELETE FROM $TABLE_MULTIWAVE_BOLUS_LINKS")
    override fun deleteAllEntries()

    @Query("SELECT * FROM $TABLE_MULTIWAVE_BOLUS_LINKS WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<MultiwaveBolusLink>
}