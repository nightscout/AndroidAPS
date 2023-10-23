package app.aaps.database.impl.daos.delegated

import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.impl.daos.TemporaryBasalDao

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