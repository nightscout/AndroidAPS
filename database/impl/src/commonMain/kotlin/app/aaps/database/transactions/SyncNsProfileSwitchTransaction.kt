package app.aaps.database.transactions

import app.aaps.database.entities.ProfileSwitch

/**
 * Sync the ProfileSwitch from NS
 */
class SyncNsProfileSwitchTransaction(private val profileSwitches: List<ProfileSwitch>) : Transaction<SyncNsProfileSwitchTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()

        for (profileSwitch in profileSwitches) {
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
                // Allow update duration to shorter only.
                // duration == 0 means "permanent/indefinite" for PS, so it must compare as
                // infinite — otherwise a permanent→finite cut (incoming > 0, current == 0)
                // would be wrongly rejected as "longer". Incoming 0 (lengthening to permanent)
                // stays rejected.
                val isCut = profileSwitch.duration > 0 &&
                    (current.duration == 0L || profileSwitch.duration < current.duration)
                if (current.duration != profileSwitch.duration && isCut) {
                    current.duration = profileSwitch.duration
                    database.profileSwitchDao.updateExistingEntry(current)
                    result.updatedDuration.add(current)
                }
                continue
            }

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
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<ProfileSwitch>()
        val inserted = mutableListOf<ProfileSwitch>()
        val invalidated = mutableListOf<ProfileSwitch>()
        val updatedDuration = mutableListOf<ProfileSwitch>()
    }
}
