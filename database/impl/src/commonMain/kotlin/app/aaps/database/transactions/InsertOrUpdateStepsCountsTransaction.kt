package app.aaps.database.transactions

import app.aaps.database.entities.StepsCount

/**
 * Batch variant of [InsertOrUpdateStepsCountTransaction]. Runs all inserts/updates inside
 * one Room transaction so the repository emits a single change event for the whole batch.
 */
class InsertOrUpdateStepsCountsTransaction(private val stepsCounts: List<StepsCount>) :
    Transaction<InsertOrUpdateStepsCountsTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val inserted = mutableListOf<StepsCount>()
        val updated = mutableListOf<StepsCount>()
        for (stepsCount in stepsCounts) {
            val existing = if (stepsCount.id == 0L) null else database.stepsCountDao.findById(stepsCount.id)
            if (existing == null) {
                database.stepsCountDao.insertNewEntry(stepsCount)
                inserted += stepsCount
            } else {
                database.stepsCountDao.updateExistingEntry(stepsCount)
                updated += stepsCount
            }
        }
        return TransactionResult(inserted, updated)
    }

    data class TransactionResult(val inserted: List<StepsCount>, val updated: List<StepsCount>)
}
