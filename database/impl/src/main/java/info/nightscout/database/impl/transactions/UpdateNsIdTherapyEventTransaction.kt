package info.nightscout.database.impl.transactions

import app.aaps.database.entities.TherapyEvent

class UpdateNsIdTherapyEventTransaction(val therapyEvents: List<TherapyEvent>) : Transaction<UpdateNsIdTherapyEventTransaction.TransactionResult>() {

    val result = TransactionResult()
    override fun run(): TransactionResult {
        for (therapyEvent in therapyEvents) {
            val current = database.therapyEventDao.findById(therapyEvent.id)
            if (current != null && current.interfaceIDs.nightscoutId != therapyEvent.interfaceIDs.nightscoutId) {
                current.interfaceIDs.nightscoutId = therapyEvent.interfaceIDs.nightscoutId
                database.therapyEventDao.updateExistingEntry(current)
                result.updatedNsId.add(current)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<TherapyEvent>()
    }
}