package app.aaps.core.interfaces.nsclient

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.DS
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.OE
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT

interface StoreDataForDb {

    val glucoseValues: MutableList<GV>
    val boluses: MutableList<BS>
    val carbs: MutableList<CA>
    val temporaryTargets: MutableList<TT>
    val effectiveProfileSwitches: MutableList<EPS>
    val bolusCalculatorResults: MutableList<BCR>
    val therapyEvents: MutableList<TE>
    val extendedBoluses: MutableList<EB>
    val temporaryBasals: MutableList<TB>
    val profileSwitches: MutableList<PS>
    val offlineEvents: MutableList<OE>
    val foods: MutableList<FD>

    val nsIdGlucoseValues: MutableList<GV>
    val nsIdBoluses: MutableList<BS>
    val nsIdCarbs: MutableList<CA>
    val nsIdTemporaryTargets: MutableList<TT>
    val nsIdEffectiveProfileSwitches: MutableList<EPS>
    val nsIdBolusCalculatorResults: MutableList<BCR>
    val nsIdTherapyEvents: MutableList<TE>
    val nsIdExtendedBoluses: MutableList<EB>
    val nsIdTemporaryBasals: MutableList<TB>
    val nsIdProfileSwitches: MutableList<PS>
    val nsIdOfflineEvents: MutableList<OE>
    val nsIdDeviceStatuses: MutableList<DS>
    val nsIdFoods: MutableList<FD>

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