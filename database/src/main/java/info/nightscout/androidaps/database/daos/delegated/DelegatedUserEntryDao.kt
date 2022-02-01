package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.UserEntryDao
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.interfaces.DBEntry

internal class DelegatedUserEntryDao(changes: MutableList<DBEntry>, private val dao: UserEntryDao) : DelegatedDao(changes), UserEntryDao by dao {

    override fun insert(userEntry: UserEntry) {
        changes.add(userEntry)
        return dao.insert(userEntry)
    }

}