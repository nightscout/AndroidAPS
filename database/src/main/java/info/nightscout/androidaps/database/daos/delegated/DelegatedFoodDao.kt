package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.FoodDao
import info.nightscout.androidaps.database.entities.Food
import info.nightscout.androidaps.database.interfaces.DBEntry

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