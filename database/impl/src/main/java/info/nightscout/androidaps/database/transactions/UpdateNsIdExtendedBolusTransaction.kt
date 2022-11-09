package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.ExtendedBolus

class UpdateNsIdExtendedBolusTransaction(val bolus: ExtendedBolus) : Transaction<Unit>() {

    override fun run() {
        val current = database.extendedBolusDao.findById(bolus.id)
        if (current != null && current.interfaceIDs.nightscoutId != bolus.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = bolus.interfaceIDs.nightscoutId
            database.extendedBolusDao.updateExistingEntry(current)
        }
    }
}