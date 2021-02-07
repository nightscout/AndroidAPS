package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_EXTENDED_BOLUSES
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.ExtendedBolus
import io.reactivex.Flowable
import io.reactivex.Single

@Suppress("FunctionName")
@Dao
internal interface ExtendedBolusDao : TraceableDao<ExtendedBolus> {

    @Query("SELECT * FROM $TABLE_EXTENDED_BOLUSES WHERE id = :id")
    override fun findById(id: Long): ExtendedBolus?

    @Query("DELETE FROM $TABLE_EXTENDED_BOLUSES")
    override fun deleteAllEntries()
}