package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.TemporaryBasalDao
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.database.interfaces.DBEntry

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