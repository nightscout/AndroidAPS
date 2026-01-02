package app.aaps.database.transactions

import app.aaps.database.entities.RunningMode
import app.aaps.database.entities.interfaces.end

class CancelCurrentTemporaryRunningModeIfAnyTransaction(
    val timestamp: Long
) : Transaction<CancelCurrentTemporaryRunningModeIfAnyTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.runningModeDao.getTemporaryRunningModeActiveAt(timestamp).blockingGet()
        if (current != null) {
            current.end = timestamp
            database.runningModeDao.updateExistingEntry(current)
            result.updated.add(current)
        }
        return result
    }

    class TransactionResult {

        val updated = mutableListOf<RunningMode>()
    }
}