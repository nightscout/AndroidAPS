package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.GlucoseValue

/**
 * Creates the GlucoseValue
 */
class InsertGlucoseValueTransaction(val glucoseValue: GlucoseValue) : Transaction<Unit>() {

    override fun run() {
        database.glucoseValueDao.insert(glucoseValue)
    }
}