package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.UserEntryDao
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.interfaces.DBEntry

internal class DelegatedUserEntryDao(changes: MutableList<DBEntry>, private val dao: UserEntryDao) : DelegatedDao(changes), UserEntryDao by dao {

    override fun insert(userEntry: UserEntry) {
        changes.add(userEntry)
        return dao.insert(userEntry)
    }

}