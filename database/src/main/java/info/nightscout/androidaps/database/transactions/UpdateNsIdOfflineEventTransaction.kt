package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.OfflineEvent

class UpdateNsIdOfflineEventTransaction(val offlineEvent: OfflineEvent) : Transaction<Unit>() {

    override fun run() {
        val current = database.offlineEventDao.findById(offlineEvent.id)
        if (current != null && current.interfaceIDs.nightscoutId != offlineEvent.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = offlineEvent.interfaceIDs.nightscoutId
            database.offlineEventDao.updateExistingEntry(current)
        }
    }
}