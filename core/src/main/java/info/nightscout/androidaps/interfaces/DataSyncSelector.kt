package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.db.DbRequest

interface DataSyncSelector {

    fun resetToNextFullSync()

    fun confirmTempTargetsTimestamp(lastSynced: Long)
    fun changedTempTargets() : List<TemporaryTarget>

    // Until NS v3
    fun changedTempTargetsCompat() : List<DbRequest>
}