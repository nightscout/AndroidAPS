package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.TherapyEvent

class InvalidateTherapyEventTransaction(val id: Long) : Transaction<InvalidateTherapyEventTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val therapyEvent = database.therapyEventDao.findById(id)
            ?: throw IllegalArgumentException("There is no such TherapyEvent with the specified ID.")
        therapyEvent.isValid = false
        database.therapyEventDao.updateExistingEntry(therapyEvent)
        result.invalidated.add(therapyEvent)
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<TherapyEvent>()
    }
}