package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.OfflineEvent

class UpdateNsIdOfflineEventTransaction(private val offlineEvents: List<OfflineEvent>) : Transaction<UpdateNsIdOfflineEventTransaction.TransactionResult>() {

    val result = TransactionResult()
    override fun run(): TransactionResult {
        for (offlineEvent in offlineEvents) {
            val current = database.offlineEventDao.findById(offlineEvent.id)
            if (current != null && current.interfaceIDs.nightscoutId != offlineEvent.interfaceIDs.nightscoutId) {
                current.interfaceIDs.nightscoutId = offlineEvent.interfaceIDs.nightscoutId
                database.offlineEventDao.updateExistingEntry(current)
                result.updatedNsId.add(current)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<OfflineEvent>()
    }
}