package app.aaps.database.transactions

import app.aaps.database.entities.HeartRate

/**
 * Batch variant of [InsertOrUpdateHeartRateTransaction]. Runs all inserts/updates inside
 * one Room transaction so the repository emits a single change event for the whole batch.
 */
class InsertOrUpdateHeartRatesTransaction(private val heartRates: List<HeartRate>) :
    Transaction<InsertOrUpdateHeartRatesTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val inserted = mutableListOf<HeartRate>()
        val updated = mutableListOf<HeartRate>()
        for (heartRate in heartRates) {
            val existing = if (heartRate.id == 0L) null else database.heartRateDao.findById(heartRate.id)
            if (existing == null) {
                database.heartRateDao.insertNewEntry(heartRate)
                inserted += heartRate
            } else {
                database.heartRateDao.updateExistingEntry(heartRate)
                updated += heartRate
            }
        }
        return TransactionResult(inserted, updated)
    }

    data class TransactionResult(val inserted: List<HeartRate>, val updated: List<HeartRate>)
}
