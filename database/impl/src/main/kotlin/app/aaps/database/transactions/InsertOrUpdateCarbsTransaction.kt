package app.aaps.database.transactions

import app.aaps.database.entities.Carbs

/**
 * Creates or updates the Carbs
 */
class InsertOrUpdateCarbsTransaction(
    private val carbs: Carbs
) : Transaction<InsertOrUpdateCarbsTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.carbsDao.findById(carbs.id)
        if (current == null) {
            database.carbsDao.insertNewEntry(carbs)
            result.inserted.add(carbs)
        } else {
            database.carbsDao.updateExistingEntry(carbs)
            result.updated.add(carbs)
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<Carbs>()
        val updated = mutableListOf<Carbs>()
    }
}