package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.TherapyEvent

class InvalidateAAPSStartedTherapyEventTransaction(private val note: String) : Transaction<InvalidateAAPSStartedTherapyEventTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val therapyEvents = database.therapyEventDao.getValidByType(TherapyEvent.Type.NOTE)
        for (event in therapyEvents) {
            if (event.note?.contains(note) == true && event.isValid) {
                event.isValid = false
                database.therapyEventDao.updateExistingEntry(event)
                result.invalidated.add(event)
            }
        }
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<TherapyEvent>()
    }
}