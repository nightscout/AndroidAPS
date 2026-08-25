package app.aaps.database.transactions

import app.aaps.database.entities.EffectiveProfileSwitch

/**
 * Sync the EffectiveProfileSwitch from NS
 */
class SyncNsEffectiveProfileSwitchTransaction(private val effectiveProfileSwitches: List<EffectiveProfileSwitch>) : Transaction<SyncNsEffectiveProfileSwitchTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()

        for (effectiveProfileSwitch in effectiveProfileSwitches) {
            val current: EffectiveProfileSwitch? =
                effectiveProfileSwitch.interfaceIDs.nightscoutId?.let {
                    database.effectiveProfileSwitchDao.findByNSId(it)
                }

            if (current != null) {
                // nsId exists, allow only invalidation
                if (current.isValid && !effectiveProfileSwitch.isValid) {
                    current.isValid = false
                    database.effectiveProfileSwitchDao.updateExistingEntry(current)
                    result.invalidated.add(current)
                }
                continue
            }

            // not known nsId
            val existing = database.effectiveProfileSwitchDao.findByTimestamp(effectiveProfileSwitch.timestamp)
            if (existing != null && existing.interfaceIDs.nightscoutId == null) {
                // the same record, update nsId only
                existing.interfaceIDs.nightscoutId = effectiveProfileSwitch.interfaceIDs.nightscoutId
                existing.isValid = effectiveProfileSwitch.isValid
                database.effectiveProfileSwitchDao.updateExistingEntry(existing)
                result.updatedNsId.add(existing)
            } else {
                database.effectiveProfileSwitchDao.insertNewEntry(effectiveProfileSwitch)
                result.inserted.add(effectiveProfileSwitch)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<EffectiveProfileSwitch>()
        val inserted = mutableListOf<EffectiveProfileSwitch>()
        val invalidated = mutableListOf<EffectiveProfileSwitch>()
    }
}