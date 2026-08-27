package app.aaps.database.daos.delegated

import app.aaps.database.daos.TherapyEventDao
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.interfaces.DBEntry

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