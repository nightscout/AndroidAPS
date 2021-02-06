package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.interfaces.end

class CancelCurrentTemporaryTargetIfAnyTransaction(
    val timestamp: Long
) : Transaction<Unit>() {
    override fun run() {
        val current = database.temporaryTargetDao.getTemporaryTargetActiveAt(timestamp)
        if (current != null) {
            current.end = timestamp
            database.temporaryTargetDao.updateExistingEntry(current)
        }
    }
}