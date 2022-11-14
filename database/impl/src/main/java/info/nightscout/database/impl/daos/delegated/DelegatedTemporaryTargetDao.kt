package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.TemporaryTargetDao
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.interfaces.DBEntry

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