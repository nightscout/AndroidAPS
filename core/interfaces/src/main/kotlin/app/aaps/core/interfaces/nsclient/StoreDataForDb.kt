package app.aaps.core.interfaces.nsclient

import app.aaps.core.data.db.BCR
import app.aaps.core.data.db.BS
import app.aaps.core.data.db.CA
import app.aaps.core.data.db.DS
import app.aaps.core.data.db.EB
import app.aaps.core.data.db.EPS
import app.aaps.core.data.db.FD
import app.aaps.core.data.db.GV
import app.aaps.core.data.db.OE
import app.aaps.core.data.db.PS
import app.aaps.core.data.db.TB
import app.aaps.core.data.db.TE
import app.aaps.core.data.db.TT

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