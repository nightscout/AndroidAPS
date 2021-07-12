package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.database.interfaces.end

class SyncPumpCancelTemporaryBasalIfAnyTransaction(
    private val timestamp: Long, private val endPumpId: Long, private val pumpType: InterfaceIDs.PumpType, private val pumpSerial: String
) : Transaction<SyncPumpCancelTemporaryBasalIfAnyTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val existing = database.temporaryBasalDao.findByPumpEndIds(endPumpId, pumpType, pumpSerial)
        if (existing != null) // assume TBR has been cut already
            return result
        val current = database.temporaryBasalDao.getTemporaryBasalActiveAt(timestamp, pumpType, pumpSerial).blockingGet()
        if (current != null && current.interfaceIDs.endId == null) { // do not allow overwrite if cut by end event
            current.end = timestamp
            current.interfaceIDs.endId = endPumpId
            database.temporaryBasalDao.updateExistingEntry(current)
            result.updated.add(current)
        }
        return result
    }

    class TransactionResult {

        val updated = mutableListOf<TemporaryBasal>()
    }
}