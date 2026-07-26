package app.aaps.database.transactions

import app.aaps.database.entities.ProfileSwitch

class UpdateNsIdProfileSwitchTransaction(val profileSwitches: List<ProfileSwitch>) : Transaction<UpdateNsIdProfileSwitchTransaction.TransactionResult>() {

    val result = TransactionResult()
    override suspend fun run(): TransactionResult {
        for (profileSwitch in profileSwitches) {
            val current = database.profileSwitchDao.findById(profileSwitch.id)
            if (current != null && current.interfaceIDs.nightscoutId != profileSwitch.interfaceIDs.nightscoutId) {
                current.interfaceIDs.nightscoutId = profileSwitch.interfaceIDs.nightscoutId
                database.profileSwitchDao.updateExistingEntry(current)
                result.updatedNsId.add(current)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<ProfileSwitch>()
    }
}