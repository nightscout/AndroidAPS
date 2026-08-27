package app.aaps.database.daos.delegated

import app.aaps.database.daos.ProfileSwitchDao
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.interfaces.DBEntry

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