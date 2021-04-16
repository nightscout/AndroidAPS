package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.Bolus

class InvalidateBolusTransaction(val id: Long) : Transaction<InvalidateBolusTransaction.TransactionResult>() {

    override fun run() : TransactionResult {
        val result = TransactionResult()
        val bolus = database.bolusDao.findById(id)
            ?: throw IllegalArgumentException("There is no such Bolus with the specified ID.")
        bolus.isValid = false
        database.bolusDao.updateExistingEntry(bolus)
        result.invalidated.add(bolus)
        return result
    }

    class TransactionResult {
        val invalidated = mutableListOf<Bolus>()
    }
}