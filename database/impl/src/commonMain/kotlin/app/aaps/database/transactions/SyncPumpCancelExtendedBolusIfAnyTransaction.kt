package app.aaps.database.transactions

import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.end

class SyncPumpCancelExtendedBolusIfAnyTransaction(
    private val timestamp: Long, private val endPumpId: Long, private val pumpType: InterfaceIDs.PumpType, private val pumpSerial: String
) : Transaction<SyncPumpCancelExtendedBolusIfAnyTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val existing = database.extendedBolusDao.findByPumpEndIds(endPumpId, pumpType, pumpSerial)
        if (existing != null) // assume EB has been cut already
            return result
        val running = database.extendedBolusDao.getExtendedBolusActiveAt(timestamp)
        if (running != null && running.interfaceIDs.endId == null) { // do not allow overwrite if cut by end event
            val pctRun = (timestamp - running.timestamp) / running.duration.toDouble()
            running.amount *= pctRun
            running.end = timestamp
            running.interfaceIDs.endId = endPumpId
            database.extendedBolusDao.updateExistingEntry(running)
            result.updated.add(running)
        }
        return result
    }

    class TransactionResult {

        val updated = mutableListOf<ExtendedBolus>()
    }
}