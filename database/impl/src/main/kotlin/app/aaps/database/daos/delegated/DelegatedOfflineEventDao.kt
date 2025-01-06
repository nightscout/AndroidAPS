package app.aaps.database.daos.delegated

import app.aaps.database.daos.OfflineEventDao
import app.aaps.database.entities.OfflineEvent
import app.aaps.database.entities.interfaces.DBEntry

internal class DelegatedOfflineEventDao(changes: MutableList<DBEntry>, private val dao: OfflineEventDao) : DelegatedDao(changes), OfflineEventDao by dao {

    override fun insertNewEntry(entry: OfflineEvent): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: OfflineEvent): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}