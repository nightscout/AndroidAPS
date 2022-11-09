package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.TemporaryBasalDao
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.entities.interfaces.DBEntry

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