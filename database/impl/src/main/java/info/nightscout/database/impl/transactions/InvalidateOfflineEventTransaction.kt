package info.nightscout.database.impl.transactions

import app.aaps.database.entities.OfflineEvent

class InvalidateOfflineEventTransaction(val id: Long) : Transaction<InvalidateOfflineEventTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val offlineEvent = database.offlineEventDao.findById(id)
            ?: throw IllegalArgumentException("There is no such OfflineEvent with the specified ID.")
        if (offlineEvent.isValid) {
            offlineEvent.isValid = false
            database.offlineEventDao.updateExistingEntry(offlineEvent)
            result.invalidated.add(offlineEvent)
        }
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<OfflineEvent>()
    }
}