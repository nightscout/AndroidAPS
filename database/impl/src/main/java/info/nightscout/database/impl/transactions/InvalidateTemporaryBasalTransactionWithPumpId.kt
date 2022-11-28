package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.entities.embedments.InterfaceIDs

class InvalidateTemporaryBasalTransactionWithPumpId(val pumpId: Long, val pumpType: InterfaceIDs.PumpType, val pumpSerial: String) :
    Transaction<InvalidateTemporaryBasalTransactionWithPumpId.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val temporaryBasal = database.temporaryBasalDao.findByPumpIds(pumpId, pumpType, pumpSerial)
            ?: throw IllegalArgumentException("There is no such Temporary Basal with the specified temp ID.")
        temporaryBasal.isValid = false
        database.temporaryBasalDao.updateExistingEntry(temporaryBasal)
        result.invalidated.add(temporaryBasal)
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<TemporaryBasal>()
    }
}