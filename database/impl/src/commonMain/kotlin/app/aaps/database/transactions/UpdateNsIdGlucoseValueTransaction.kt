package app.aaps.database.transactions

import app.aaps.database.entities.GlucoseValue

class UpdateNsIdGlucoseValueTransaction(private val glucoseValues: List<GlucoseValue>) : Transaction<UpdateNsIdGlucoseValueTransaction.TransactionResult>() {

    val result = TransactionResult()
    override suspend fun run(): TransactionResult {
        for (glucoseValue in glucoseValues) {
            val current = database.glucoseValueDao.findById(glucoseValue.id)
            if (current != null && current.interfaceIDs.nightscoutId != glucoseValue.interfaceIDs.nightscoutId) {
                current.interfaceIDs.nightscoutId = glucoseValue.interfaceIDs.nightscoutId
                database.glucoseValueDao.updateExistingEntry(current)
                result.updatedNsId.add(current)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<GlucoseValue>()
    }
}