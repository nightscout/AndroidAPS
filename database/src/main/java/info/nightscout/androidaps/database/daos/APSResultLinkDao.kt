package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_APS_RESULTS
import info.nightscout.androidaps.database.TABLE_APS_RESULT_LINKS
import info.nightscout.androidaps.database.entities.APSResultLink
import io.reactivex.Single

@Suppress("FunctionName")
@Dao
internal interface APSResultLinkDao : TraceableDao<APSResultLink> {

    @Query("SELECT * FROM $TABLE_APS_RESULT_LINKS WHERE id = :id")
    override fun findById(id: Long): APSResultLink?

    @Query("DELETE FROM $TABLE_APS_RESULTS")
    override fun deleteAllEntries()
}