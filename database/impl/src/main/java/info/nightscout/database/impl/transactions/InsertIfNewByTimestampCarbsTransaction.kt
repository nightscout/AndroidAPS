package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.Carbs
import info.nightscout.database.entities.embedments.InterfaceIDs

/**
 * Creates Carbs if record doesn't exist
 */
class InsertIfNewByTimestampCarbsTransaction(
    private val carbs: Carbs
) : Transaction<InsertIfNewByTimestampCarbsTransaction.TransactionResult>() {

    constructor(
        timestamp: Long,
        amount: Double,
        duration: Long,
        interfaceIDs_backing: InterfaceIDs? = null
    ) : this(
        Carbs(
        timestamp = timestamp,
        amount = amount,
        duration = duration,
        interfaceIDs_backing = interfaceIDs_backing
    )
    )

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.carbsDao.findByTimestamp(carbs.timestamp)
        if (current == null) {
            database.carbsDao.insertNewEntry(carbs)
            result.inserted.add(carbs)
        } else
            result.existing.add(carbs)
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<Carbs>()
        val existing = mutableListOf<Carbs>()
    }
}