package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.interfaces.end
import kotlin.math.abs

/**
 * Sync the TemporaryTarget from NS
 */
class SyncNsTemporaryTargetTransaction(private val temporaryTarget: TemporaryTarget, private val invalidateByNsOnly: Boolean) : Transaction<SyncNsTemporaryTargetTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        if (temporaryTarget.duration != 0L) {
            // not ending event
            val current: TemporaryTarget? =
                temporaryTarget.interfaceIDs.nightscoutId?.let {
                    database.temporaryTargetDao.findByNSId(it)
                }

            if (current != null) {
                // nsId exists, allow only invalidation
                if (current.isValid && !temporaryTarget.isValid) {
                    current.isValid = false
                    database.temporaryTargetDao.updateExistingEntry(current)
                    result.invalidated.add(current)
                }
                return result
            }

            if (invalidateByNsOnly) return result

            // not known nsId
            val running = database.temporaryTargetDao.getTemporaryTargetActiveAt(temporaryTarget.timestamp).blockingGet()
            if (running != null && abs(running.timestamp - temporaryTarget.timestamp) < 1000 && running.interfaceIDs.nightscoutId == null) { // allow missing milliseconds
                // the same record, update nsId only
                running.interfaceIDs.nightscoutId = temporaryTarget.interfaceIDs.nightscoutId
                database.temporaryTargetDao.updateExistingEntry(running)
                result.updatedNsId.add(running)
            } else if (running != null) {
                // another running record. end current and insert new
                running.end = temporaryTarget.timestamp
                database.temporaryTargetDao.updateExistingEntry(running)
                database.temporaryTargetDao.insertNewEntry(temporaryTarget)
                result.ended.add(running)
                result.inserted.add(temporaryTarget)
            } else {
                database.temporaryTargetDao.insertNewEntry(temporaryTarget)
                result.inserted.add(temporaryTarget)
            }
            return result

        } else {
            // ending event
            val running = database.temporaryTargetDao.getTemporaryTargetActiveAt(temporaryTarget.timestamp).blockingGet()
            if (running != null) {
                running.end = temporaryTarget.timestamp
                database.temporaryTargetDao.updateExistingEntry(running)
                result.ended.add(running)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<TemporaryTarget>()
        val inserted = mutableListOf<TemporaryTarget>()
        val invalidated = mutableListOf<TemporaryTarget>()
        val ended = mutableListOf<TemporaryTarget>()
    }
}