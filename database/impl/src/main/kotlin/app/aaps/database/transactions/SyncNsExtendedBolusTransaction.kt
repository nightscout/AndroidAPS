package app.aaps.database.transactions

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
                    // Allow update duration to shorter only
                    if (current.duration != extendedBolus.duration && nsClientMode && extendedBolus.duration < current.duration) {
                        current.duration = extendedBolus.duration
                        current.amount = extendedBolus.amount
                        database.extendedBolusDao.updateExistingEntry(current)
                        result.updatedDuration.add(current)
                    }
                    continue
                }

                // not known nsId
                // Check by pumpId + pumpType + pumpSerial (primary deduplication - prevents NS duplicate _id records)
                val existingByPumpId = if (extendedBolus.interfaceIDs.pumpId != null && extendedBolus.interfaceIDs.pumpType != null && extendedBolus.interfaceIDs.pumpSerial != null) {
                    database.extendedBolusDao.findByPumpIds(extendedBolus.interfaceIDs.pumpId!!, extendedBolus.interfaceIDs.pumpType!!, extendedBolus.interfaceIDs.pumpSerial!!)
                } else {
                    null
                }

                if (existingByPumpId != null) {
                    // Same pump extended bolus exists, just update/add the new nsId
                    if (existingByPumpId.interfaceIDs.nightscoutId == null) {
                        existingByPumpId.interfaceIDs.nightscoutId = extendedBolus.interfaceIDs.nightscoutId
                        existingByPumpId.isValid = extendedBolus.isValid
                        database.extendedBolusDao.updateExistingEntry(existingByPumpId)
                        result.updatedNsId.add(existingByPumpId)
                    }
                    // If existing already has a different nsId, this is a duplicate NS record - ignore it
                    continue
                }

                // Fallback: check by active extended bolus at timestamp
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