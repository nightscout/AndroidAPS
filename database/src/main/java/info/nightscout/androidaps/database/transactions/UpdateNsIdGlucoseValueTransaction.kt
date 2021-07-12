package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.GlucoseValue

class UpdateNsIdGlucoseValueTransaction(val glucoseValue: GlucoseValue) : Transaction<Unit>() {

    override fun run() {
        val current = database.glucoseValueDao.findById(glucoseValue.id)
        if (current != null && current.interfaceIDs.nightscoutId != glucoseValue.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = glucoseValue.interfaceIDs.nightscoutId
            database.glucoseValueDao.updateExistingEntry(current)
        }
    }
}