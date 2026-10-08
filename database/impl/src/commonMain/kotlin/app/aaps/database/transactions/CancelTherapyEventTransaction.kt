package app.aaps.database.transactions

import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.interfaces.end

class CancelTherapyEventTransaction(
    val id: Long,
    val timestamp: Long
) : Transaction<CancelTherapyEventTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.therapyEventDao.findById(id) ?: return result
        if (!current.isValid) return result
        if (current.timestamp >= timestamp) return result
        // Long.MAX_VALUE encodes "indefinite" (no end) for TE created by indefinite scenes;
        // avoid the additive overflow that would wrap negative and trip this guard.
        val isFiniteDuration = current.duration > 0 && current.duration < Long.MAX_VALUE
        if (isFiniteDuration && current.timestamp + current.duration <= timestamp) return result
        current.end = timestamp
        database.therapyEventDao.updateExistingEntry(current)
        result.updated.add(current)
        return result
    }

    class TransactionResult {

        val updated = mutableListOf<TherapyEvent>()
    }
}
