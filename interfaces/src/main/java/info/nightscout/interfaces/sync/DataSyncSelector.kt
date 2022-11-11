package info.nightscout.interfaces.sync

import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.BolusCalculatorResult
import info.nightscout.database.entities.Carbs
import info.nightscout.database.entities.DeviceStatus
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.ExtendedBolus
import info.nightscout.database.entities.Food
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.OfflineEvent
import info.nightscout.database.entities.ProfileSwitch
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.TherapyEvent
import org.json.JSONObject

interface DataSyncSelector {

    data class PairTemporaryTarget(val value: TemporaryTarget, val updateRecordId: Long)
    data class PairGlucoseValue(val value: GlucoseValue, val updateRecordId: Long)
    data class PairTherapyEvent(val value: TherapyEvent, val updateRecordId: Long)
    data class PairFood(val value: Food, val updateRecordId: Long)
    data class PairBolus(val value: Bolus, val updateRecordId: Long)
    data class PairCarbs(val value: Carbs, val updateRecordId: Long)
    data class PairBolusCalculatorResult(val value: BolusCalculatorResult, val updateRecordId: Long)
    data class PairTemporaryBasal(val value: TemporaryBasal, val updateRecordId: Long)
    data class PairExtendedBolus(val value: ExtendedBolus, val updateRecordId: Long)
    data class PairProfileSwitch(val value: ProfileSwitch, val updateRecordId: Long)
    data class PairEffectiveProfileSwitch(val value: EffectiveProfileSwitch, val updateRecordId: Long)
    data class PairOfflineEvent(val value: OfflineEvent, val updateRecordId: Long)
    data class PairProfileStore(val value: JSONObject, val timestampSync: Long)

    fun queueSize(): Long

    fun doUpload()

    fun resetToNextFullSync()

    fun confirmLastBolusIdIfGreater(lastSynced: Long)
    fun changedBoluses(): List<Bolus>

    // Until NS v3
    fun processChangedBolusesCompat()

    fun confirmLastCarbsIdIfGreater(lastSynced: Long)
    fun changedCarbs(): List<Carbs>

    // Until NS v3
    fun processChangedCarbsCompat()

    fun confirmLastBolusCalculatorResultsIdIfGreater(lastSynced: Long)
    fun changedBolusCalculatorResults(): List<BolusCalculatorResult>

    // Until NS v3
    fun processChangedBolusCalculatorResultsCompat()

    fun confirmLastTempTargetsIdIfGreater(lastSynced: Long)
    fun changedTempTargets(): List<TemporaryTarget>

    // Until NS v3
    fun processChangedTempTargetsCompat()

    fun confirmLastGlucoseValueIdIfGreater(lastSynced: Long)
    fun changedGlucoseValues(): List<GlucoseValue>

    // Until NS v3
    fun processChangedGlucoseValuesCompat()

    fun confirmLastTherapyEventIdIfGreater(lastSynced: Long)
    fun changedTherapyEvents(): List<TherapyEvent>

    // Until NS v3
    fun processChangedTherapyEventsCompat()

    fun confirmLastFoodIdIfGreater(lastSynced: Long)
    fun changedFoods(): List<Food>

    // Until NS v3
    fun processChangedFoodsCompat()

    fun confirmLastDeviceStatusIdIfGreater(lastSynced: Long)
    fun changedDeviceStatuses(): List<DeviceStatus>

    // Until NS v3
    fun processChangedDeviceStatusesCompat()

    fun confirmLastTemporaryBasalIdIfGreater(lastSynced: Long)
    fun changedTemporaryBasals(): List<TemporaryBasal>

    // Until NS v3
    fun processChangedTemporaryBasalsCompat()

    fun confirmLastExtendedBolusIdIfGreater(lastSynced: Long)
    fun changedExtendedBoluses(): List<ExtendedBolus>

    // Until NS v3
    fun processChangedExtendedBolusesCompat()

    fun confirmLastProfileSwitchIdIfGreater(lastSynced: Long)
    fun changedProfileSwitch(): List<ProfileSwitch>

    // Until NS v3
    fun processChangedProfileSwitchesCompat()

    fun confirmLastEffectiveProfileSwitchIdIfGreater(lastSynced: Long)
    fun changedEffectiveProfileSwitch(): List<EffectiveProfileSwitch>

    // Until NS v3
    fun processChangedEffectiveProfileSwitchesCompat()

    fun confirmLastOfflineEventIdIfGreater(lastSynced: Long)
    fun changedOfflineEvents(): List<OfflineEvent>

    // Until NS v3
    fun processChangedOfflineEventsCompat()

    fun confirmLastProfileStore(lastSynced: Long)
    fun processChangedProfileStore()
}