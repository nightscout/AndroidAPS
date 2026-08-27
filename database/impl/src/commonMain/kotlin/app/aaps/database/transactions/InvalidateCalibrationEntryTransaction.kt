package app.aaps.database.transactions

import app.aaps.database.entities.CalibrationEntry

/**
 * Invalidates the CalibrationEntry with the specified id
 */
class InvalidateCalibrationEntryTransaction(val id: Long) : Transaction<InvalidateCalibrationEntryTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val calibrationEntry = database.calibrationEntryDao.findById(id)
            ?: throw IllegalArgumentException("There is no such CalibrationEntry with the specified ID.")
        if (calibrationEntry.isValid) {
            calibrationEntry.isValid = false
            database.calibrationEntryDao.updateExistingEntry(calibrationEntry)
            result.invalidated.add(calibrationEntry)
        }
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<CalibrationEntry>()
    }
}
