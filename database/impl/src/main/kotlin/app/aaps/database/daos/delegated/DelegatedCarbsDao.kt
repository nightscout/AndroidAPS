package app.aaps.database.daos.delegated

import app.aaps.database.daos.CarbsDao
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.interfaces.DBEntry

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