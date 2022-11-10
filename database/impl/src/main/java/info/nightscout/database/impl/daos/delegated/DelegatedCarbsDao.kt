package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.CarbsDao
import info.nightscout.database.entities.Carbs
import info.nightscout.database.entities.interfaces.DBEntry

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