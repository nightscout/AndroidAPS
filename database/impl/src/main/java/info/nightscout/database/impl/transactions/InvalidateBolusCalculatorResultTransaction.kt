package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.BolusCalculatorResult

class InvalidateBolusCalculatorResultTransaction(val id: Long) : Transaction<InvalidateBolusCalculatorResultTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val bolusCalculatorResult = database.bolusCalculatorResultDao.findById(id)
            ?: throw IllegalArgumentException("There is no such BolusCalculatorResult with the specified ID.")
        if (bolusCalculatorResult.isValid) {
            bolusCalculatorResult.isValid = false
            database.bolusCalculatorResultDao.updateExistingEntry(bolusCalculatorResult)
            result.invalidated.add(bolusCalculatorResult)
        }
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<BolusCalculatorResult>()
    }
}