package app.aaps.database.transactions

import app.aaps.database.entities.CalibrationEntry

/**
 * Inserts or updates the CalibrationEntry.
 * Used for locally created entries (master side, no nightscoutId yet).
 */
class InsertOrUpdateCalibrationEntryTransaction(private val calibrationEntry: CalibrationEntry) : Transaction<InsertOrUpdateCalibrationEntryTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.calibrationEntryDao.findById(calibrationEntry.id)
        if (current == null) {
            database.calibrationEntryDao.insertNewEntry(calibrationEntry)
            result.inserted.add(calibrationEntry)
        } else {
            database.calibrationEntryDao.updateExistingEntry(calibrationEntry)
            result.updated.add(calibrationEntry)
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<CalibrationEntry>()
        val updated = mutableListOf<CalibrationEntry>()
    }
}
