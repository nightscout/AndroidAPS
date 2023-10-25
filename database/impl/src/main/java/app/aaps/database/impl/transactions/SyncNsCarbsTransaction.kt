package app.aaps.database.impl.transactions

import app.aaps.database.entities.Carbs

/**
 * Sync the carbs from NS
 */
class SyncNsCarbsTransaction(private val carbs: List<Carbs>, private val nsClientMode: Boolean) :
    Transaction<SyncNsCarbsTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        for (carb in carbs) {
            val current: Carbs? =
                carb.interfaceIDs.nightscoutId?.let {
                    database.carbsDao.findByNSId(it)
                }

            if (current != null) {
                // nsId exists, allow only invalidation
                if (current.isValid && !carb.isValid) {
                    current.isValid = false
                    database.carbsDao.updateExistingEntry(current)
                    result.invalidated.add(current)
                }
                // and change duration
                if (current.duration != carb.duration && nsClientMode) {
                    current.amount = carb.amount
                    current.duration = carb.duration
                    database.carbsDao.updateExistingEntry(current)
                    result.updated.add(current)
                }
                continue
            }

            // not known nsId
            val existing = database.carbsDao.findByTimestamp(carb.timestamp)
            if (existing != null && existing.interfaceIDs.nightscoutId == null) {
                // the same record, update nsId only
                existing.interfaceIDs.nightscoutId = carb.interfaceIDs.nightscoutId
                existing.isValid = carb.isValid
                database.carbsDao.updateExistingEntry(existing)
                result.updatedNsId.add(existing)
            } else {
                database.carbsDao.insertNewEntry(carb)
                result.inserted.add(carb)
            }
        }
        return result
    }

    class TransactionResult {

        val updated = mutableListOf<Carbs>()
        val updatedNsId = mutableListOf<Carbs>()
        val inserted = mutableListOf<Carbs>()
        val invalidated = mutableListOf<Carbs>()
    }
}