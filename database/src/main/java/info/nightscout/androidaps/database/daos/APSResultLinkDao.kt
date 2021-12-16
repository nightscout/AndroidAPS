package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_APS_RESULTS
import info.nightscout.androidaps.database.TABLE_APS_RESULT_LINKS
import info.nightscout.androidaps.database.entities.APSResultLink

@Suppress("FunctionName")
@Dao
internal interface APSResultLinkDao : TraceableDao<APSResultLink> {

    @Query("SELECT * FROM $TABLE_APS_RESULT_LINKS WHERE id = :id")
    override fun findById(id: Long): APSResultLink?

    @Query("DELETE FROM $TABLE_APS_RESULTS")
    override fun deleteAllEntries()

    @Query("SELECT * FROM $TABLE_APS_RESULT_LINKS WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<APSResultLink>
}