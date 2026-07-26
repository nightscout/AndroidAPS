package app.aaps.database.transactions

import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.interfaces.end

/**
 * Creates or updates the extended bolus from pump synchronization
 */
class SyncPumpExtendedBolusTransaction(private val extendedBolus: ExtendedBolus) : Transaction<SyncPumpExtendedBolusTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        extendedBolus.interfaceIDs.pumpId ?: extendedBolus.interfaceIDs.pumpType
        ?: extendedBolus.interfaceIDs.pumpSerial
        ?: throw IllegalStateException("Some pump ID is null")
        val result = TransactionResult()
        val existing = database.extendedBolusDao.findByPumpIds(extendedBolus.interfaceIDs.pumpId!!, extendedBolus.interfaceIDs.pumpType!!, extendedBolus.interfaceIDs.pumpSerial!!)
        if (existing != null) {
            if (existing.interfaceIDs.endId == null &&
                (existing.timestamp != extendedBolus.timestamp ||
                    existing.amount != extendedBolus.amount ||
                    existing.duration != extendedBolus.duration)
            ) {
                existing.timestamp = extendedBolus.timestamp
                existing.amount = extendedBolus.amount
                existing.duration = extendedBolus.duration
                database.extendedBolusDao.updateExistingEntry(existing)
                result.updated.add(existing)
            }
        } else {
            val running = database.extendedBolusDao.getExtendedBolusActiveAtLegacy(extendedBolus.timestamp)
            // Only shorten a previous extended bolus when the new one genuinely starts after it and it
            // has a real duration. Without this guard, a new record whose (second-resolution) timestamp
            // equals or precedes the running one's makes `end = timestamp` non-positive and
            // ExtendedBolus.setEnd throws `require(duration > 0)`, crashing the whole app. That happens
            // when the same extended bolus is synced twice with slightly different timestamps — the
            // command path and the pump's history event (DanaRSPacketAPSHistoryEvents) — which is not a
            // real supersession, so skipping it is also the correct behaviour, not just crash-avoidance.
            if (running != null && running.duration > 0 && extendedBolus.timestamp > running.timestamp) {
                val pctRun = (extendedBolus.timestamp - running.timestamp) / running.duration.toDouble()
                running.amount *= pctRun
                running.end = extendedBolus.timestamp
                running.interfaceIDs.endId = extendedBolus.interfaceIDs.pumpId
                database.extendedBolusDao.updateExistingEntry(running)
                result.updated.add(running)
            }
            database.extendedBolusDao.insertNewEntry(extendedBolus)
            result.inserted.add(extendedBolus)
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<ExtendedBolus>()
        val updated = mutableListOf<ExtendedBolus>()
    }
}