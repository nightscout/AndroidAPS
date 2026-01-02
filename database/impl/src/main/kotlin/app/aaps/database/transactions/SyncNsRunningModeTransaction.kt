package app.aaps.database.transactions

import app.aaps.database.entities.RunningMode

/**
 * Sync the RunningMode from NS
 */
class SyncNsRunningModeTransaction(private val runningModes: List<RunningMode>) : Transaction<SyncNsRunningModeTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        for (runningMode in runningModes) {
            val current: RunningMode? =
                runningMode.interfaceIDs.nightscoutId?.let {
                    database.runningModeDao.findByNSId(it)
                }

            if (current != null) {
                // nsId exists, allow only invalidation
                if (current.isValid && !runningMode.isValid) {
                    current.isValid = false
                    database.runningModeDao.updateExistingEntry(current)
                    result.invalidated.add(current)
                }
                // Allow update duration to shorter only
                if (current.duration != runningMode.duration && runningMode.duration < current.duration) {
                    current.duration = runningMode.duration
                    database.runningModeDao.updateExistingEntry(current)
                    result.updatedDuration.add(current)
                }
                continue
            }

            // not known nsId
            val existing = database.runningModeDao.findByTimestamp(runningMode.timestamp)
            if (existing != null && existing.interfaceIDs.nightscoutId == null) {
                // the same record, update nsId only
                existing.interfaceIDs.nightscoutId = runningMode.interfaceIDs.nightscoutId
                existing.isValid = runningMode.isValid
                database.runningModeDao.updateExistingEntry(existing)
                result.updatedNsId.add(existing)
            } else {
                database.runningModeDao.insertNewEntry(runningMode)
                result.inserted.add(runningMode)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<RunningMode>()
        val inserted = mutableListOf<RunningMode>()
        val invalidated = mutableListOf<RunningMode>()
        val updatedDuration = mutableListOf<RunningMode>()
    }
}