package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.TotalDailyDoseDao
import info.nightscout.database.entities.TotalDailyDose
import info.nightscout.database.entities.interfaces.DBEntry

internal class DelegatedTotalDailyDoseDao(changes: MutableList<DBEntry>, private val dao: TotalDailyDoseDao) : DelegatedDao(changes), TotalDailyDoseDao by dao {

    override fun insertNewEntry(entry: TotalDailyDose): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: TotalDailyDose): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}