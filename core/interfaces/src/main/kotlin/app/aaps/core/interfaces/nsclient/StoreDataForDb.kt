package app.aaps.core.interfaces.nsclient

import app.aaps.core.data.db.GV
import app.aaps.core.data.db.OE
import app.aaps.core.data.db.TE
import app.aaps.core.data.db.TT
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.DeviceStatus
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.Food
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.TemporaryBasal

interface StoreDataForDb {

    val glucoseValues: MutableList<GV>
    val boluses: MutableList<Bolus>
    val carbs: MutableList<Carbs>
    val temporaryTargets: MutableList<TT>
    val effectiveProfileSwitches: MutableList<EffectiveProfileSwitch>
    val bolusCalculatorResults: MutableList<BolusCalculatorResult>
    val therapyEvents: MutableList<TE>
    val extendedBoluses: MutableList<ExtendedBolus>
    val temporaryBasals: MutableList<TemporaryBasal>
    val profileSwitches: MutableList<ProfileSwitch>
    val offlineEvents: MutableList<OE>
    val foods: MutableList<Food>

    val nsIdGlucoseValues: MutableList<GV>
    val nsIdBoluses: MutableList<Bolus>
    val nsIdCarbs: MutableList<Carbs>
    val nsIdTemporaryTargets: MutableList<TT>
    val nsIdEffectiveProfileSwitches: MutableList<EffectiveProfileSwitch>
    val nsIdBolusCalculatorResults: MutableList<BolusCalculatorResult>
    val nsIdTherapyEvents: MutableList<TE>
    val nsIdExtendedBoluses: MutableList<ExtendedBolus>
    val nsIdTemporaryBasals: MutableList<TemporaryBasal>
    val nsIdProfileSwitches: MutableList<ProfileSwitch>
    val nsIdOfflineEvents: MutableList<OE>
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