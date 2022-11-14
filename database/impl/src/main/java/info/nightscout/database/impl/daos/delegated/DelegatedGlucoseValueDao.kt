package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.GlucoseValueDao
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.interfaces.DBEntry

internal class DelegatedGlucoseValueDao(changes: MutableList<DBEntry>, private val dao: GlucoseValueDao) : DelegatedDao(changes), GlucoseValueDao by dao {

    override fun insertNewEntry(entry: GlucoseValue): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: GlucoseValue): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}