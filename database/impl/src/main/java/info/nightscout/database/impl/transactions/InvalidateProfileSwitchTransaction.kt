package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.ProfileSwitch

class InvalidateProfileSwitchTransaction(val id: Long) : Transaction<InvalidateProfileSwitchTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val profileSwitch = database.profileSwitchDao.findById(id)
            ?: throw IllegalArgumentException("There is no such ProfileSwitch with the specified ID.")
        profileSwitch.isValid = false
        database.profileSwitchDao.updateExistingEntry(profileSwitch)
        result.invalidated.add(profileSwitch)
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<ProfileSwitch>()
    }
}