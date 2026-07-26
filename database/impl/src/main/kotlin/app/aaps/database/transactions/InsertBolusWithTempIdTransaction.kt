package app.aaps.database.transactions

import app.aaps.database.entities.Bolus

/**
 * Creates or updates the Bolus from pump synchronization
 */
class InsertBolusWithTempIdTransaction(
    private val bolus: Bolus
) : Transaction<InsertBolusWithTempIdTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        bolus.interfaceIDs.temporaryId ?: bolus.interfaceIDs.pumpType ?: bolus.interfaceIDs.pumpSerial ?: throw IllegalStateException("Some pump ID is null")
        val result = TransactionResult()
        val current = database.bolusDao.findByPumpTempIds(bolus.interfaceIDs.temporaryId!!, bolus.interfaceIDs.pumpType!!, bolus.interfaceIDs.pumpSerial!!)
        if (current == null) {
            bolus.id = database.bolusDao.insert(bolus)
            result.inserted.add(bolus)
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<Bolus>()
    }
}