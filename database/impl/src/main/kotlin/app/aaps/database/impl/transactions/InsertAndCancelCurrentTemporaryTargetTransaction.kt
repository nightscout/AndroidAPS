package app.aaps.database.impl.transactions

import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.interfaces.end

class InsertAndCancelCurrentTemporaryTargetTransaction(
    val temporaryTarget: TemporaryTarget
) : Transaction<InsertAndCancelCurrentTemporaryTargetTransaction.TransactionResult>() {

    constructor(timestamp: Long, duration: Long, reason: TemporaryTarget.Reason, lowTarget: Double, highTarget: Double) :
        this(TemporaryTarget(timestamp = timestamp, reason = reason, lowTarget = lowTarget, highTarget = highTarget, duration = duration))

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.temporaryTargetDao.getTemporaryTargetActiveAt(temporaryTarget.timestamp).blockingGet()
        if (current != null) {
            current.end = temporaryTarget.timestamp
            database.temporaryTargetDao.updateExistingEntry(current)
            result.updated.add(current)
        }
        database.temporaryTargetDao.insertNewEntry(temporaryTarget)
        result.inserted.add(temporaryTarget)
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<TemporaryTarget>()
        val updated = mutableListOf<TemporaryTarget>()
    }
}