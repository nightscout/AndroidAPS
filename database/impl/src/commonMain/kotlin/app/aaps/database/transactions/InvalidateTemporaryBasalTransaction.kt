package app.aaps.database.transactions

import app.aaps.database.entities.TemporaryBasal

class InvalidateTemporaryBasalTransaction(val id: Long) : Transaction<InvalidateTemporaryBasalTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val temporaryBasal = database.temporaryBasalDao.findById(id)
            ?: throw IllegalArgumentException("There is no such Temporary Basal with the specified ID.")
        if (temporaryBasal.isValid) {
            temporaryBasal.isValid = false
            database.temporaryBasalDao.updateExistingEntry(temporaryBasal)
            result.invalidated.add(temporaryBasal)
        }
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<TemporaryBasal>()
    }
}