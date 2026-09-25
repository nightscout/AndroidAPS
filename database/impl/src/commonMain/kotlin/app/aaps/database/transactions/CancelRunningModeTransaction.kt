package app.aaps.database.transactions

import app.aaps.database.entities.RunningMode
import app.aaps.database.entities.interfaces.end

class CancelRunningModeTransaction(
    val id: Long,
    val timestamp: Long
) : Transaction<CancelRunningModeTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.runningModeDao.findById(id) ?: return result
        if (!current.isValid) return result
        if (current.timestamp >= timestamp) return result
        // Long.MAX_VALUE encodes "indefinite" elsewhere — defensively avoid the additive
        // overflow that would wrap negative and trip this guard, even though current RM scenes
        // use 0 for permanent rather than MAX_VALUE.
        val isFiniteDuration = current.duration > 0 && current.duration < Long.MAX_VALUE
        if (isFiniteDuration && current.timestamp + current.duration <= timestamp) return result
        current.end = timestamp
        database.runningModeDao.updateExistingEntry(current)
        result.updated.add(current)
        return result
    }

    class TransactionResult {

        val updated = mutableListOf<RunningMode>()
    }
}
