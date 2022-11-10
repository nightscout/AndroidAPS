package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.TherapyEventDao
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.interfaces.DBEntry

internal class DelegatedTherapyEventDao(changes: MutableList<DBEntry>, private val dao: TherapyEventDao) : DelegatedDao(changes), TherapyEventDao by dao {

    override fun insertNewEntry(entry: TherapyEvent): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: TherapyEvent): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}