package app.aaps.database.transactions

import app.aaps.database.entities.TherapyEvent

/**
 * Sync the TherapyEvents from NS
 */
class SyncNsTherapyEventTransaction(private val therapyEvents: List<TherapyEvent>, private val nsClientMode: Boolean) :
    Transaction<SyncNsTherapyEventTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()

        for (therapyEvent in therapyEvents) {
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
                // Allow update duration to shorter only
                if (current.duration != therapyEvent.duration && nsClientMode && therapyEvent.duration < current.duration) {
                    current.duration = therapyEvent.duration
                    database.therapyEventDao.updateExistingEntry(current)
                    result.updatedDuration.add(current)
                }
                // allow SiteRotation Update
                if (current.location != therapyEvent.location || current.arrow != therapyEvent.arrow || current.note != therapyEvent.note) {
                    current.location = therapyEvent.location
                    current.arrow = therapyEvent.arrow
                    current.note = therapyEvent.note
                    database.therapyEventDao.updateExistingEntry(current)
                    result.updatedSite.add(current)
                }
                continue
            }

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
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<TherapyEvent>()
        val updatedDuration = mutableListOf<TherapyEvent>()
        val updatedSite = mutableListOf<TherapyEvent>()
        val inserted = mutableListOf<TherapyEvent>()
        val invalidated = mutableListOf<TherapyEvent>()
    }
}