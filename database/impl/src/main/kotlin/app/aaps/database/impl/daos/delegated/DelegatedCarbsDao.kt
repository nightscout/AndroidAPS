package app.aaps.database.impl.daos.delegated

import app.aaps.database.entities.Carbs
import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.impl.daos.CarbsDao

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