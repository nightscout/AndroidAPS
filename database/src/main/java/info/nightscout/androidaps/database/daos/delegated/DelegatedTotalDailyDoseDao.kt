package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.TotalDailyDoseDao
import info.nightscout.androidaps.database.entities.TotalDailyDose
import info.nightscout.androidaps.database.interfaces.DBEntry

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