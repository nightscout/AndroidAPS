package app.aaps.database.daos.delegated

import app.aaps.database.daos.TotalDailyDoseDao
import app.aaps.database.entities.TotalDailyDose
import app.aaps.database.entities.interfaces.DBEntry

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