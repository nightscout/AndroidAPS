package app.aaps.database.daos.delegated

import app.aaps.database.daos.GlucoseValueDao
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.interfaces.DBEntry

internal class DelegatedGlucoseValueDao(changes: MutableList<DBEntry>, private val dao: GlucoseValueDao) : DelegatedDao(changes), GlucoseValueDao by dao {

    override suspend fun insertNewEntry(entry: GlucoseValue): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override suspend fun updateExistingEntry(entry: GlucoseValue): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}