package app.aaps.database.transactions

import app.aaps.database.entities.EffectiveProfileSwitch

class InvalidateEffectiveProfileSwitchTransaction(val id: Long) : Transaction<InvalidateEffectiveProfileSwitchTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val effectiveProfileSwitch = database.effectiveProfileSwitchDao.findById(id)
            ?: throw IllegalArgumentException("There is no such EffectiveProfileSwitch with the specified ID.")
        if (effectiveProfileSwitch.isValid) {
            effectiveProfileSwitch.isValid = false
            database.effectiveProfileSwitchDao.updateExistingEntry(effectiveProfileSwitch)
            result.invalidated.add(effectiveProfileSwitch)
        }
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<EffectiveProfileSwitch>()
    }
}