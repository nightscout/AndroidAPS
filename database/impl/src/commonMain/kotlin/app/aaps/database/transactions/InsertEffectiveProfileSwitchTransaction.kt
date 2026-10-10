package app.aaps.database.transactions

import app.aaps.database.entities.EffectiveProfileSwitch

class InsertEffectiveProfileSwitchTransaction(private val effectiveProfileSwitch: EffectiveProfileSwitch) : Transaction<InsertEffectiveProfileSwitchTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()

        database.effectiveProfileSwitchDao.insertNewEntry(effectiveProfileSwitch)
        result.inserted.add(effectiveProfileSwitch)
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<EffectiveProfileSwitch>()
    }
}