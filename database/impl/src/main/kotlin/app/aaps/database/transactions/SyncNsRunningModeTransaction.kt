package app.aaps.database.transactions

import app.aaps.database.entities.RunningMode

/**
 * Sync the RunningMode from NS
 */
class SyncNsRunningModeTransaction(private val runningModes: List<RunningMode>) : Transaction<SyncNsRunningModeTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
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
                // Allow update duration to shorter only.
                // duration == 0 means "permanent/indefinite" for RM, so it must compare as
                // infinite — otherwise a permanent→finite cut (incoming > 0, current == 0)
                // would be wrongly rejected as "longer". Incoming 0 (lengthening to permanent)
                // stays rejected.
                // Guard: autoForced rows (SUSPENDED_BY_PUMP, constraint-forced modes) are
                // locally determined by each device from its own pump/constraints state.
                // Remote clients (including older versions that rewrite durations based on
                // their non-authoritative local pump.isSuspended()) must not be allowed to
                // shorten them here — otherwise the chip on the authoritative device
                // flips to the truncated value on every NS round-trip.
                val isCut = runningMode.duration > 0 &&
                    (current.duration == 0L || runningMode.duration < current.duration)
                if (!current.autoForced &&
                    current.duration != runningMode.duration &&
                    isCut
                ) {
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
