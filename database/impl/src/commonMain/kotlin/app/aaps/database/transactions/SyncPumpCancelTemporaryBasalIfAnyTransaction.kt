package app.aaps.database.transactions

import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.end

class SyncPumpCancelTemporaryBasalIfAnyTransaction(
    private val timestamp: Long, private val endPumpId: Long, private val pumpType: InterfaceIDs.PumpType, private val pumpSerial: String
) : Transaction<SyncPumpCancelTemporaryBasalIfAnyTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val existing = database.temporaryBasalDao.findByPumpEndIds(endPumpId, pumpType, pumpSerial)
        if (existing != null) // assume TBR has been cut already
            return result
        val running = database.temporaryBasalDao.getTemporaryBasalActiveAt(timestamp)
        if (running != null && running.interfaceIDs.endId == null) { // do not allow overwrite if cut by end event
            val old = running.copy()
            if (running.timestamp != timestamp) running.end = timestamp // prevent zero duration
            else running.duration = 1
            running.interfaceIDs.endId = endPumpId
            database.temporaryBasalDao.updateExistingEntry(running)
            result.updated.add(Pair(old, running))
        }
        return result
    }

    class TransactionResult {

        val updated = mutableListOf<Pair<TemporaryBasal, TemporaryBasal>>()
    }
}