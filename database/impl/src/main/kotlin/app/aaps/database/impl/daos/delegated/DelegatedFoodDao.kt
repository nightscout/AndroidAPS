package app.aaps.database.impl.daos.delegated

import app.aaps.database.entities.Food
import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.impl.daos.FoodDao

internal class DelegatedFoodDao(changes: MutableList<DBEntry>, private val dao: FoodDao) : DelegatedDao(changes), FoodDao by dao {

    override fun insertNewEntry(entry: Food): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: Food): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}