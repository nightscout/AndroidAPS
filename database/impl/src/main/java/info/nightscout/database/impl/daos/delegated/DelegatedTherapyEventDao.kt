package info.nightscout.database.impl.daos.delegated

import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.interfaces.DBEntry
import info.nightscout.database.impl.daos.TherapyEventDao

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