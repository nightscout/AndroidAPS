package app.aaps.database.daos.delegated

import app.aaps.database.daos.UserEntryDao
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.interfaces.DBEntry

internal class DelegatedUserEntryDao(changes: MutableList<DBEntry>, private val dao: UserEntryDao) : DelegatedDao(changes), UserEntryDao by dao {

    override fun insert(userEntry: UserEntry) {
        changes.add(userEntry)
        return dao.insert(userEntry)
    }

}