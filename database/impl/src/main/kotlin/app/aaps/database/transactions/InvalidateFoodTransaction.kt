package app.aaps.database.transactions

import app.aaps.database.entities.Food

class InvalidateFoodTransaction(val id: Long) : Transaction<InvalidateFoodTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val food = database.foodDao.findById(id)
            ?: throw IllegalArgumentException("There is no such Food with the specified ID.")
        if (food.isValid) {
            food.isValid = false
            database.foodDao.updateExistingEntry(food)
            result.invalidated.add(food)
        }
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<Food>()
    }
}