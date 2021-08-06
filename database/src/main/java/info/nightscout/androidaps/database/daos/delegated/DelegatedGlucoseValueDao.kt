package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.GlucoseValueDao
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.interfaces.DBEntry

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