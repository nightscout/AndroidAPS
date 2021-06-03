package info.nightscout.androidaps.database.transactions

class InvalidateOfflineEventTransaction(val id: Long) : Transaction<Unit>() {
    override fun run() {
        val offlineEvent = database.offlineEventDao.findById(id)
            ?: throw IllegalArgumentException("There is no such OfflineEvent with the specified ID.")
        offlineEvent.isValid = false
        database.offlineEventDao.updateExistingEntry(offlineEvent)
    }
}