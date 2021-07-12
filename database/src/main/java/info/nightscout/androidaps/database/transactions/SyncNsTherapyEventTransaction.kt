package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.TherapyEvent

/**
 * Sync the TherapyEvents from NS
 */
class SyncNsTherapyEventTransaction(private val therapyEvent: TherapyEvent, private val invalidateByNsOnly: Boolean) : Transaction<SyncNsTherapyEventTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        val current: TherapyEvent? =
            therapyEvent.interfaceIDs.nightscoutId?.let {
                database.therapyEventDao.findByNSId(it)
            }

        if (current != null) {
            // nsId exists, allow only invalidation
            if (current.isValid && !therapyEvent.isValid) {
                current.isValid = false
                database.therapyEventDao.updateExistingEntry(current)
                result.invalidated.add(current)
            }
            return result
        }

        if (invalidateByNsOnly) return result

        // not known nsId
        val existing = database.therapyEventDao.findByTimestamp(therapyEvent.type, therapyEvent.timestamp)
        if (existing != null && existing.interfaceIDs.nightscoutId == null) {
            // the same record, update nsId only
            existing.interfaceIDs.nightscoutId = therapyEvent.interfaceIDs.nightscoutId
            existing.isValid = therapyEvent.isValid
            database.therapyEventDao.updateExistingEntry(existing)
            result.updatedNsId.add(existing)
        } else {
            database.therapyEventDao.insertNewEntry(therapyEvent)
            result.inserted.add(therapyEvent)
        }
        return result

    }

    class TransactionResult {

        val updatedNsId = mutableListOf<TherapyEvent>()
        val inserted = mutableListOf<TherapyEvent>()
        val invalidated = mutableListOf<TherapyEvent>()
    }
}