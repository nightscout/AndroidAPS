package info.nightscout.interfaces.nsclient

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
import info.nightscout.database.transactions.TransactionGlucoseValue

interface StoreDataForDb {
    val glucoseValues: MutableList<TransactionGlucoseValue>
    val boluses: MutableList<Bolus>
    val carbs: MutableList<Carbs>
    val temporaryTargets: MutableList<TemporaryTarget>
    val effectiveProfileSwitches: MutableList<EffectiveProfileSwitch>
    val bolusCalculatorResults: MutableList<BolusCalculatorResult>
    val therapyEvents: MutableList<TherapyEvent>
    val extendedBoluses: MutableList<ExtendedBolus>
    val temporaryBasals: MutableList<TemporaryBasal>
    val profileSwitches: MutableList<ProfileSwitch>
    val offlineEvents: MutableList<OfflineEvent>
    val foods: MutableList<Food>

    val nsIdGlucoseValues: MutableList<GlucoseValue>
    val nsIdBoluses: MutableList<Bolus>
    val nsIdCarbs: MutableList<Carbs>
    val nsIdTemporaryTargets: MutableList<TemporaryTarget>
    val nsIdEffectiveProfileSwitches: MutableList<EffectiveProfileSwitch>
    val nsIdBolusCalculatorResults: MutableList<BolusCalculatorResult>
    val nsIdTherapyEvents: MutableList<TherapyEvent>
    val nsIdExtendedBoluses: MutableList<ExtendedBolus>
    val nsIdTemporaryBasals: MutableList<TemporaryBasal>
    val nsIdProfileSwitches: MutableList<ProfileSwitch>
    val nsIdOfflineEvents: MutableList<OfflineEvent>
    val nsIdDeviceStatuses: MutableList<DeviceStatus>
    val nsIdFoods: MutableList<Food>

    fun storeTreatmentsToDb()
    fun storeGlucoseValuesToDb()
    fun storeFoodsToDb()
    fun scheduleNsIdUpdate()
    fun updateNsIds()
}