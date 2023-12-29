package app.aaps.database.impl.daos.workaround

import androidx.room.Transaction
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.impl.daos.ProfileSwitchDao
import app.aaps.database.impl.daos.TraceableDao
import app.aaps.database.impl.daos.insertNewEntryImpl
import app.aaps.database.impl.daos.updateExistingEntryImpl

internal interface ProfileSwitchDaoWorkaround : TraceableDao<ProfileSwitch> {

    @Transaction
    override fun insertNewEntry(entry: ProfileSwitch): Long =
        (this as ProfileSwitchDao).insertNewEntryImpl(entry)

    @Transaction
    override fun updateExistingEntry(entry: ProfileSwitch): Long =
        (this as ProfileSwitchDao).updateExistingEntryImpl(entry)
}
