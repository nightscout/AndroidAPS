package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.Carbs

/**
 * Creates or updates the Carbs
 */
class InsertOrUpdateCarbsTransaction(
    private val carbs: Carbs
) : Transaction<InsertOrUpdateCarbsTransaction.TransactionResult>() {

    constructor(
        timestamp: Long,
        amount: Double,
        duration: Long,
        interfaceIDs_backing: InterfaceIDs? = null
    ) : this(Carbs(
        timestamp = timestamp,
        amount = amount,
        duration = duration,
        interfaceIDs_backing = interfaceIDs_backing
    ))

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.carbsDao.findById(carbs.id)
        if (current == null) {
            database.carbsDao.insertNewEntry(carbs)
            result.inserted.add(carbs)
        } else {
            database.carbsDao.updateExistingEntry(carbs)
            result.updated.add(carbs)
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<Carbs>()
        val updated = mutableListOf<Carbs>()
    }
}