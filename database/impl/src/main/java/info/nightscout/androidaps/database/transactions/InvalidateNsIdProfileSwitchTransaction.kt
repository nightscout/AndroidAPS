package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.Food
import info.nightscout.androidaps.database.entities.ProfileSwitch

class InvalidateNsIdProfileSwitchTransaction(val nsId: String) : Transaction<InvalidateNsIdProfileSwitchTransaction.TransactionResult>() {

    override fun run() : TransactionResult{
        val result = TransactionResult()
        val current = database.profileSwitchDao.findByNSId(nsId)
        if (current != null) {
            current.isValid = false
            database.profileSwitchDao.updateExistingEntry(current)
            result.invalidated.add(current)
        }
        return result
    }

    class TransactionResult {
        val invalidated = mutableListOf<ProfileSwitch>()
    }

}