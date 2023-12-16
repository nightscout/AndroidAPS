package app.aaps.database.impl.daos.delegated

import app.aaps.database.entities.Food
import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.impl.daos.FoodDao

internal class DelegatedFoodDao(changes: MutableList<DBEntry>, private val dao: FoodDao) : DelegatedDao(changes), FoodDao by dao {

    override fun insertNewEntry(food: Food): Long {
        changes.add(food)
        return dao.insertNewEntry(food)
    }

    override fun updateExistingEntry(food: Food): Long {
        changes.add(food)
        return dao.updateExistingEntry(food)
    }
}