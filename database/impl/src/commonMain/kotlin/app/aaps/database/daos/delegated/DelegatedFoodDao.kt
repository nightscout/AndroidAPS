package app.aaps.database.daos.delegated

import app.aaps.database.daos.FoodDao
import app.aaps.database.entities.Food
import app.aaps.database.entities.interfaces.DBEntry

internal class DelegatedFoodDao(changes: MutableList<DBEntry>, private val dao: FoodDao) : DelegatedDao(changes), FoodDao by dao {

    override suspend fun insertNewEntry(entry: Food): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override suspend fun updateExistingEntry(entry: Food): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}