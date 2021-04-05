package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.database.interfaces.end

/**
 * Creates or updates the Temporary basal from pump synchronization
 */
class SyncPumpTemporaryBasalTransaction(
    private val temporaryBasal: TemporaryBasal,
    private val type: TemporaryBasal.Type? // extra parameter because field is not nullable in TemporaryBasal.class
) : Transaction<SyncPumpTemporaryBasalTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        temporaryBasal.interfaceIDs.pumpId ?: temporaryBasal.interfaceIDs.pumpType
        ?: temporaryBasal.interfaceIDs.pumpSerial
        ?: throw IllegalStateException("Some pump ID is null")
        val result = TransactionResult()
        val current = database.temporaryBasalDao.findByPumpIds(temporaryBasal.interfaceIDs.pumpId!!, temporaryBasal.interfaceIDs.pumpType!!, temporaryBasal.interfaceIDs.pumpSerial!!)
        if (current != null) {
            if (
                current.timestamp != temporaryBasal.timestamp ||
                current.rate != temporaryBasal.rate ||
                current.duration != temporaryBasal.duration  && current.interfaceIDs.endId == null ||
                current.type != type ?: current.type
            ) {
                current.timestamp = temporaryBasal.timestamp
                current.rate = temporaryBasal.rate
                current.duration = temporaryBasal.duration
                current.type = type ?: current.type
                database.temporaryBasalDao.updateExistingEntry(current)
                result.updated.add(current)
            }
        } else {
            val running = database.temporaryBasalDao.getTemporaryBasalActiveAt(temporaryBasal.timestamp, temporaryBasal.interfaceIDs.pumpType!!, temporaryBasal.interfaceIDs.pumpSerial!!).blockingGet()
            if (running != null) {
                running.end = temporaryBasal.timestamp
                database.temporaryBasalDao.updateExistingEntry(running)
                result.updated.add(running)
            }
            database.temporaryBasalDao.insertNewEntry(temporaryBasal)
            result.inserted.add(temporaryBasal)
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<TemporaryBasal>()
        val updated = mutableListOf<TemporaryBasal>()
    }
}