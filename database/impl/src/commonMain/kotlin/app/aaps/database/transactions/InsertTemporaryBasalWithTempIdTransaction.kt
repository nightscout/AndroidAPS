package app.aaps.database.transactions

import app.aaps.database.entities.TemporaryBasal

/**
 * Creates or updates the TemporaryBasal from pump synchronization
 */
class InsertTemporaryBasalWithTempIdTransaction(
    private val temporaryBasal: TemporaryBasal
) : Transaction<InsertTemporaryBasalWithTempIdTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        temporaryBasal.interfaceIDs.temporaryId ?: temporaryBasal.interfaceIDs.pumpType
        ?: temporaryBasal.interfaceIDs.pumpSerial
        ?: throw IllegalStateException("Some pump ID is null")
        val result = TransactionResult()
        val current = database.temporaryBasalDao.findByPumpTempIds(temporaryBasal.interfaceIDs.temporaryId!!, temporaryBasal.interfaceIDs.pumpType!!, temporaryBasal.interfaceIDs.pumpSerial!!)
        if (current == null) {
            // insertNewEntry, not insert: only it reports the change, so the IOB and the overview know of it now,
            // not first when the pump history replaces the temporary id (#5280).
            temporaryBasal.id = database.temporaryBasalDao.insertNewEntry(temporaryBasal)
            result.inserted.add(temporaryBasal)
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<TemporaryBasal>()
    }
}