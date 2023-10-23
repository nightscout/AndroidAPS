package app.aaps.database.impl.daos.delegated

import app.aaps.database.entities.OfflineEvent
import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.impl.daos.OfflineEventDao

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