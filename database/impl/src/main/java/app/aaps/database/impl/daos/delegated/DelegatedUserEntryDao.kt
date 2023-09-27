package app.aaps.database.impl.daos.delegated

import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.impl.daos.UserEntryDao

internal class DelegatedUserEntryDao(changes: MutableList<DBEntry>, private val dao: UserEntryDao) : DelegatedDao(changes), UserEntryDao by dao {

    override fun insert(userEntry: UserEntry) {
        changes.add(userEntry)
        return dao.insert(userEntry)
    }

}