package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.GlucoseValue

/**
 * Updates the GlucoseValue
 */
class UpdateGlucoseValueTransaction(val glucoseValue: GlucoseValue) : Transaction<Unit>() {

    override fun run() {
        database.glucoseValueDao.updateExistingEntry(glucoseValue)
    }
}