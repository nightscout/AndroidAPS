package app.aaps.database.transactions

import app.aaps.database.entities.HeartRate

class InsertOrUpdateHeartRateTransaction(private val heartRate: HeartRate) :
    Transaction<InsertOrUpdateHeartRateTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val existing = if (heartRate.id == 0L) null else database.heartRateDao.findById(heartRate.id)
        return if (existing == null) {
            database.heartRateDao.insertNewEntry(heartRate).let {
                TransactionResult(listOf(heartRate), emptyList())
            }
        } else {
            database.heartRateDao.updateExistingEntry(heartRate)
            TransactionResult(emptyList(), listOf(heartRate))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as InsertOrUpdateHeartRateTransaction
        return heartRate == other.heartRate
    }

    override fun hashCode(): Int {
        return heartRate.hashCode()
    }

    data class TransactionResult(val inserted: List<HeartRate>, val updated: List<HeartRate>)
}
