package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.TemporaryBasal

class UpdateNsIdTemporaryBasalTransaction(val temporaryBasal: TemporaryBasal) : Transaction<Unit>() {

    override fun run() {
        val current = database.temporaryBasalDao.findById(temporaryBasal.id)
        if (current != null && current.interfaceIDs.nightscoutId != temporaryBasal.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = temporaryBasal.interfaceIDs.nightscoutId
            database.temporaryBasalDao.updateExistingEntry(current)
        }
    }
}