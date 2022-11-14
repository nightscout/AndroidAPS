package info.nightscout.database.impl.transactions

class InvalidateTemporaryTargetTransaction(val id: Long) : Transaction<Unit>() {
    override fun run() {
        val temporaryTarget = database.temporaryTargetDao.findById(id)
            ?: throw IllegalArgumentException("There is no such TemporaryTarget with the specified ID.")
        temporaryTarget.isValid = false
        database.temporaryTargetDao.updateExistingEntry(temporaryTarget)
    }
}