package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.FoodDao
import info.nightscout.database.entities.Food
import info.nightscout.database.entities.interfaces.DBEntry

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