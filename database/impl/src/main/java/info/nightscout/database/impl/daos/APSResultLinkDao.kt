package info.nightscout.database.impl.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.APSResultLink
import app.aaps.database.entities.TABLE_APS_RESULTS
import app.aaps.database.entities.TABLE_APS_RESULT_LINKS

@Dao
internal interface APSResultLinkDao : TraceableDao<APSResultLink> {

    @Query("SELECT * FROM $TABLE_APS_RESULT_LINKS WHERE id = :id")
    override fun findById(id: Long): APSResultLink?

    @Query("DELETE FROM $TABLE_APS_RESULTS")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_APS_RESULTS WHERE dateCreated < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_APS_RESULTS WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT * FROM $TABLE_APS_RESULT_LINKS WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<APSResultLink>
}