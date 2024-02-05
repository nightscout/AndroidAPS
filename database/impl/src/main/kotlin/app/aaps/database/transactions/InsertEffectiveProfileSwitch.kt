package app.aaps.database.transactions

import app.aaps.database.entities.EffectiveProfileSwitch

class InsertEffectiveProfileSwitch(private val effectiveProfileSwitch: EffectiveProfileSwitch) : Transaction<InsertEffectiveProfileSwitch.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        database.effectiveProfileSwitchDao.insertNewEntry(effectiveProfileSwitch)
        result.inserted.add(effectiveProfileSwitch)
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<EffectiveProfileSwitch>()
    }
}