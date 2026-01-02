package app.aaps.core.interfaces.nsclient

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.DS
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT

interface StoreDataForDb {

    fun addToGlucoseValues(payload: MutableList<GV>): Boolean
    fun addToBoluses(payload: BS): Boolean
    fun addToCarbs(payload: CA): Boolean
    fun addToTemporaryTargets(payload: TT): Boolean
    fun addToEffectiveProfileSwitches(payload: EPS): Boolean
    fun addToBolusCalculatorResults(payload: BCR): Boolean
    fun addToTherapyEvents(payload: TE): Boolean
    fun addToExtendedBoluses(payload: EB): Boolean
    fun addToTemporaryBasals(payload: TB): Boolean
    fun addToProfileSwitches(payload: PS): Boolean
    fun addToRunningModes(payload: RM): Boolean
    fun addToFoods(payload: MutableList<FD>): Boolean

    fun addToNsIdGlucoseValues(payload: GV): Boolean
    fun addToNsIdBoluses(payload: BS): Boolean
    fun addToNsIdCarbs(payload: CA): Boolean
    fun addToNsIdTemporaryTargets(payload: TT): Boolean
    fun addToNsIdEffectiveProfileSwitches(payload: EPS): Boolean
    fun addToNsIdBolusCalculatorResults(payload: BCR): Boolean
    fun addToNsIdTherapyEvents(payload: TE): Boolean
    fun addToNsIdExtendedBoluses(payload: EB): Boolean
    fun addToNsIdTemporaryBasals(payload: TB): Boolean
    fun addToNsIdProfileSwitches(payload: PS): Boolean
    fun addToNsIdRunningModes(payload: RM): Boolean
    fun addToNsIdDeviceStatuses(payload: DS): Boolean
    fun addToNsIdFoods(payload: FD): Boolean

    fun addToDeleteTreatment(payload: String): Boolean
    fun addToDeleteGlucoseValue(payload: String): Boolean

    fun updateDeletedGlucoseValuesInDb()
    fun storeTreatmentsToDb(fullSync: Boolean)
    fun updateDeletedTreatmentsInDb()
    fun storeGlucoseValuesToDb()
    fun storeFoodsToDb()
    fun scheduleNsIdUpdate()
    fun updateNsIds()
}