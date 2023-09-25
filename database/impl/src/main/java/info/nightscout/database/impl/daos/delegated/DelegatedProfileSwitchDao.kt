package info.nightscout.database.impl.daos.delegated

import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.interfaces.DBEntry
import info.nightscout.database.impl.daos.ProfileSwitchDao

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