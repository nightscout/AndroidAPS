package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.TemporaryTarget

class UpdateNsIdTemporaryTargetTransaction(val temporaryTarget: TemporaryTarget) : Transaction<Unit>() {

    override fun run() {
        val current = database.temporaryTargetDao.findById(temporaryTarget.id)
        if (current != null && current.interfaceIDs.nightscoutId != temporaryTarget.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = temporaryTarget.interfaceIDs.nightscoutId
            database.temporaryTargetDao.updateExistingEntry(current)
        }
    }
}