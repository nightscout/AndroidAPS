package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.OfflineEvent
import info.nightscout.database.entities.interfaces.end

class CancelCurrentOfflineEventIfAnyTransaction(
    val timestamp: Long
) : Transaction<CancelCurrentOfflineEventIfAnyTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.offlineEventDao.getOfflineEventActiveAt(timestamp).blockingGet()
        if (current != null) {
            current.end = timestamp
            database.offlineEventDao.updateExistingEntry(current)
            result.updated.add(current)
        }
        return result
    }

    class TransactionResult {

        val updated = mutableListOf<OfflineEvent>()
    }
}