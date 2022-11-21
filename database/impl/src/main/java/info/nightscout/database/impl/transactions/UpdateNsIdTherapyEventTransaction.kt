package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.TherapyEvent

class UpdateNsIdTherapyEventTransaction(val therapyEvent: TherapyEvent) : Transaction<Unit>() {

    override fun run() {
        val current = database.therapyEventDao.findById(therapyEvent.id)
        if (current != null && current.interfaceIDs.nightscoutId != therapyEvent.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = therapyEvent.interfaceIDs.nightscoutId
            database.therapyEventDao.updateExistingEntry(current)
        }
    }
}