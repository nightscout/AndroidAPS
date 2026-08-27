package app.aaps.database.transactions

import app.aaps.database.entities.Carbs

/**
 * Creates Carbs if record doesn't exist
 */
class InsertIfNewByTimestampCarbsTransaction(
    private val carbs: Carbs
) : Transaction<InsertIfNewByTimestampCarbsTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.carbsDao.findByTimestamp(carbs.timestamp)
        if (current == null) {
            database.carbsDao.insertNewEntry(carbs)
            result.inserted.add(carbs)
        } else
            result.existing.add(carbs)
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<Carbs>()
        val existing = mutableListOf<Carbs>()
    }
}