package app.aaps.database.transactions

import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.interfaces.end

class InsertAndCancelCurrentTemporaryTargetTransaction(
    val temporaryTarget: TemporaryTarget
) : Transaction<InsertAndCancelCurrentTemporaryTargetTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.temporaryTargetDao.getTemporaryTargetActiveAtLegacy(temporaryTarget.timestamp)
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