package info.nightscout.androidaps.database.transactions

class InvalidateFoodTransaction(val id: Long) : Transaction<Unit>() {

    override fun run() {
        val food = database.foodDao.findById(id)
            ?: throw IllegalArgumentException("There is no such Food with the specified ID.")
        food.isValid = false
        database.foodDao.updateExistingEntry(food)
    }
}