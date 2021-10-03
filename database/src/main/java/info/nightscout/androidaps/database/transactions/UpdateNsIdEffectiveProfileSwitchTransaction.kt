package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.EffectiveProfileSwitch

class UpdateNsIdEffectiveProfileSwitchTransaction(val effectiveProfileSwitch: EffectiveProfileSwitch) : Transaction<Unit>() {

    override fun run() {
        val current = database.effectiveProfileSwitchDao.findById(effectiveProfileSwitch.id)
        if (current != null && current.interfaceIDs.nightscoutId != effectiveProfileSwitch.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = effectiveProfileSwitch.interfaceIDs.nightscoutId
            database.effectiveProfileSwitchDao.updateExistingEntry(current)
        }
    }
}