package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.ProfileSwitch

class UpdateNsIdProfileSwitchTransaction(val profileSwitch: ProfileSwitch) : Transaction<Unit>() {

    override fun run() {
        val current = database.profileSwitchDao.findById(profileSwitch.id)
        if (current != null && current.interfaceIDs.nightscoutId != profileSwitch.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = profileSwitch.interfaceIDs.nightscoutId
            database.profileSwitchDao.updateExistingEntry(current)
        }
    }
}