package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.embedments.InsulinConfiguration
import info.nightscout.database.entities.embedments.InterfaceIDs

/**
 * Creates or updates the Bolus
 */
class InsertOrUpdateBolusTransaction(
    private val bolus: Bolus
) : Transaction<InsertOrUpdateBolusTransaction.TransactionResult>() {

    constructor(
        timestamp: Long,
        amount: Double,
        type: Bolus.Type,
        notes: String? = null,
        isBasalInsulin: Boolean = false,
        insulinConfiguration: InsulinConfiguration? = null,
        interfaceIDs_backing: InterfaceIDs? = null
    ) : this(
        Bolus(
        timestamp = timestamp,
        amount = amount,
        type = type,
        notes = notes,
        isBasalInsulin = isBasalInsulin,
        insulinConfiguration = insulinConfiguration,
        interfaceIDs_backing = interfaceIDs_backing
    )
    )

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.bolusDao.findById(bolus.id)
        if (current == null) {
            database.bolusDao.insertNewEntry(bolus)
            result.inserted.add(bolus)
        } else {
            database.bolusDao.updateExistingEntry(bolus)
            result.updated.add(bolus)
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<Bolus>()
        val updated = mutableListOf<Bolus>()
    }
}