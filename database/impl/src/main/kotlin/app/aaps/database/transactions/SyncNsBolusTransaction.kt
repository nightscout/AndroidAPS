package app.aaps.database.transactions

import app.aaps.database.entities.Bolus

/**
 * Sync the Bolus from NS
 */
class SyncNsBolusTransaction(private val boluses: List<Bolus>) : Transaction<SyncNsBolusTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        for (bolus in boluses) {
            val current: Bolus? =
                bolus.interfaceIDs.nightscoutId?.let {
                    database.bolusDao.getByNSId(it)
                }

            if (current != null) {
                // nsId exists, allow only invalidation or amount update (for drivers setting full amount upfront)
                if (current.isValid && !bolus.isValid) {
                    current.isValid = false
                    database.bolusDao.updateExistingEntry(current)
                    result.invalidated.add(current)
                }
                if (current.amount != bolus.amount) {
                    current.amount = bolus.amount
                    database.bolusDao.updateExistingEntry(current)
                    result.updated.add(current)
                }
                continue
            }

            // not known nsId
            // Check by pumpId + pumpType + pumpSerial (primary deduplication - prevents NS duplicate _id records)
            val existingByPumpId = if (bolus.interfaceIDs.pumpId != null && bolus.interfaceIDs.pumpType != null && bolus.interfaceIDs.pumpSerial != null) {
                database.bolusDao.findByPumpIds(bolus.interfaceIDs.pumpId!!, bolus.interfaceIDs.pumpType!!, bolus.interfaceIDs.pumpSerial!!)
            } else {
                null
            }

            if (existingByPumpId != null) {
                // Same pump bolus exists, just update/add the new nsId
                if (existingByPumpId.interfaceIDs.nightscoutId == null) {
                    existingByPumpId.interfaceIDs.nightscoutId = bolus.interfaceIDs.nightscoutId
                    existingByPumpId.isValid = bolus.isValid
                    existingByPumpId.amount = bolus.amount
                    database.bolusDao.updateExistingEntry(existingByPumpId)
                    result.updatedNsId.add(existingByPumpId)
                }
                // If existing already has a different nsId, this is a duplicate NS record - ignore it
                continue
            }

            // Fallback: check by timestamp (for manual boluses without pumpId)
            val existing = database.bolusDao.findByTimestamp(bolus.timestamp)
            if (existing != null && existing.interfaceIDs.nightscoutId == null) {
                // the same record, update nsId only and amount
                existing.interfaceIDs.nightscoutId = bolus.interfaceIDs.nightscoutId
                existing.isValid = bolus.isValid
                existing.amount = bolus.amount
                database.bolusDao.updateExistingEntry(existing)
                result.updatedNsId.add(existing)
            } else {
                database.bolusDao.insertNewEntry(bolus)
                result.inserted.add(bolus)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<Bolus>()
        val inserted = mutableListOf<Bolus>()
        val invalidated = mutableListOf<Bolus>()
        val updated = mutableListOf<Bolus>()
    }
}