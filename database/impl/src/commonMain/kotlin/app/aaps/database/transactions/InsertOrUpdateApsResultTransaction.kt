package app.aaps.database.transactions

import app.aaps.database.entities.APSResult

/**
 * Creates or updates the ApsResult
 */
class InsertOrUpdateApsResultTransaction(
    private val apsResult: APSResult
) : Transaction<InsertOrUpdateApsResultTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.apsResultDao.findById(apsResult.id)
        if (current == null) {
            database.apsResultDao.insertNewEntry(apsResult)
            result.inserted.add(apsResult)
        } else {
            database.apsResultDao.updateExistingEntry(apsResult)
            result.updated.add(apsResult)
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<APSResult>()
        val updated = mutableListOf<APSResult>()
    }
}