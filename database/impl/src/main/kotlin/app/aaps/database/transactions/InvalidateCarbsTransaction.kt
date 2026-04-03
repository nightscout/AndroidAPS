package app.aaps.database.transactions

import app.aaps.database.entities.Carbs

class InvalidateCarbsTransaction(val id: Long) : Transaction<InvalidateCarbsTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val carbs = database.carbsDao.findById(id)
            ?: throw IllegalArgumentException("There is no such Carbs with the specified ID.")
        if (carbs.isValid) {
            carbs.isValid = false
            database.carbsDao.updateExistingEntry(carbs)
            result.invalidated.add(carbs)
        }
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<Carbs>()
    }
}