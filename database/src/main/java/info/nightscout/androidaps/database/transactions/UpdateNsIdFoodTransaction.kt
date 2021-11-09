package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.Food

class UpdateNsIdFoodTransaction(val food: Food) : Transaction<Unit>() {

    override fun run() {
        val current = database.foodDao.findById(food.id)
        if (current != null && current.interfaceIDs.nightscoutId != food.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = food.interfaceIDs.nightscoutId
            database.foodDao.updateExistingEntry(current)
        }
    }
}