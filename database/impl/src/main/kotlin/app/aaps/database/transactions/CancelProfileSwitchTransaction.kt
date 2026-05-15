package app.aaps.database.transactions

import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.interfaces.end

class CancelProfileSwitchTransaction(
    val id: Long,
    val timestamp: Long
) : Transaction<CancelProfileSwitchTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.profileSwitchDao.findById(id) ?: return result
        if (!current.isValid) return result
        if (current.timestamp >= timestamp) return result
        // Long.MAX_VALUE encodes "indefinite" elsewhere (TT/TE) — defensively avoid the additive
        // overflow that would wrap negative and trip this guard, even though current PS scenes
        // use 0 for permanent rather than MAX_VALUE.
        val isFiniteDuration = current.duration > 0 && current.duration < Long.MAX_VALUE
        if (isFiniteDuration && current.timestamp + current.duration <= timestamp) return result
        current.end = timestamp
        database.profileSwitchDao.updateExistingEntry(current)
        result.updated.add(current)
        return result
    }

    class TransactionResult {

        val updated = mutableListOf<ProfileSwitch>()
    }
}
