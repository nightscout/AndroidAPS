package app.aaps.database.transactions

import app.aaps.database.entities.Bolus

class UpdateNsIdBolusTransaction(private val boluses: List<Bolus>) : Transaction<UpdateNsIdBolusTransaction.TransactionResult>() {

    val result = TransactionResult()
    override suspend fun run(): TransactionResult {
        for (bolus in boluses) {
            val current = database.bolusDao.findById(bolus.id)
            if (current != null && current.interfaceIDs.nightscoutId != bolus.interfaceIDs.nightscoutId) {
                current.interfaceIDs.nightscoutId = bolus.interfaceIDs.nightscoutId
                database.bolusDao.updateExistingEntry(current)
                result.updatedNsId.add(current)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<Bolus>()
    }
}