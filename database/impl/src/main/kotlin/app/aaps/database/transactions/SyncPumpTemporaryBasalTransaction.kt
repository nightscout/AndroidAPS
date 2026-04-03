package app.aaps.database.transactions

import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.interfaces.end

/**
 * Creates or updates the Temporary basal from pump synchronization
 */
class SyncPumpTemporaryBasalTransaction(
    private val temporaryBasal: TemporaryBasal,
    private val type: TemporaryBasal.Type? // extra parameter because field is not nullable in TemporaryBasal.class
) : Transaction<SyncPumpTemporaryBasalTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        temporaryBasal.interfaceIDs.pumpId ?: temporaryBasal.interfaceIDs.pumpType
        ?: temporaryBasal.interfaceIDs.pumpSerial
        ?: throw IllegalStateException("Some pump ID is null")
        val result = TransactionResult()
        val existing = database.temporaryBasalDao.findByPumpIds(temporaryBasal.interfaceIDs.pumpId!!, temporaryBasal.interfaceIDs.pumpType!!, temporaryBasal.interfaceIDs.pumpSerial!!)
        if (existing != null) {
            if (
                existing.timestamp != temporaryBasal.timestamp ||
                existing.rate != temporaryBasal.rate ||
                existing.duration != temporaryBasal.duration && existing.interfaceIDs.endId == null ||
                existing.type != (type ?: existing.type)
            ) {
                val old = existing.copy()
                existing.timestamp = temporaryBasal.timestamp
                existing.rate = temporaryBasal.rate
                existing.duration = temporaryBasal.duration
                existing.type = type ?: existing.type
                database.temporaryBasalDao.updateExistingEntry(existing)
                result.updated.add(Pair(old, existing))
            }
        } else {
            val running = database.temporaryBasalDao.getTemporaryBasalActiveAtLegacy(temporaryBasal.timestamp)
            if (running != null) {
                val old = running.copy()
                running.end = temporaryBasal.timestamp
                running.interfaceIDs.endId = temporaryBasal.interfaceIDs.pumpId
                database.temporaryBasalDao.updateExistingEntry(running)
                result.updated.add(Pair(old, running))
            }
            database.temporaryBasalDao.insertNewEntry(temporaryBasal)
            result.inserted.add(temporaryBasal)
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<TemporaryBasal>()
        val updated = mutableListOf<Pair<TemporaryBasal, TemporaryBasal>>()
    }
}