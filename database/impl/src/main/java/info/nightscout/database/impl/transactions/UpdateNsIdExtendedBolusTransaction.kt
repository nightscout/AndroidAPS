package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.ExtendedBolus

class UpdateNsIdExtendedBolusTransaction(val bolus: ExtendedBolus) : Transaction<Unit>() {

    override fun run() {
        val current = database.extendedBolusDao.findById(bolus.id)
        if (current != null && current.interfaceIDs.nightscoutId != bolus.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = bolus.interfaceIDs.nightscoutId
            database.extendedBolusDao.updateExistingEntry(current)
        }
    }
}