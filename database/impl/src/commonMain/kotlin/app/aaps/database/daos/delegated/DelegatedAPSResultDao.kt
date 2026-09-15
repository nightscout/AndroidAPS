package app.aaps.database.daos.delegated

import app.aaps.database.daos.APSResultDao
import app.aaps.database.entities.interfaces.DBEntry

internal class DelegatedAPSResultDao(changes: MutableList<DBEntry>, private val dao: APSResultDao) : DelegatedDao(changes), APSResultDao by dao {

    override suspend fun insertNewEntry(entry: app.aaps.database.entities.APSResult): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override suspend fun updateExistingEntry(entry: app.aaps.database.entities.APSResult): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}