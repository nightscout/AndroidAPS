package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.CarbsDao
import info.nightscout.androidaps.database.entities.Carbs
import info.nightscout.androidaps.database.interfaces.DBEntry

internal class DelegatedCarbsDao(changes: MutableList<DBEntry>, private val dao: CarbsDao) : DelegatedDao(changes), CarbsDao by dao {

    override fun insertNewEntry(entry: Carbs): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: Carbs): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}