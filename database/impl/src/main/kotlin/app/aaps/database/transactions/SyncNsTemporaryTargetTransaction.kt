package app.aaps.database.transactions

import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.interfaces.end
import kotlin.math.abs

/**
 * Sync the TemporaryTarget from NS
 */
class SyncNsTemporaryTargetTransaction(private val temporaryTargets: List<TemporaryTarget>) :
    Transaction<SyncNsTemporaryTargetTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        for (temporaryTarget in temporaryTargets) {
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
                    // Allow update duration to shorter only
                    if (current.duration != temporaryTarget.duration && temporaryTarget.duration < current.duration) {
                        current.duration = temporaryTarget.duration
                        database.temporaryTargetDao.updateExistingEntry(current)
                        result.updatedDuration.add(current)
                    }
                    continue
                }

                // not known nsId
                val running = database.temporaryTargetDao.getTemporaryTargetActiveAt(temporaryTarget.timestamp).blockingGet()
                if (running != null && abs(running.timestamp - temporaryTarget.timestamp) < 1000) { // allow missing milliseconds
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
                continue
            } else {
                // ending event
                val running = database.temporaryTargetDao.getTemporaryTargetActiveAt(temporaryTarget.timestamp).blockingGet()
                if (running != null) {
                    running.end = temporaryTarget.timestamp
                    database.temporaryTargetDao.updateExistingEntry(running)
                    result.ended.add(running)
                }
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<TemporaryTarget>()
        val updatedDuration = mutableListOf<TemporaryTarget>()
        val inserted = mutableListOf<TemporaryTarget>()
        val invalidated = mutableListOf<TemporaryTarget>()
        val ended = mutableListOf<TemporaryTarget>()
    }
}