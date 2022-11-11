package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.Carbs

class InvalidateCarbsTransaction(val id: Long) : Transaction<InvalidateCarbsTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val carbs = database.carbsDao.findById(id)
            ?: throw IllegalArgumentException("There is no such Carbs with the specified ID.")
        carbs.isValid = false
        database.carbsDao.updateExistingEntry(carbs)
        result.invalidated.add(carbs)
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<Carbs>()
    }
}