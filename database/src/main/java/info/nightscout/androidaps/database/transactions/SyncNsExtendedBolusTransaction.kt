package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.ExtendedBolus
import info.nightscout.androidaps.database.interfaces.end
import kotlin.math.abs

/**
 * Sync the Extended bolus from NS
 */
class SyncNsExtendedBolusTransaction(private val extendedBolus: ExtendedBolus, private val invalidateByNsOnly: Boolean) : Transaction<SyncNsExtendedBolusTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

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
                return result
            }

            if (invalidateByNsOnly) return result

            // not known nsId
            val running = database.extendedBolusDao.getExtendedBolusActiveAt(extendedBolus.timestamp).blockingGet()
            if (running != null && abs(running.timestamp - extendedBolus.timestamp) < 1000 && running.interfaceIDs.nightscoutId == null) { // allow missing milliseconds
                // the same record, update nsId only
                running.interfaceIDs.nightscoutId = extendedBolus.interfaceIDs.nightscoutId
                database.extendedBolusDao.updateExistingEntry(running)
                result.updatedNsId.add(running)
            } else if (running != null) {
                // another running record. end current and insert new
                running.end = extendedBolus.timestamp
                database.extendedBolusDao.updateExistingEntry(running)
                database.extendedBolusDao.insertNewEntry(extendedBolus)
                result.ended.add(running)
                result.inserted.add(extendedBolus)
            } else {
                database.extendedBolusDao.insertNewEntry(extendedBolus)
                result.inserted.add(extendedBolus)
            }
            return result

        } else {
            // ending event
            val running = database.extendedBolusDao.getExtendedBolusActiveAt(extendedBolus.timestamp).blockingGet()
            if (running != null) {
                running.end = extendedBolus.timestamp
                database.extendedBolusDao.updateExistingEntry(running)
                result.ended.add(running)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<ExtendedBolus>()
        val inserted = mutableListOf<ExtendedBolus>()
        val invalidated = mutableListOf<ExtendedBolus>()
        val ended = mutableListOf<ExtendedBolus>()
    }
}