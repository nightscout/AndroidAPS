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
            if (running != null) {
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