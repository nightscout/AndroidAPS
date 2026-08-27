package app.aaps.database.daos.delegated

import app.aaps.database.daos.TemporaryTargetDao
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.interfaces.DBEntry

internal class DelegatedTemporaryTargetDao(changes: MutableList<DBEntry>, private val dao: TemporaryTargetDao) : DelegatedDao(changes), TemporaryTargetDao by dao {

    override fun insertNewEntry(entry: TemporaryTarget): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: TemporaryTarget): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}