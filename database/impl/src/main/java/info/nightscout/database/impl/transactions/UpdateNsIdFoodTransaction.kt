package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.Food

class UpdateNsIdFoodTransaction(val food: Food) : Transaction<Unit>() {

    override fun run() {
        val current = database.foodDao.findById(food.id)
        if (current != null && current.interfaceIDs.nightscoutId != food.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = food.interfaceIDs.nightscoutId
            database.foodDao.updateExistingEntry(current)
        }
    }
}