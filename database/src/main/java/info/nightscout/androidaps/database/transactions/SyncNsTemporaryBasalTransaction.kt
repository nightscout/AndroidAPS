package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.database.interfaces.end
import kotlin.math.abs

/**
 * Sync the Temporary Basal from NS
 */
class SyncNsTemporaryBasalTransaction(private val temporaryBasal: TemporaryBasal, private val invalidateByNsOnly: Boolean) : Transaction<SyncNsTemporaryBasalTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        if (temporaryBasal.duration != 0L) {
            // not ending event
            val current: TemporaryBasal? =
                temporaryBasal.interfaceIDs.nightscoutId?.let {
                    database.temporaryBasalDao.findByNSId(it)
                }

            if (current != null) {
                // nsId exists, allow only invalidation
                if (current.isValid && !temporaryBasal.isValid) {
                    current.isValid = false
                    database.temporaryBasalDao.updateExistingEntry(current)
                    result.invalidated.add(current)
                }
                return result
            }

            if (invalidateByNsOnly) return result

            // not known nsId
            val running = database.temporaryBasalDao.getTemporaryBasalActiveAt(temporaryBasal.timestamp).blockingGet()
            if (running != null && abs(running.timestamp - temporaryBasal.timestamp) < 1000 && running.interfaceIDs.nightscoutId == null) { // allow missing milliseconds
                // the same record, update nsId only
                running.interfaceIDs.nightscoutId = temporaryBasal.interfaceIDs.nightscoutId
                database.temporaryBasalDao.updateExistingEntry(running)
                result.updatedNsId.add(running)
            } else if (running != null) {
                // another running record. end current and insert new
                running.end = temporaryBasal.timestamp
                database.temporaryBasalDao.updateExistingEntry(running)
                database.temporaryBasalDao.insertNewEntry(temporaryBasal)
                result.ended.add(running)
                result.inserted.add(temporaryBasal)
            } else {
                database.temporaryBasalDao.insertNewEntry(temporaryBasal)
                result.inserted.add(temporaryBasal)
            }
            return result
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<TemporaryBasal>()
        val inserted = mutableListOf<TemporaryBasal>()
        val invalidated = mutableListOf<TemporaryBasal>()
        val ended = mutableListOf<TemporaryBasal>()
    }
}