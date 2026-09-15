package app.aaps.database.transactions

import app.aaps.database.entities.CalibrationEntry

/**
 * Sync the CalibrationEntries from NS (follower side). Dedup by nightscoutId.
 */
class SyncNsCalibrationEntryTransaction(private val calibrationEntries: List<CalibrationEntry>) : Transaction<SyncNsCalibrationEntryTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()

        for (calibrationEntry in calibrationEntries) {
            val current: CalibrationEntry? =
                calibrationEntry.interfaceIDs.nightscoutId?.let {
                    database.calibrationEntryDao.findByNSId(it)
                }

            if (current != null) {
                // nsId exists, update if different
                if (!current.contentEqualsTo(calibrationEntry)) {
                    if (calibrationEntry.isValid && current.isValid) result.updated.add(current)
                    else if (!calibrationEntry.isValid && current.isValid) result.invalidated.add(current)
                    current.copyFrom(calibrationEntry)
                    database.calibrationEntryDao.updateExistingEntry(current)
                }
            } else {
                // Unknown nsId. It may be our own just-uploaded entry whose nsId hasn't been
                // written locally yet (the addToNsIdCalibrationEntries update is async) — match by
                // timestamp and attach the nsId instead of inserting a duplicate.
                val existing = database.calibrationEntryDao.findByTimestamp(calibrationEntry.timestamp)
                if (existing != null && existing.interfaceIDs.nightscoutId == null) {
                    existing.interfaceIDs.nightscoutId = calibrationEntry.interfaceIDs.nightscoutId
                    existing.isValid = calibrationEntry.isValid
                    database.calibrationEntryDao.updateExistingEntry(existing)
                    result.updatedNsId.add(existing)
                } else {
                    database.calibrationEntryDao.insertNewEntry(calibrationEntry)
                    result.inserted.add(calibrationEntry)
                }
            }
        }
        return result
    }

    class TransactionResult {

        val updated = mutableListOf<CalibrationEntry>()
        val inserted = mutableListOf<CalibrationEntry>()
        val invalidated = mutableListOf<CalibrationEntry>()
        val updatedNsId = mutableListOf<CalibrationEntry>()
    }
}
