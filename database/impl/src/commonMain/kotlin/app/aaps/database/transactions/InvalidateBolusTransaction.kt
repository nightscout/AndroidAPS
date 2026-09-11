package app.aaps.database.transactions

class InvalidateBolusTransaction(val id: Long) : Transaction<InvalidateBolusTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val bolus = database.bolusDao.findById(id)
            ?: throw IllegalArgumentException("There is no such Bolus with the specified ID.")
        if (bolus.isValid) {
            bolus.isValid = false
            database.bolusDao.updateExistingEntry(bolus)
            result.invalidated.add(bolus)
        }
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<app.aaps.database.entities.Bolus>()
    }
}