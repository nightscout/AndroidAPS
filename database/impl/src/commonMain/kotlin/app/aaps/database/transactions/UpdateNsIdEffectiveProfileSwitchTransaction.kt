package app.aaps.database.transactions

import app.aaps.database.entities.EffectiveProfileSwitch

class UpdateNsIdEffectiveProfileSwitchTransaction(private val effectiveProfileSwitches: List<EffectiveProfileSwitch>) : Transaction<UpdateNsIdEffectiveProfileSwitchTransaction.TransactionResult>() {

    val result = TransactionResult()
    override suspend fun run(): TransactionResult {
        for (effectiveProfileSwitch in effectiveProfileSwitches) {
            val current = database.effectiveProfileSwitchDao.findById(effectiveProfileSwitch.id)
            if (current != null && current.interfaceIDs.nightscoutId != effectiveProfileSwitch.interfaceIDs.nightscoutId) {
                current.interfaceIDs.nightscoutId = effectiveProfileSwitch.interfaceIDs.nightscoutId
                database.effectiveProfileSwitchDao.updateExistingEntry(current)
                result.updatedNsId.add(current)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<EffectiveProfileSwitch>()
    }
}