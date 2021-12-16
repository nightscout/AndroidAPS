package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.OfflineEventDao
import info.nightscout.androidaps.database.entities.OfflineEvent
import info.nightscout.androidaps.database.interfaces.DBEntry

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