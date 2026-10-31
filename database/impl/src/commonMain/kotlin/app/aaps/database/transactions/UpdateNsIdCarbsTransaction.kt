package app.aaps.database.transactions

import app.aaps.database.entities.Carbs

class UpdateNsIdCarbsTransaction(private val carbs: List<Carbs>) : Transaction<UpdateNsIdCarbsTransaction.TransactionResult>() {

    val result = TransactionResult()
    override suspend fun run(): TransactionResult {
        for (carb in carbs) {
            val current = database.carbsDao.findById(carb.id)
            if (current != null && current.interfaceIDs.nightscoutId != carb.interfaceIDs.nightscoutId) {
                current.interfaceIDs.nightscoutId = carb.interfaceIDs.nightscoutId
                database.carbsDao.updateExistingEntry(current)
                result.updatedNsId.add(current)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<Carbs>()
    }
}