package app.aaps.database.transactions

import app.aaps.database.entities.RunningMode

class InvalidateRunningModeTransaction(val id: Long) : Transaction<InvalidateRunningModeTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val runningMode = database.runningModeDao.findById(id)
            ?: throw IllegalArgumentException("There is no such RunningMode with the specified ID.")
        if (runningMode.isValid) {
            runningMode.isValid = false
            database.runningModeDao.updateExistingEntry(runningMode)
            result.invalidated.add(runningMode)
        }
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<RunningMode>()
    }
}