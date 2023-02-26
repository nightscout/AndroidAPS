package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.OfflineEvent
import info.nightscout.database.entities.interfaces.end
import kotlin.math.abs

/**
 * Sync the OfflineEvent from NS
 */
class SyncNsOfflineEventTransaction(private val offlineEvents: List<OfflineEvent>, private val nsClientMode: Boolean) :
    Transaction<SyncNsOfflineEventTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        for (offlineEvent in offlineEvents) {
            if (offlineEvent.duration != 0L) {
                // not ending event
                val current: OfflineEvent? =
                    offlineEvent.interfaceIDs.nightscoutId?.let {
                        database.offlineEventDao.findByNSId(it)
                    }

                if (current != null) {
                    // nsId exists, allow only invalidation
                    if (current.isValid && !offlineEvent.isValid) {
                        current.isValid = false
                        database.offlineEventDao.updateExistingEntry(current)
                        result.invalidated.add(current)
                    }
                    if (current.duration != offlineEvent.duration && nsClientMode) {
                        current.duration = offlineEvent.duration
                        database.offlineEventDao.updateExistingEntry(current)
                        result.updatedDuration.add(current)
                    }
                    continue
                }

                // not known nsId
                val running = database.offlineEventDao.getOfflineEventActiveAt(offlineEvent.timestamp).blockingGet()
                if (running != null && abs(running.timestamp - offlineEvent.timestamp) < 1000) { // allow missing milliseconds
                    // the same record, update nsId only
                    running.interfaceIDs.nightscoutId = offlineEvent.interfaceIDs.nightscoutId
                    database.offlineEventDao.updateExistingEntry(running)
                    result.updatedNsId.add(running)
                } else if (running != null) {
                    // another running record. end current and insert new
                    running.end = offlineEvent.timestamp
                    database.offlineEventDao.updateExistingEntry(running)
                    database.offlineEventDao.insertNewEntry(offlineEvent)
                    result.ended.add(running)
                    result.inserted.add(offlineEvent)
                } else {
                    database.offlineEventDao.insertNewEntry(offlineEvent)
                    result.inserted.add(offlineEvent)
                }
                continue

            } else {
                // ending event
                val running = database.offlineEventDao.getOfflineEventActiveAt(offlineEvent.timestamp).blockingGet()
                if (running != null) {
                    running.end = offlineEvent.timestamp
                    database.offlineEventDao.updateExistingEntry(running)
                    result.ended.add(running)
                }
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<OfflineEvent>()
        val updatedDuration = mutableListOf<OfflineEvent>()
        val inserted = mutableListOf<OfflineEvent>()
        val invalidated = mutableListOf<OfflineEvent>()
        val ended = mutableListOf<OfflineEvent>()
    }
}