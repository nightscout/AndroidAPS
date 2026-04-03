package app.aaps.database.transactions

import app.aaps.database.entities.TherapyEvent

class InvalidateTherapyEventsWithNoteTransaction(private val note: String) : Transaction<InvalidateTherapyEventsWithNoteTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
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