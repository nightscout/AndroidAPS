package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.BolusCalculatorResult

class UpdateNsIdBolusCalculatorResultTransaction(val bolusCalculatorResult: BolusCalculatorResult) : Transaction<Unit>() {

    override fun run() {
        val current = database.bolusCalculatorResultDao.findById(bolusCalculatorResult.id)
        if (current != null && current.interfaceIDs.nightscoutId != bolusCalculatorResult.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = bolusCalculatorResult.interfaceIDs.nightscoutId
            database.bolusCalculatorResultDao.updateExistingEntry(current)
        }
    }
}