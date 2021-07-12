package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.Bolus

class UpdateNsIdBolusTransaction(val bolus: Bolus) : Transaction<Unit>() {

    override fun run() {
        val current = database.bolusDao.findById(bolus.id)
        if (current != null && current.interfaceIDs.nightscoutId != bolus.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = bolus.interfaceIDs.nightscoutId
            database.bolusDao.updateExistingEntry(current)
        }
    }
}