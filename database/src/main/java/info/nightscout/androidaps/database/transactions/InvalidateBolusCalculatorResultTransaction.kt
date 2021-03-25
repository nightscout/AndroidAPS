package info.nightscout.androidaps.database.transactions

class InvalidateBolusCalculatorResultTransaction(val id: Long) : Transaction<Unit>() {

    override fun run() {
        val bolusCalculatorResult = database.bolusCalculatorResultDao.findById(id)
            ?: throw IllegalArgumentException("There is no such BolusCalculatorResult with the specified ID.")

        bolusCalculatorResult.isValid = false
        database.bolusCalculatorResultDao.updateExistingEntry(bolusCalculatorResult)
    }
}