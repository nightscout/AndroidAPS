package app.aaps.database.transactions

import app.aaps.database.entities.ProfileSwitch

/**
 * Inserts or updates [profileSwitches] in one transaction, so an observer sees all of them or none. An insulin change
 * writes several switches that only make sense together.
 */
class InsertOrUpdateProfileSwitchTransaction(val profileSwitches: List<ProfileSwitch>) : Transaction<InsertOrUpdateProfileSwitchTransaction.TransactionResult>() {

    constructor(profileSwitch: ProfileSwitch) : this(listOf(profileSwitch))

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()

        for (profileSwitch in profileSwitches) {
            val current = database.profileSwitchDao.findById(profileSwitch.id)
            if (current == null) {
                database.profileSwitchDao.insertNewEntry(profileSwitch)
                result.inserted.add(profileSwitch)
            } else {
                database.profileSwitchDao.updateExistingEntry(profileSwitch)
                result.updated.add(profileSwitch)
            }
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<ProfileSwitch>()
        val updated = mutableListOf<ProfileSwitch>()
    }
}
