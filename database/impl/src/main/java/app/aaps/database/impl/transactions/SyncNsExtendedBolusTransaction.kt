package app.aaps.database.impl.transactions

import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.interfaces.end
import kotlin.math.abs

/**
 * Sync the Extended bolus from NS
 */
class SyncNsExtendedBolusTransaction(private val extendedBoluses: List<ExtendedBolus>, private val nsClientMode: Boolean) :
    Transaction<SyncNsExtendedBolusTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        for (extendedBolus in extendedBoluses) {
            if (extendedBolus.duration != 0L) {
                // not ending event
                val current: ExtendedBolus? =
                    extendedBolus.interfaceIDs.nightscoutId?.let {
                        database.extendedBolusDao.findByNSId(it)
                    }

                if (current != null) {
                    // nsId exists, allow only invalidation
                    if (current.isValid && !extendedBolus.isValid) {
                        current.isValid = false
                        database.extendedBolusDao.updateExistingEntry(current)
                        result.invalidated.add(current)
                    }
                    if (current.duration != extendedBolus.duration && nsClientMode) {
                        current.duration = extendedBolus.duration
                        current.amount = extendedBolus.amount
                        database.extendedBolusDao.updateExistingEntry(current)
                        result.updatedDuration.add(current)
                    }
                    continue
                }

                // not known nsId
                val running = database.extendedBolusDao.getExtendedBolusActiveAt(extendedBolus.timestamp).blockingGet()
                if (running != null && abs(running.timestamp - extendedBolus.timestamp) < 1000) { // allow missing milliseconds
                    // the same record, update nsId only
                    running.interfaceIDs.nightscoutId = extendedBolus.interfaceIDs.nightscoutId
                    database.extendedBolusDao.updateExistingEntry(running)
                    result.updatedNsId.add(running)
                } else if (running != null) {
                    // another running record. end current and insert new
                    val pctRun = (extendedBolus.timestamp - running.timestamp) / running.duration.toDouble()
                    running.amount *= pctRun
                    running.end = extendedBolus.timestamp
                    database.extendedBolusDao.updateExistingEntry(running)
                    database.extendedBolusDao.insertNewEntry(extendedBolus)
                    result.ended.add(running)
                    result.inserted.add(extendedBolus)
                } else {
                    database.extendedBolusDao.insertNewEntry(extendedBolus)
                    result.inserted.add(extendedBolus)
                }
                continue

            } else {
                // ending event
                val running = database.extendedBolusDao.getExtendedBolusActiveAt(extendedBolus.timestamp).blockingGet()
                if (running != null) {
                    val pctRun = (extendedBolus.timestamp - running.timestamp) / running.duration.toDouble()
                    running.amount *= pctRun
                    running.end = extendedBolus.timestamp
                    database.extendedBolusDao.updateExistingEntry(running)
                    result.ended.add(running)
                }
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<ExtendedBolus>()
        val updatedDuration = mutableListOf<ExtendedBolus>()
        val inserted = mutableListOf<ExtendedBolus>()
        val invalidated = mutableListOf<ExtendedBolus>()
        val ended = mutableListOf<ExtendedBolus>()
    }
}