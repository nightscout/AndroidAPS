package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.transactions.Transaction

/**
 * Invalidates the GlucoseValue with the specified id
 */
class InvalidateGlucoseValueTransaction(val id: Long) : Transaction<Unit>() {
    override fun run() {
        val glucoseValue = database.glucoseValueDao.findById(id)
                ?: throw IllegalArgumentException("There is no such GlucoseValue with the specified ID.")
        glucoseValue.isValid = false
        database.glucoseValueDao.updateExistingEntry(glucoseValue)
    }
}