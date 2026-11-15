package app.aaps.database.transactions

import app.aaps.database.entities.Food

class UpdateNsIdFoodTransaction(private val foods: List<Food>) : Transaction<UpdateNsIdFoodTransaction.TransactionResult>() {

    val result = TransactionResult()

    override suspend fun run(): TransactionResult {
        for (food in foods) {
            val current = database.foodDao.findById(food.id)
            if (current != null && current.interfaceIDs.nightscoutId != food.interfaceIDs.nightscoutId) {
                current.interfaceIDs.nightscoutId = food.interfaceIDs.nightscoutId
                database.foodDao.updateExistingEntry(current)
                result.updatedNsId.add(current)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<Food>()
    }
}