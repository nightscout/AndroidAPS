package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.Carbs

class UpdateNsIdCarbsTransaction(val carbs: Carbs) : Transaction<Unit>() {

    override fun run() {
        val current = database.carbsDao.findById(carbs.id)
        if (current != null && current.interfaceIDs.nightscoutId != carbs.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = carbs.interfaceIDs.nightscoutId
            database.carbsDao.updateExistingEntry(current)
        }
    }
}