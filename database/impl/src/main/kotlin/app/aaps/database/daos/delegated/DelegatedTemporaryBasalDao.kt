package app.aaps.database.daos.delegated

import app.aaps.database.daos.TemporaryBasalDao
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.interfaces.DBEntry

internal class DelegatedTemporaryBasalDao(changes: MutableList<DBEntry>, private val dao: TemporaryBasalDao) : DelegatedDao(changes), TemporaryBasalDao by dao {

    override fun insertNewEntry(entry: TemporaryBasal): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: TemporaryBasal): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}