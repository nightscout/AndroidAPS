package app.aaps.database.transactions

import app.aaps.database.entities.Bolus

/**
 * Creates or updates the Bolus from pump synchronization
 */
class SyncBolusWithTempIdTransaction(
    private val bolus: Bolus,
    private val newType: Bolus.Type?
) : Transaction<SyncBolusWithTempIdTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        bolus.interfaceIDs.temporaryId ?: bolus.interfaceIDs.pumpType ?: bolus.interfaceIDs.pumpSerial ?: throw IllegalStateException("Some pump ID is null")
        val result = TransactionResult()
        val current = database.bolusDao.findByPumpTempIds(bolus.interfaceIDs.temporaryId!!, bolus.interfaceIDs.pumpType!!, bolus.interfaceIDs.pumpSerial!!)
        if (current != null) {
            current.timestamp = bolus.timestamp
            current.amount = bolus.amount
            current.type = newType ?: current.type
            current.interfaceIDs.pumpId = bolus.interfaceIDs.pumpId
            database.bolusDao.updateExistingEntry(current)
            result.updated.add(current)
        }
        return result
    }

    class TransactionResult {

        val updated = mutableListOf<Bolus>()
    }
}