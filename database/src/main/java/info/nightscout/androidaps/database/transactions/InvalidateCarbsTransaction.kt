package info.nightscout.androidaps.database.transactions

class InvalidateCarbsTransaction(val id: Long) : Transaction<Unit>() {

    override fun run() {
        val carbs = database.carbsDao.findById(id)
            ?: throw IllegalArgumentException("There is no such Carbs with the specified ID.")
        carbs.isValid = false
        database.carbsDao.updateExistingEntry(carbs)
    }
}