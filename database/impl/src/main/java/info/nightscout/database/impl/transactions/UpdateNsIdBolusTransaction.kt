package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.Bolus

class UpdateNsIdBolusTransaction(val bolus: Bolus) : Transaction<Unit>() {

    override fun run() {
        val current = database.bolusDao.findById(bolus.id)
        if (current != null && current.interfaceIDs.nightscoutId != bolus.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = bolus.interfaceIDs.nightscoutId
            database.bolusDao.updateExistingEntry(current)
        }
    }
}