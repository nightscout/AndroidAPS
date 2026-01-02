package app.aaps.plugins.sync.nsclient.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.utils.JsonHelper
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.plugins.sync.nsclient.extensions.extendedBolusFromJson
import app.aaps.plugins.sync.nsclient.extensions.fromJson
import app.aaps.plugins.sync.nsclient.extensions.isEffectiveProfileSwitch
import app.aaps.plugins.sync.nsclient.extensions.temporaryBasalFromJson
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class NSClientAddUpdateWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var config: Config
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var storeDataForDb: StoreDataForDb
    @Inject lateinit var profileUtil: ProfileUtil

    override suspend fun doWorkAndLog(): Result {
        val treatments = dataWorkerStorage.pickupJSONArray(inputData.getLong(DataWorkerStorage.STORE_KEY, -1))
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        val ret = Result.success()
        var latestDateInReceivedData = 0L

        for (i in 0 until treatments.length()) {
            var json = treatments.getJSONObject(i)
            aapsLogger.debug(LTag.DATABASE, "Received NS treatment: $json")

            val insulin = JsonHelper.safeGetDouble(json, "insulin")
            val carbs = JsonHelper.safeGetDouble(json, "carbs")
            var eventType = JsonHelper.safeGetString(json, "eventType")
            if (eventType == null) {
                aapsLogger.debug(LTag.NSCLIENT, "Wrong treatment. Ignoring : $json")
                continue
            }

            //Find latest date in treatment
            val mills = JsonHelper.safeGetLong(json, "mills")
            if (mills != 0L && mills < dateUtil.now() && mills > latestDateInReceivedData)
                latestDateInReceivedData = mills

            if (insulin > 0 && (preferences.get(BooleanKey.NsClientAcceptInsulin) || config.AAPSCLIENT)) {
                BS.fromJson(json)?.let { bolus ->
                    storeDataForDb.addToBoluses(bolus)
                } ?: aapsLogger.error("Error parsing bolus json $json")
            }
            if (carbs != 0.0 && (preferences.get(BooleanKey.NsClientAcceptCarbs) || config.AAPSCLIENT)) {
                CA.fromJson(json)?.let { carb ->
                    storeDataForDb.addToCarbs(carb)
                } ?: aapsLogger.error("Error parsing bolus json $json")
            }
            // Convert back emulated TBR -> EB
            if (eventType == TE.Type.TEMPORARY_BASAL.text && json.has("extendedEmulated")) {
                val ebJson = json.getJSONObject("extendedEmulated")
                ebJson.put("_id", json.getString("_id"))
                ebJson.put("isValid", json.getBoolean("isValid"))
                ebJson.put("mills", mills)
                json = ebJson
                eventType = JsonHelper.safeGetString(json, "eventType")

                activePlugin.activePump.let { if (it is VirtualPump) it.fakeDataDetected = true }
            }
            when {
                insulin > 0 || carbs > 0                                          -> Any()
                eventType == TE.Type.TEMPORARY_TARGET.text                        ->
                    if (preferences.get(BooleanKey.NsClientAcceptTempTarget) || config.AAPSCLIENT) {
                        TT.fromJson(json, profileUtil)?.let { temporaryTarget ->
                            storeDataForDb.addToTemporaryTargets(temporaryTarget)
                        } ?: aapsLogger.error("Error parsing TT json $json")
                    }

                eventType == TE.Type.NOTE.text && json.isEffectiveProfileSwitch() -> // replace this by new Type when available in NS
                    if (preferences.get(BooleanKey.NsClientAcceptProfileSwitch) || config.AAPSCLIENT) {
                        EPS.fromJson(json, dateUtil)?.let { effectiveProfileSwitch ->
                            storeDataForDb.addToEffectiveProfileSwitches(effectiveProfileSwitch)
                        } ?: aapsLogger.error("Error parsing EffectiveProfileSwitch json $json")
                    }

                eventType == TE.Type.BOLUS_WIZARD.text                            ->
                    BCR.fromJson(json)?.let { bolusCalculatorResult ->
                        storeDataForDb.addToBolusCalculatorResults(bolusCalculatorResult)
                    } ?: aapsLogger.error("Error parsing BolusCalculatorResult json $json")

                eventType == TE.Type.CANNULA_CHANGE.text ||
                    eventType == TE.Type.INSULIN_CHANGE.text ||
                    eventType == TE.Type.SENSOR_CHANGE.text ||
                    eventType == TE.Type.FINGER_STICK_BG_VALUE.text ||
                    eventType == TE.Type.NONE.text ||
                    eventType == TE.Type.ANNOUNCEMENT.text ||
                    eventType == TE.Type.QUESTION.text ||
                    eventType == TE.Type.EXERCISE.text ||
                    eventType == TE.Type.NOTE.text ||
                    eventType == TE.Type.PUMP_BATTERY_CHANGE.text                 ->
                    if (preferences.get(BooleanKey.NsClientAcceptTherapyEvent) || config.AAPSCLIENT) {
                        TE.fromJson(json)?.let { therapyEvent ->
                            storeDataForDb.addToTherapyEvents(therapyEvent)
                        } ?: aapsLogger.error("Error parsing TherapyEvent json $json")
                    }

                eventType == TE.Type.COMBO_BOLUS.text                             ->
                    if (preferences.get(BooleanKey.NsClientAcceptTbrEb) || config.AAPSCLIENT) {
                        EB.extendedBolusFromJson(json)?.let { extendedBolus ->
                            storeDataForDb.addToExtendedBoluses(extendedBolus)
                        } ?: aapsLogger.error("Error parsing ExtendedBolus json $json")
                    }

                eventType == TE.Type.TEMPORARY_BASAL.text                         ->
                    if (preferences.get(BooleanKey.NsClientAcceptTbrEb) || config.AAPSCLIENT) {
                        TB.temporaryBasalFromJson(json)?.let { temporaryBasal ->
                            storeDataForDb.addToTemporaryBasals(temporaryBasal)
                        } ?: aapsLogger.error("Error parsing TemporaryBasal json $json")
                    }

                eventType == TE.Type.PROFILE_SWITCH.text                          ->
                    if (preferences.get(BooleanKey.NsClientAcceptProfileSwitch) || config.AAPSCLIENT) {
                        PS.fromJson(json, dateUtil, activePlugin)?.let { profileSwitch ->
                            storeDataForDb.addToProfileSwitches(profileSwitch)
                        } ?: aapsLogger.error("Error parsing ProfileSwitch json $json")
                    }

                eventType == TE.Type.APS_OFFLINE.text                             ->
                    if (preferences.get(BooleanKey.NsClientAcceptRunningMode) && config.isEngineeringMode() || config.AAPSCLIENT) {
                        RM.fromJson(json)?.let { runningMode ->
                            storeDataForDb.addToRunningModes(runningMode)
                        } ?: aapsLogger.error("Error parsing RunningMode json $json")
                    }
            }
        }
        storeDataForDb.storeTreatmentsToDb(fullSync = false)
        activePlugin.activeNsClient?.updateLatestTreatmentReceivedIfNewer(latestDateInReceivedData)
        return ret
    }
}