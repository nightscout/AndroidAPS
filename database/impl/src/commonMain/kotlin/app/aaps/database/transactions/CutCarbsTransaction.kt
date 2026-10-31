package app.aaps.database.transactions

import app.aaps.database.entities.Carbs
import app.aaps.database.entities.interfaces.end
import kotlin.math.roundToInt

class CutCarbsTransaction(val id: Long, val end: Long) : Transaction<CutCarbsTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()
        val carbs = database.carbsDao.findById(id)
            ?: throw IllegalArgumentException("There is no such Carbs with the specified ID.")
        if (carbs.timestamp == end) {
            carbs.isValid = false
            database.carbsDao.updateExistingEntry(carbs)
            result.invalidated.add(carbs)
        } else if (end in carbs.timestamp..carbs.end) {
            val pctRun = (end - carbs.timestamp) / carbs.duration.toDouble()
            carbs.amount = (carbs.amount * pctRun).roundToInt().toDouble()
            carbs.end = end
            database.carbsDao.updateExistingEntry(carbs)
            result.updated.add(carbs)
        }
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<Carbs>()
        val updated = mutableListOf<Carbs>()
    }
}