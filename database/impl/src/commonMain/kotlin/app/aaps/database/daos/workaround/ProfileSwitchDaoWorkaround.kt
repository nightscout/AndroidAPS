package app.aaps.database.daos.workaround

import androidx.room.Transaction
import app.aaps.database.daos.ProfileSwitchDao
import app.aaps.database.daos.TraceableDao
import app.aaps.database.daos.insertNewEntryImpl
import app.aaps.database.daos.updateExistingEntryImpl
import app.aaps.database.entities.ProfileSwitch

internal interface ProfileSwitchDaoWorkaround : TraceableDao<ProfileSwitch> {

    @Transaction
    override fun insertNewEntry(entry: ProfileSwitch): Long =
        (this as ProfileSwitchDao).insertNewEntryImpl(entry)

    @Transaction
    override fun updateExistingEntry(entry: ProfileSwitch): Long =
        (this as ProfileSwitchDao).updateExistingEntryImpl(entry)
}
