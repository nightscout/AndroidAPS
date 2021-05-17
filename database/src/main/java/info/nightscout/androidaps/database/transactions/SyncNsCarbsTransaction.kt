package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.Carbs

/**
 * Sync the carbs from NS
 */
class SyncNsCarbsTransaction(private val carbs: Carbs, private val invalidateByNsOnly: Boolean) : Transaction<SyncNsCarbsTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        val current: Carbs? =
            carbs.interfaceIDs.nightscoutId?.let {
                database.carbsDao.findByNSId(it)
            }

        if (current != null) {
            // nsId exists, allow only invalidation
            if (current.isValid && !carbs.isValid) {
                current.isValid = false
                database.carbsDao.updateExistingEntry(current)
                result.invalidated.add(current)
            }
            return result
        }

        if (invalidateByNsOnly) return result

        // not known nsId
        val existing = database.carbsDao.findByTimestamp(carbs.timestamp)
        if (existing != null && existing.interfaceIDs.nightscoutId == null) {
            // the same record, update nsId only
            existing.interfaceIDs.nightscoutId = carbs.interfaceIDs.nightscoutId
            existing.isValid = carbs.isValid
            database.carbsDao.updateExistingEntry(existing)
            result.updatedNsId.add(existing)
        } else {
            database.carbsDao.insertNewEntry(carbs)
            result.inserted.add(carbs)
        }
        return result

    }

    class TransactionResult {

        val updatedNsId = mutableListOf<Carbs>()
        val inserted = mutableListOf<Carbs>()
        val invalidated = mutableListOf<Carbs>()
    }
}