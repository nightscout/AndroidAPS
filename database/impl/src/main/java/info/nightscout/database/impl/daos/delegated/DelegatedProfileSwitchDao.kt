package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.ProfileSwitchDao
import info.nightscout.database.entities.ProfileSwitch
import info.nightscout.database.entities.interfaces.DBEntry

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