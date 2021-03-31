package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.database.entities.DeviceStatus
import info.nightscout.androidaps.database.entities.*

interface DataSyncSelector {

    data class PairTemporaryTarget(val value: TemporaryTarget, val updateRecordId: Long)
    data class PairGlucoseValue(val value: GlucoseValue, val updateRecordId: Long)
    data class PairTherapyEvent(val value: TherapyEvent, val updateRecordId: Long)
    data class PairFood(val value: Food, val updateRecordId: Long)
    data class PairBolus(val value: Bolus, val updateRecordId: Long)
    data class PairCarbs(val value: Carbs, val updateRecordId: Long)
    data class PairBolusCalculatorResult(val value: BolusCalculatorResult, val updateRecordId: Long)

    fun resetToNextFullSync()

    fun confirmLastBolusIdIfGreater(lastSynced: Long)
    fun changedBoluses() : List<Bolus>
    // Until NS v3
    fun processChangedBolusesCompat(): Boolean

    fun confirmLastCarbsIdIfGreater(lastSynced: Long)
    fun changedCarbs() : List<Carbs>
    // Until NS v3
    fun processChangedCarbsCompat(): Boolean

    fun confirmLastBolusCalculatorResultsIdIfGreater(lastSynced: Long)
    fun changedBolusCalculatorResults() : List<BolusCalculatorResult>
    // Until NS v3
    fun processChangedBolusCalculatorResultsCompat(): Boolean

    fun confirmLastTempTargetsIdIfGreater(lastSynced: Long)
    fun changedTempTargets() : List<TemporaryTarget>
    // Until NS v3
    fun processChangedTempTargetsCompat(): Boolean

    fun confirmLastGlucoseValueIdIfGreater(lastSynced: Long)
    fun changedGlucoseValues() : List<GlucoseValue>
    // Until NS v3
    fun processChangedGlucoseValuesCompat(): Boolean

    fun confirmLastTherapyEventIdIfGreater(lastSynced: Long)
    fun changedTherapyEvents() : List<TherapyEvent>
    // Until NS v3
    fun processChangedTherapyEventsCompat(): Boolean

    fun confirmLastFoodIdIfGreater(lastSynced: Long)
    fun changedFoods() : List<Food>
    // Until NS v3
    fun processChangedFoodsCompat(): Boolean

    fun confirmLastDeviceStatusIdIfGreater(lastSynced: Long)
    fun changedDeviceStatuses() : List<DeviceStatus>
    // Until NS v3
    fun processChangedDeviceStatusesCompat(): Boolean
}