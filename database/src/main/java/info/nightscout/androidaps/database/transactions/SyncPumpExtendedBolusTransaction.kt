package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.ExtendedBolus
import info.nightscout.androidaps.database.interfaces.end

/**
 * Creates or updates the extended bolus from pump synchronization
 */
class SyncPumpExtendedBolusTransaction(private val extendedBolus: ExtendedBolus) : Transaction<SyncPumpExtendedBolusTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        extendedBolus.interfaceIDs.pumpId ?: extendedBolus.interfaceIDs.pumpType
        ?: extendedBolus.interfaceIDs.pumpSerial
        ?: throw IllegalStateException("Some pump ID is null")
        val result = TransactionResult()
        val current = database.extendedBolusDao.findByPumpIds(extendedBolus.interfaceIDs.pumpId!!, extendedBolus.interfaceIDs.pumpType!!, extendedBolus.interfaceIDs.pumpSerial!!)
        if (current != null) {
            if (current.interfaceIDs.endId == null &&
                (current.timestamp != extendedBolus.timestamp ||
                    current.amount != extendedBolus.amount ||
                    current.duration != extendedBolus.duration)
            ) {
                current.timestamp = extendedBolus.timestamp
                current.amount = extendedBolus.amount
                current.duration = extendedBolus.duration
                database.extendedBolusDao.updateExistingEntry(current)
                result.updated.add(current)
            }
        } else {
            val running = database.extendedBolusDao.getExtendedBolusActiveAt(extendedBolus.timestamp, extendedBolus.interfaceIDs.pumpType!!, extendedBolus.interfaceIDs.pumpSerial!!).blockingGet()
            if (running != null) {
                val pctRun = (extendedBolus.timestamp - running.timestamp) / running.duration.toDouble()
                running.amount /= pctRun
                running.end = extendedBolus.timestamp
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