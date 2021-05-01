package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.ProfileSwitch

/**
 * Sync the ProfileSwitch from NS
 */
class SyncNsProfileSwitchTransaction(private val profileSwitch: ProfileSwitch, private val invalidateByNsOnly: Boolean) : Transaction<SyncNsProfileSwitchTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        val current: ProfileSwitch? =
            profileSwitch.interfaceIDs.nightscoutId?.let {
                database.profileSwitchDao.findByNSId(it)
            }

        if (current != null) {
            // nsId exists, allow only invalidation
            if (current.isValid && !profileSwitch.isValid) {
                current.isValid = false
                database.profileSwitchDao.updateExistingEntry(current)
                result.invalidated.add(current)
            }
            return result
        }

        if (invalidateByNsOnly) return result

        // not known nsId
        val existing = database.profileSwitchDao.findByTimestamp(profileSwitch.timestamp)
        if (existing != null && existing.interfaceIDs.nightscoutId == null) {
            // the same record, update nsId only
            existing.interfaceIDs.nightscoutId = profileSwitch.interfaceIDs.nightscoutId
            existing.isValid = profileSwitch.isValid
            database.profileSwitchDao.updateExistingEntry(existing)
            result.updatedNsId.add(existing)
        } else {
            database.profileSwitchDao.insertNewEntry(profileSwitch)
            result.inserted.add(profileSwitch)
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<ProfileSwitch>()
        val inserted = mutableListOf<ProfileSwitch>()
        val invalidated = mutableListOf<ProfileSwitch>()
    }
}