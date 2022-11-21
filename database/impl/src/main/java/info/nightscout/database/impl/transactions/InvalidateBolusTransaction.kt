package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.Bolus

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