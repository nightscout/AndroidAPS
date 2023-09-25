package app.aaps.core.interfaces.nsclient

import app.aaps.database.entities.Bolus
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.DeviceStatus
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.Food
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.OfflineEvent
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.transactions.TransactionGlucoseValue

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

    val deleteTreatment: MutableList<String>
    val deleteGlucoseValue: MutableList<String>

    fun updateDeletedGlucoseValuesInDb()
    fun storeTreatmentsToDb()
    fun updateDeletedTreatmentsInDb()
    fun storeGlucoseValuesToDb()
    fun storeFoodsToDb()
    fun scheduleNsIdUpdate()
    fun updateNsIds()
}