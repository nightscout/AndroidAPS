package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.embedments.InsulinConfiguration
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.Bolus
import java.lang.IllegalStateException

/**
 * Creates or updates the Bolus from pump synchronization
 */
class SyncPumpBolusTransaction(
    private val bolus: Bolus,
) : Transaction<SyncPumpBolusTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        bolus.interfaceIDs.pumpId ?: bolus.interfaceIDs.pumpType ?: bolus.interfaceIDs.pumpSerial ?:
            throw IllegalStateException("Some pump ID is null")
        val result = TransactionResult()
        val current = database.bolusDao.findByPumpIds(bolus.interfaceIDs.pumpId!!, bolus.interfaceIDs.pumpType!!, bolus.interfaceIDs.pumpSerial!!)
        if (current == null) {
            database.bolusDao.insertNewEntry(bolus)
            result.inserted.add(bolus)
        } else {
            bolus.isValid = current.isValid
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