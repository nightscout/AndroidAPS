package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_BOLUSES
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.Bolus
import io.reactivex.Single

@Suppress("FunctionName")
@Dao
internal interface BolusDao : TraceableDao<Bolus> {

    @Query("SELECT * FROM $TABLE_BOLUSES WHERE id = :id")
    override fun findById(id: Long): Bolus?

    @Query("DELETE FROM $TABLE_BOLUSES")
    override fun deleteAllEntries()
}