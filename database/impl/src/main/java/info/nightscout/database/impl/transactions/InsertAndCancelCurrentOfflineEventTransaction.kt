package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.OfflineEvent
import info.nightscout.database.entities.interfaces.end

class InsertAndCancelCurrentOfflineEventTransaction(
    val offlineEvent: OfflineEvent
) : Transaction<InsertAndCancelCurrentOfflineEventTransaction.TransactionResult>() {

    constructor(timestamp: Long, duration: Long, reason: OfflineEvent.Reason) :
        this(OfflineEvent(timestamp = timestamp, reason = reason, duration = duration))

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.offlineEventDao.getOfflineEventActiveAt(offlineEvent.timestamp).blockingGet()
        if (current != null) {
            current.end = offlineEvent.timestamp
            database.offlineEventDao.updateExistingEntry(current)
            result.updated.add(current)
        }
        database.offlineEventDao.insertNewEntry(offlineEvent)
        result.inserted.add(offlineEvent)
        return result
    }

    class TransactionResult {
        val inserted = mutableListOf<OfflineEvent>()
        val updated = mutableListOf<OfflineEvent>()
    }
}