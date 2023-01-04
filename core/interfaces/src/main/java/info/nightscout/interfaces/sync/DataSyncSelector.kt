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

    interface DataPair { 
        val value: Any
        val id: Long
    }
    data class PairTemporaryTarget(override val value: TemporaryTarget, override val id: Long): DataPair
    data class PairGlucoseValue(override val value: GlucoseValue, override val id: Long): DataPair
    data class PairTherapyEvent(override val value: TherapyEvent, override val id: Long): DataPair
    data class PairFood(override val value: Food, override val id: Long): DataPair
    data class PairBolus(override val value: Bolus, override val id: Long): DataPair
    data class PairCarbs(override val value: Carbs, override val id: Long): DataPair
    data class PairBolusCalculatorResult(override val value: BolusCalculatorResult, override val id: Long): DataPair
    data class PairTemporaryBasal(override val value: TemporaryBasal, override val id: Long): DataPair
    data class PairExtendedBolus(override val value: ExtendedBolus, override val id: Long): DataPair
    data class PairProfileSwitch(override val value: ProfileSwitch, override val id: Long): DataPair
    data class PairEffectiveProfileSwitch(override val value: EffectiveProfileSwitch, override val id: Long): DataPair
    data class PairOfflineEvent(override val value: OfflineEvent, override val id: Long): DataPair
    data class PairProfileStore(override val value: JSONObject, override val id: Long): DataPair
    data class PairDeviceStatus(override val value: DeviceStatus, override val id: Long): DataPair

    fun queueSize(): Long

    fun doUpload()

    fun resetToNextFullSync()

    fun confirmLastBolusIdIfGreater(lastSynced: Long)
    fun processChangedBoluses()

    fun confirmLastCarbsIdIfGreater(lastSynced: Long)
    fun processChangedCarbs()

    fun confirmLastBolusCalculatorResultsIdIfGreater(lastSynced: Long)
    fun processChangedBolusCalculatorResults()

    fun confirmLastTempTargetsIdIfGreater(lastSynced: Long)
    fun processChangedTempTargets()

    fun confirmLastGlucoseValueIdIfGreater(lastSynced: Long)
    fun processChangedGlucoseValues()

    fun confirmLastTherapyEventIdIfGreater(lastSynced: Long)
    fun processChangedTherapyEvents()

    fun confirmLastFoodIdIfGreater(lastSynced: Long)
    fun processChangedFoods()

    fun confirmLastDeviceStatusIdIfGreater(lastSynced: Long)
    fun processChangedDeviceStatuses()

    fun confirmLastTemporaryBasalIdIfGreater(lastSynced: Long)
    fun processChangedTemporaryBasals()

    fun confirmLastExtendedBolusIdIfGreater(lastSynced: Long)
    fun processChangedExtendedBoluses()

    fun confirmLastProfileSwitchIdIfGreater(lastSynced: Long)
    fun processChangedProfileSwitches()

    fun confirmLastEffectiveProfileSwitchIdIfGreater(lastSynced: Long)
    fun processChangedEffectiveProfileSwitches()

    fun confirmLastOfflineEventIdIfGreater(lastSynced: Long)
    fun processChangedOfflineEvents()

    fun confirmLastProfileStore(lastSynced: Long)
    fun processChangedProfileStore()
}