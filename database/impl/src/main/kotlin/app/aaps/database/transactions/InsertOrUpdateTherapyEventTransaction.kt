package app.aaps.database.transactions

import app.aaps.database.entities.APSResult
import app.aaps.database.entities.TherapyEvent

/**
 * Creates or updates the ApsResult
 */
class InsertOrUpdateTherapyEventTransaction(
    val therapyEvent: TherapyEvent
) : Transaction<InsertOrUpdateTherapyEventTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.therapyEventDao.findById(therapyEvent.id)
        if (current == null) {
            database.therapyEventDao.insertNewEntry(therapyEvent)
            result.inserted.add(therapyEvent)
        } else {
            database.therapyEventDao.updateExistingEntry(therapyEvent)
            result.updated.add(therapyEvent)
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<TherapyEvent>()
        val updated = mutableListOf<TherapyEvent>()
    }
}