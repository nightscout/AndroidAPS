package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.TemporaryTargetDao
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.interfaces.DBEntry

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