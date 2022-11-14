package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.ProfileSwitch

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