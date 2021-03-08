package info.nightscout.androidaps.database.transactions

class InvalidateTherapyEventTransaction(val id: Long) : Transaction<Unit>() {
    override fun run() {
        val therapyEvent = database.therapyEventDao.findById(id)
            ?: throw IllegalArgumentException("There is no such TherapyEvent with the specified ID.")
        therapyEvent.isValid = false
        database.therapyEventDao.updateExistingEntry(therapyEvent)
    }
}