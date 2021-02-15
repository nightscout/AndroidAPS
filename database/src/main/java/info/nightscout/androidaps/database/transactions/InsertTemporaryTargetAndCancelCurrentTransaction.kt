package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.interfaces.end

class InsertTemporaryTargetAndCancelCurrentTransaction(
    private val temporaryTarget: TemporaryTarget
) : Transaction<Unit>() {

    constructor(timestamp: Long, duration: Long, reason: TemporaryTarget.Reason, lowTarget: Double, highTarget: Double) :
        this(TemporaryTarget(timestamp = timestamp, reason = reason, lowTarget = lowTarget, highTarget = highTarget, duration = duration))

    override fun run() {
        val current = database.temporaryTargetDao.getTemporaryTargetActiveAt(temporaryTarget.timestamp)
        if (current != null) {
            current.end = temporaryTarget.timestamp
            database.temporaryTargetDao.updateExistingEntry(current)
        }
        database.temporaryTargetDao.insertNewEntry(temporaryTarget)
    }
}