package app.aaps.database.transactions

import app.aaps.database.entities.BolusCalculatorResult

/**
 * Creates or updates the BolusCalculatorResult
 */
class InsertOrUpdateBolusCalculatorResultTransaction(
    private val bolusCalculatorResult: BolusCalculatorResult
) : Transaction<InsertOrUpdateBolusCalculatorResultTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.bolusCalculatorResultDao.findById(bolusCalculatorResult.id)
        if (current == null) {
            database.bolusCalculatorResultDao.insertNewEntry(bolusCalculatorResult)
            result.inserted.add(bolusCalculatorResult)
        } else {
            database.bolusCalculatorResultDao.updateExistingEntry(bolusCalculatorResult)
            result.updated.add(bolusCalculatorResult)
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<BolusCalculatorResult>()
        val updated = mutableListOf<BolusCalculatorResult>()
    }
}