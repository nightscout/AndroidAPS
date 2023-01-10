package info.nightscout.plugins.sync.nsclient.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.XDripBroadcast
import info.nightscout.interfaces.nsclient.StoreDataForDb
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.VirtualPump
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.nsclient.extensions.bolusCalculatorResultFromJson
import info.nightscout.plugins.sync.nsclient.extensions.bolusFromJson
import info.nightscout.plugins.sync.nsclient.extensions.carbsFromJson
import info.nightscout.plugins.sync.nsclient.extensions.effectiveProfileSwitchFromJson
import info.nightscout.plugins.sync.nsclient.extensions.extendedBolusFromJson
import info.nightscout.plugins.sync.nsclient.extensions.isEffectiveProfileSwitch
import info.nightscout.plugins.sync.nsclient.extensions.offlineEventFromJson
import info.nightscout.plugins.sync.nsclient.extensions.profileSwitchFromJson
import info.nightscout.plugins.sync.nsclient.extensions.temporaryBasalFromJson
import info.nightscout.plugins.sync.nsclient.extensions.temporaryTargetFromJson
import info.nightscout.plugins.sync.nsclient.extensions.therapyEventFromJson
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class NSClientAddUpdateWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var config: Config
    @Inject lateinit var sp: SP
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var xDripBroadcast: XDripBroadcast
    @Inject lateinit var storeDataForDb: StoreDataForDb

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
            if (mills != 0L && mills < dateUtil.now())
                if (mills > latestDateInReceivedData) latestDateInReceivedData = mills

            if (insulin > 0) {
                if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_insulin, false) || config.NSCLIENT) {
                    bolusFromJson(json)?.let { bolus ->
                        storeDataForDb.boluses.add(bolus)
                    } ?: aapsLogger.error("Error parsing bolus json $json")
                }
            }
            if (carbs > 0) {
                if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_carbs, false) || config.NSCLIENT) {
                    carbsFromJson(json)?.let { carb ->
                        storeDataForDb.carbs.add(carb)
                    } ?: aapsLogger.error("Error parsing bolus json $json")
                }
            }
            // Convert back emulated TBR -> EB
            if (eventType == TherapyEvent.Type.TEMPORARY_BASAL.text && json.has("extendedEmulated")) {
                val ebJson = json.getJSONObject("extendedEmulated")
                ebJson.put("_id", json.getString("_id"))
                ebJson.put("isValid", json.getBoolean("isValid"))
                ebJson.put("mills", mills)
                json = ebJson
                eventType = JsonHelper.safeGetString(json, "eventType")

                activePlugin.activePump.let { if (it is VirtualPump) it.fakeDataDetected = true }
            }
            when {
                insulin > 0 || carbs > 0                                                    -> Any()
                eventType == TherapyEvent.Type.TEMPORARY_TARGET.text                        ->
                    if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_temp_target, false) || config.NSCLIENT) {
                        temporaryTargetFromJson(json)?.let { temporaryTarget ->
                            storeDataForDb.temporaryTargets.add(temporaryTarget)
                        } ?: aapsLogger.error("Error parsing TT json $json")
                    }

                eventType == TherapyEvent.Type.NOTE.text && json.isEffectiveProfileSwitch() -> // replace this by new Type when available in NS
                    if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_profile_switch, false) || config.NSCLIENT) {
                        effectiveProfileSwitchFromJson(json, dateUtil)?.let { effectiveProfileSwitch ->
                            storeDataForDb.effectiveProfileSwitches.add(effectiveProfileSwitch)
                        } ?: aapsLogger.error("Error parsing EffectiveProfileSwitch json $json")
                    }

                eventType == TherapyEvent.Type.BOLUS_WIZARD.text                            ->
                    bolusCalculatorResultFromJson(json)?.let { bolusCalculatorResult ->
                        storeDataForDb.bolusCalculatorResults.add(bolusCalculatorResult)
                    } ?: aapsLogger.error("Error parsing BolusCalculatorResult json $json")

                eventType == TherapyEvent.Type.CANNULA_CHANGE.text ||
                    eventType == TherapyEvent.Type.INSULIN_CHANGE.text ||
                    eventType == TherapyEvent.Type.SENSOR_CHANGE.text ||
                    eventType == TherapyEvent.Type.FINGER_STICK_BG_VALUE.text ||
                    eventType == TherapyEvent.Type.NONE.text ||
                    eventType == TherapyEvent.Type.ANNOUNCEMENT.text ||
                    eventType == TherapyEvent.Type.QUESTION.text ||
                    eventType == TherapyEvent.Type.EXERCISE.text ||
                    eventType == TherapyEvent.Type.NOTE.text ||
                    eventType == TherapyEvent.Type.PUMP_BATTERY_CHANGE.text                 ->
                    if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_therapy_events, false) || config.NSCLIENT) {
                        therapyEventFromJson(json)?.let { therapyEvent ->
                            storeDataForDb.therapyEvents.add(therapyEvent)
                        } ?: aapsLogger.error("Error parsing TherapyEvent json $json")
                    }

                eventType == TherapyEvent.Type.COMBO_BOLUS.text                             ->
                    if (config.isEngineeringMode() && sp.getBoolean(R.string.key_ns_receive_tbr_eb, false) || config.NSCLIENT) {
                        extendedBolusFromJson(json)?.let { extendedBolus ->
                            storeDataForDb.extendedBoluses.add(extendedBolus)
                        } ?: aapsLogger.error("Error parsing ExtendedBolus json $json")
                    }

                eventType == TherapyEvent.Type.TEMPORARY_BASAL.text                         ->
                    if (config.isEngineeringMode() && sp.getBoolean(R.string.key_ns_receive_tbr_eb, false) || config.NSCLIENT) {
                        temporaryBasalFromJson(json)?.let { temporaryBasal ->
                            storeDataForDb.temporaryBasals.add(temporaryBasal)
                        } ?: aapsLogger.error("Error parsing TemporaryBasal json $json")
                    }

                eventType == TherapyEvent.Type.PROFILE_SWITCH.text                          ->
                    if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_profile_switch, false) || config.NSCLIENT) {
                        profileSwitchFromJson(json, dateUtil, activePlugin)?.let { profileSwitch ->
                            storeDataForDb.profileSwitches.add(profileSwitch)
                        } ?: aapsLogger.error("Error parsing ProfileSwitch json $json")
                    }

                eventType == TherapyEvent.Type.APS_OFFLINE.text                             ->
                    if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_offline_event, false) && config.isEngineeringMode() || config.NSCLIENT) {
                        offlineEventFromJson(json)?.let { offlineEvent ->
                            storeDataForDb.offlineEvents.add(offlineEvent)
                        } ?: aapsLogger.error("Error parsing OfflineEvent json $json")
                    }
            }
        }
        storeDataForDb.storeTreatmentsToDb()
        activePlugin.activeNsClient?.updateLatestTreatmentReceivedIfNewer(latestDateInReceivedData)
        xDripBroadcast.sendTreatments(treatments)
        return ret
    }
}