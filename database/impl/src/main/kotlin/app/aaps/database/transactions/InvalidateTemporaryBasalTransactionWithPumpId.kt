package app.aaps.database.transactions

import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.embedments.InterfaceIDs

class InvalidateTemporaryBasalTransactionWithPumpId(val pumpId: Long, val pumpType: InterfaceIDs.PumpType, val pumpSerial: String) :
    Transaction<InvalidateTemporaryBasalTransactionWithPumpId.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val temporaryBasal = database.temporaryBasalDao.findByPumpIds(pumpId, pumpType, pumpSerial)
            ?: throw IllegalArgumentException("There is no such Temporary Basal with the specified temp ID.")
        if (temporaryBasal.isValid) {
            temporaryBasal.isValid = false
            database.temporaryBasalDao.updateExistingEntry(temporaryBasal)
            result.invalidated.add(temporaryBasal)
        }
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<TemporaryBasal>()
    }
}