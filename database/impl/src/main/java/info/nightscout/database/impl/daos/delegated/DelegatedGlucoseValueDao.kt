package info.nightscout.database.impl.daos.delegated

import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.interfaces.DBEntry
import info.nightscout.database.impl.daos.GlucoseValueDao

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