package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_THERAPY_EVENTS
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.TherapyEvent
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single

@Dao
internal interface TherapyEventDao : TraceableDao<TherapyEvent> {

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE id = :id")
    override fun findById(id: Long): TherapyEvent?

    @Query("DELETE FROM $TABLE_THERAPY_EVENTS")
    override fun deleteAllEntries()

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE type = :type AND timestamp = :timestamp AND referenceId IS NULL")
    fun findByTimestamp(type: TherapyEvent.Type, timestamp: Long): TherapyEvent?

}