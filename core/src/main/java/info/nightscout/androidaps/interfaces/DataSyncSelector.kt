package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TemporaryTarget

interface DataSyncSelector {

    data class PairTemporaryTarget(val value: TemporaryTarget, val updateRecordId: Long)
    data class PairGlucoseValue(val value: GlucoseValue, val updateRecordId: Long)

    fun resetToNextFullSync()

    fun confirmTempTargetsTimestamp(lastSynced: Long)
    fun confirmTempTargetsTimestampIfGreater(lastSynced: Long)
    fun changedTempTargets() : List<TemporaryTarget>
    // Until NS v3
    fun processChangedTempTargetsCompat(): Boolean

    fun confirmLastGlucoseValueId(lastSynced: Long)
    fun confirmLastGlucoseValueIdIfGreater(lastSynced: Long)
    fun changedGlucoseValues() : List<GlucoseValue>
    // Until NS v3
    fun processChangedGlucoseValuesCompat(): Boolean
}