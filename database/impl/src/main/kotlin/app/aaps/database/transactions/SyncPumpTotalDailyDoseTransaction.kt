package app.aaps.database.transactions

import app.aaps.database.entities.TotalDailyDose

/**
 * Creates or updates the TotalDailyDose from pump synchronization
 */
class SyncPumpTotalDailyDoseTransaction(
    private val tdd: TotalDailyDose
) : Transaction<SyncPumpTotalDailyDoseTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        tdd.interfaceIDs.pumpType ?: tdd.interfaceIDs.pumpSerial
        ?: throw IllegalStateException("Some pump ID is null")

        val result = TransactionResult()
        var current: TotalDailyDose? = null
        // search by pumpId
        if (tdd.interfaceIDs.pumpId != null) {
            current = database.totalDailyDoseDao.findByPumpIds(tdd.interfaceIDs.pumpId!!, tdd.interfaceIDs.pumpType!!, tdd.interfaceIDs.pumpSerial!!)
        }
        // search by timestamp
        if (current == null) {
            current = database.totalDailyDoseDao.findByPumpTimestamp(tdd.timestamp, tdd.interfaceIDs.pumpType, tdd.interfaceIDs.pumpSerial)
        }
        if (current == null) {
            database.totalDailyDoseDao.insertNewEntry(tdd)
            result.inserted.add(tdd)
        } else {
            current.basalAmount = tdd.basalAmount
            current.bolusAmount = tdd.bolusAmount
            current.totalAmount = tdd.totalAmount
            current.carbs = tdd.carbs
            current.interfaceIDs.pumpId = tdd.interfaceIDs.pumpId
            database.totalDailyDoseDao.updateExistingEntry(current)
            result.updated.add(current)
        }

        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<TotalDailyDose>()
        val updated = mutableListOf<TotalDailyDose>()
    }
}