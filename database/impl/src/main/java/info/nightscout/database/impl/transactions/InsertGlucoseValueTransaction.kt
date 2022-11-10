package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.GlucoseValue

/**
 * Creates the GlucoseValue
 */
class InsertGlucoseValueTransaction(val glucoseValue: GlucoseValue) : Transaction<Unit>() {

    override fun run() {
        database.glucoseValueDao.insert(glucoseValue)
    }
}