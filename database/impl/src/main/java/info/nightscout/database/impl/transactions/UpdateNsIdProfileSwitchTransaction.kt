package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.ProfileSwitch

class UpdateNsIdProfileSwitchTransaction(val profileSwitch: ProfileSwitch) : Transaction<Unit>() {

    override fun run() {
        val current = database.profileSwitchDao.findById(profileSwitch.id)
        if (current != null && current.interfaceIDs.nightscoutId != profileSwitch.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = profileSwitch.interfaceIDs.nightscoutId
            database.profileSwitchDao.updateExistingEntry(current)
        }
    }
}