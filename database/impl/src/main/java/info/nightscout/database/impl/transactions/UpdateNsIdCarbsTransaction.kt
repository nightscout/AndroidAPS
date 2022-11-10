package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.Carbs

class UpdateNsIdCarbsTransaction(val carbs: Carbs) : Transaction<Unit>() {

    override fun run() {
        val current = database.carbsDao.findById(carbs.id)
        if (current != null && current.interfaceIDs.nightscoutId != carbs.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = carbs.interfaceIDs.nightscoutId
            database.carbsDao.updateExistingEntry(current)
        }
    }
}