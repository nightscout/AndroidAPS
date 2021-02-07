package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.TemporaryTarget

/**
 * Updates the TemporaryTarget
 */
class UpdateTemporaryTargetTransaction(private val temporaryTarget: TemporaryTarget) : Transaction<Unit>() {

    override fun run() {
        database.temporaryTargetDao.updateExistingEntry(temporaryTarget)
    }
}