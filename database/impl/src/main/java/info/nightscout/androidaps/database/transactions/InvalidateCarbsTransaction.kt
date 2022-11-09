package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.Carbs

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