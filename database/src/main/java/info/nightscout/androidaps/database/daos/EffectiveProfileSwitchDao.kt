package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_EFFECTIVE_PROFILE_SWITCHES
import info.nightscout.androidaps.database.entities.EffectiveProfileSwitch
import io.reactivex.Single

@Suppress("FunctionName")
@Dao
internal interface EffectiveProfileSwitchDao : TraceableDao<EffectiveProfileSwitch> {

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE id = :id")
    override fun findById(id: Long): EffectiveProfileSwitch?

    @Query("DELETE FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES")
    override fun deleteAllEntries()
}