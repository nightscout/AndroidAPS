package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.Food

/**
 * Sync the TherapyEvents from NS
 */
class SyncNsFoodTransaction(private val food: Food) : Transaction<SyncNsFoodTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        val current: Food? =
            food.interfaceIDs.nightscoutId?.let {
                database.foodDao.findByNSId(it)
            }

        if (current != null) {
            // nsId exists, update if different
            if (!current.contentEqualsTo(food)) {
                current.copyFrom(food)
                database.foodDao.updateExistingEntry(current)
                if (food.isValid && current.isValid) result.updated.add(current)
                else if (!food.isValid && current.isValid) result.invalidated.add(current)
            }
            return result
        }

        // not known nsId, add
        database.foodDao.insertNewEntry(food)
        result.inserted.add(food)
        return result

    }

    class TransactionResult {

        val updated = mutableListOf<Food>()
        val inserted = mutableListOf<Food>()
        val invalidated = mutableListOf<Food>()
    }
}