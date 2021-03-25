package info.nightscout.androidaps.database.transactions

class InvalidateBolusTransaction(val id: Long) : Transaction<Unit>() {

    override fun run() {
        val bolus = database.bolusDao.findById(id)
            ?: throw IllegalArgumentException("There is no such Bolus with the specified ID.")
        bolus.isValid = false
        database.bolusDao.updateExistingEntry(bolus)
    }
}