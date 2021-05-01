package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.Bolus

/**
 * Sync the Bolus from NS
 */
class SyncNsBolusTransaction(private val bolus: Bolus, private val invalidateByNsOnly: Boolean) : Transaction<SyncNsBolusTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        val current: Bolus? =
            bolus.interfaceIDs.nightscoutId?.let {
                database.bolusDao.findByNSId(it)
            }

        if (current != null) {
            // nsId exists, allow only invalidation
            if (current.isValid && !bolus.isValid) {
                current.isValid = false
                database.bolusDao.updateExistingEntry(current)
                result.invalidated.add(current)
            }
            return result
        }

        if (invalidateByNsOnly) return result

        // not known nsId
        val existing = database.bolusDao.findByTimestamp(bolus.timestamp)
        if (existing != null && existing.interfaceIDs.nightscoutId == null) {
            // the same record, update nsId only
            existing.interfaceIDs.nightscoutId = bolus.interfaceIDs.nightscoutId
            existing.isValid = bolus.isValid
            database.bolusDao.updateExistingEntry(existing)
            result.updatedNsId.add(existing)
        } else {
            database.bolusDao.insertNewEntry(bolus)
            result.inserted.add(bolus)
        }
        return result

    }

    class TransactionResult {

        val updatedNsId = mutableListOf<Bolus>()
        val inserted = mutableListOf<Bolus>()
        val invalidated = mutableListOf<Bolus>()
    }
}