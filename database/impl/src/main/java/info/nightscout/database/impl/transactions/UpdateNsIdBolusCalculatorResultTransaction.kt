package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.BolusCalculatorResult

class UpdateNsIdBolusCalculatorResultTransaction(val bolusCalculatorResult: BolusCalculatorResult) : Transaction<Unit>() {

    override fun run() {
        val current = database.bolusCalculatorResultDao.findById(bolusCalculatorResult.id)
        if (current != null && current.interfaceIDs.nightscoutId != bolusCalculatorResult.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = bolusCalculatorResult.interfaceIDs.nightscoutId
            database.bolusCalculatorResultDao.updateExistingEntry(current)
        }
    }
}