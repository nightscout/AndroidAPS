package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.TemporaryBasal

class UpdateNsIdTemporaryBasalTransaction(val bolus: TemporaryBasal) : Transaction<Unit>() {

    override fun run() {
        val current = database.temporaryBasalDao.findById(bolus.id)
        if (current != null && current.interfaceIDs.nightscoutId != bolus.interfaceIDs.nightscoutId)
            database.temporaryBasalDao.updateExistingEntry(bolus)
    }
}