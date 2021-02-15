package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.GlucoseValue

/**
 * Updates the GlucoseValue
 */
class UpdateGlucoseValueTransaction(val glucoseValue: GlucoseValue) : Transaction<Unit>() {

    override fun run() {
        database.glucoseValueDao.updateExistingEntry(glucoseValue)
    }
}