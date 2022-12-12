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

    interface DataPair
    data class PairTemporaryTarget(val value: TemporaryTarget, val updateRecordId: Long): DataPair
    data class PairGlucoseValue(val value: GlucoseValue, val updateRecordId: Long): DataPair
    data class PairTherapyEvent(val value: TherapyEvent, val updateRecordId: Long): DataPair
    data class PairFood(val value: Food, val updateRecordId: Long): DataPair
    data class PairBolus(val value: Bolus, val updateRecordId: Long): DataPair
    data class PairCarbs(val value: Carbs, val updateRecordId: Long): DataPair
    data class PairBolusCalculatorResult(val value: BolusCalculatorResult, val updateRecordId: Long): DataPair
    data class PairTemporaryBasal(val value: TemporaryBasal, val updateRecordId: Long): DataPair
    data class PairExtendedBolus(val value: ExtendedBolus, val updateRecordId: Long): DataPair
    data class PairProfileSwitch(val value: ProfileSwitch, val updateRecordId: Long): DataPair
    data class PairEffectiveProfileSwitch(val value: EffectiveProfileSwitch, val updateRecordId: Long): DataPair
    data class PairOfflineEvent(val value: OfflineEvent, val updateRecordId: Long): DataPair
    data class PairProfileStore(val value: JSONObject, val timestampSync: Long): DataPair
    data class PairDeviceStatus(val value: DeviceStatus, val unused: Long?): DataPair

    fun queueSize(): Long

    fun doUpload()

    fun resetToNextFullSync()

    fun confirmLastBolusIdIfGreater(lastSynced: Long)
    fun processChangedBolusesCompat()

    fun confirmLastCarbsIdIfGreater(lastSynced: Long)
    fun processChangedCarbsCompat()

    fun confirmLastBolusCalculatorResultsIdIfGreater(lastSynced: Long)
    fun processChangedBolusCalculatorResultsCompat()

    fun confirmLastTempTargetsIdIfGreater(lastSynced: Long)
    fun processChangedTempTargetsCompat()

    fun confirmLastGlucoseValueIdIfGreater(lastSynced: Long)
    fun processChangedGlucoseValuesCompat()

    fun confirmLastTherapyEventIdIfGreater(lastSynced: Long)
    fun processChangedTherapyEventsCompat()

    fun confirmLastFoodIdIfGreater(lastSynced: Long)
    fun processChangedFoodsCompat()

    fun confirmLastDeviceStatusIdIfGreater(lastSynced: Long)
    fun processChangedDeviceStatusesCompat()

    fun confirmLastTemporaryBasalIdIfGreater(lastSynced: Long)
    fun processChangedTemporaryBasalsCompat()

    fun confirmLastExtendedBolusIdIfGreater(lastSynced: Long)
    fun processChangedExtendedBolusesCompat()

    fun confirmLastProfileSwitchIdIfGreater(lastSynced: Long)
    fun processChangedProfileSwitchesCompat()

    fun confirmLastEffectiveProfileSwitchIdIfGreater(lastSynced: Long)
    fun processChangedEffectiveProfileSwitchesCompat()

    fun confirmLastOfflineEventIdIfGreater(lastSynced: Long)
    fun processChangedOfflineEventsCompat()

    fun confirmLastProfileStore(lastSynced: Long)
    fun processChangedProfileStore()
}