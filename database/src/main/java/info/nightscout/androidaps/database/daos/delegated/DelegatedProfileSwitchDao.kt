package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.ProfileSwitchDao
import info.nightscout.androidaps.database.entities.ProfileSwitch
import info.nightscout.androidaps.database.interfaces.DBEntry

internal class DelegatedProfileSwitchDao(changes: MutableList<DBEntry>, private val dao: ProfileSwitchDao) : DelegatedDao(changes), ProfileSwitchDao by dao {

    override fun insertNewEntry(entry: ProfileSwitch): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: ProfileSwitch): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}