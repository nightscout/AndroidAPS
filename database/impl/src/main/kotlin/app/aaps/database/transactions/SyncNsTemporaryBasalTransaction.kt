package app.aaps.database.transactions

import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.interfaces.end
import kotlin.math.abs

/**
 * Sync the Temporary Basal from NS
 */
class SyncNsTemporaryBasalTransaction(private val temporaryBasals: List<TemporaryBasal>, private val nsClientMode: Boolean) : Transaction<SyncNsTemporaryBasalTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        for (temporaryBasal in temporaryBasals) {
            if (temporaryBasal.duration != 0L) {
                // not ending event
                val current = temporaryBasal.interfaceIDs.nightscoutId?.let {
                    database.temporaryBasalDao.findByNSId(it) ?: temporaryBasal.interfaceIDs.pumpId?.let {
                        val pumpId = temporaryBasal.interfaceIDs.pumpId
                        val pumpType = temporaryBasal.interfaceIDs.pumpType
                        val pumpSerial = temporaryBasal.interfaceIDs.pumpSerial
                        if (pumpId != null && pumpType != null && pumpSerial != null)
                            database.temporaryBasalDao.findByPumpIds(pumpId, pumpType, pumpSerial)
                        else null
                    }
                }

                if (current != null) {
                    // nsId exists, allow only invalidation
                    if (current.isValid && !temporaryBasal.isValid) {
                        current.isValid = false
                        database.temporaryBasalDao.updateExistingEntry(current)
                        result.invalidated.add(current)
                    }
                    if (current.duration != temporaryBasal.duration && nsClientMode) {
                        current.duration = temporaryBasal.duration
                        database.temporaryBasalDao.updateExistingEntry(current)
                        result.updatedDuration.add(current)
                    }
                    continue
                }

                // not known nsId
                val running = database.temporaryBasalDao.getTemporaryBasalActiveAt(temporaryBasal.timestamp).blockingGet()
                if (running != null && abs(running.timestamp - temporaryBasal.timestamp) < 1000) { // allow missing milliseconds
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
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<TemporaryBasal>()
        val updatedDuration = mutableListOf<TemporaryBasal>()
        val inserted = mutableListOf<TemporaryBasal>()
        val invalidated = mutableListOf<TemporaryBasal>()
        val ended = mutableListOf<TemporaryBasal>()
    }
}