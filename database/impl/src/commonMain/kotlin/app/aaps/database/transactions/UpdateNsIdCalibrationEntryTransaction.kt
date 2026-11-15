package app.aaps.database.transactions

import app.aaps.database.entities.CalibrationEntry

class UpdateNsIdCalibrationEntryTransaction(private val calibrationEntries: List<CalibrationEntry>) : Transaction<UpdateNsIdCalibrationEntryTransaction.TransactionResult>() {

    val result = TransactionResult()
    override suspend fun run(): TransactionResult {
        for (calibrationEntry in calibrationEntries) {
            val current = database.calibrationEntryDao.findById(calibrationEntry.id)
            if (current != null && current.interfaceIDs.nightscoutId != calibrationEntry.interfaceIDs.nightscoutId) {
                current.interfaceIDs.nightscoutId = calibrationEntry.interfaceIDs.nightscoutId
                database.calibrationEntryDao.updateExistingEntry(current)
                result.updatedNsId.add(current)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<CalibrationEntry>()
    }
}
