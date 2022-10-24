package info.nightscout.androidaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.BuildHelper
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.plugins.sync.nsShared.StoreDataForDb
import info.nightscout.androidaps.plugins.sync.nsclientV3.extensions.toBolus
import info.nightscout.androidaps.plugins.sync.nsclientV3.extensions.toBolusCalculatorResult
import info.nightscout.androidaps.plugins.sync.nsclientV3.extensions.toCarbs
import info.nightscout.androidaps.plugins.sync.nsclientV3.extensions.toEffectiveProfileSwitch
import info.nightscout.androidaps.plugins.sync.nsclientV3.extensions.toProfileSwitch
import info.nightscout.androidaps.plugins.sync.nsclientV3.extensions.toTemporaryBasal
import info.nightscout.androidaps.plugins.sync.nsclientV3.extensions.toTemporaryTarget
import info.nightscout.androidaps.plugins.sync.nsclientV3.extensions.toTherapyEvent
import info.nightscout.androidaps.receivers.DataWorkerStorage
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.sdk.localmodel.treatment.NSBolus
import info.nightscout.sdk.localmodel.treatment.NSBolusWizard
import info.nightscout.sdk.localmodel.treatment.NSCarbs
import info.nightscout.sdk.localmodel.treatment.NSEffectiveProfileSwitch
import info.nightscout.sdk.localmodel.treatment.NSProfileSwitch
import info.nightscout.sdk.localmodel.treatment.NSTemporaryBasal
import info.nightscout.sdk.localmodel.treatment.NSTemporaryTarget
import info.nightscout.sdk.localmodel.treatment.NSTherapyEvent
import info.nightscout.sdk.localmodel.treatment.NSTreatment
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject

class ProcessTreatmentsWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Inject lateinit var xDripBroadcast: XDripBroadcast
    @Inject lateinit var storeDataForDb: StoreDataForDb

    override fun doWork(): Result {
        @Suppress("UNCHECKED_CAST")
        val treatments = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as List<NSTreatment>?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        val ret = Result.success()
        var latestDateInReceivedData = 0L

        for (treatment in treatments) {
            aapsLogger.debug(LTag.DATABASE, "Received NS treatment: $treatment")

            //Find latest date in treatment
            val mills = treatment.date
            if (mills != 0L && mills < dateUtil.now())
                if (mills > latestDateInReceivedData) latestDateInReceivedData = mills

            when (treatment) {
                is NSBolus                  ->
                    if (sp.getBoolean(R.string.key_ns_receive_insulin, false) || config.NSCLIENT)
                        storeDataForDb.boluses.add(treatment.toBolus())

                is NSCarbs                  ->
                    if (sp.getBoolean(R.string.key_ns_receive_carbs, false) || config.NSCLIENT)
                        storeDataForDb.carbs.add(treatment.toCarbs())

                is NSTemporaryTarget        ->
                    if (sp.getBoolean(R.string.key_ns_receive_temp_target, false) || config.NSCLIENT) {
                        if (treatment.duration > 0L) {
                            // not ending event
                            if (treatment.targetBottomAsMgdl() < Constants.MIN_TT_MGDL
                                || treatment.targetBottomAsMgdl() > Constants.MAX_TT_MGDL
                                || treatment.targetTopAsMgdl() < Constants.MIN_TT_MGDL
                                || treatment.targetTopAsMgdl() > Constants.MAX_TT_MGDL
                                || treatment.targetBottomAsMgdl() > treatment.targetTopAsMgdl()
                            ) {
                                aapsLogger.debug(LTag.DATABASE, "Ignored TemporaryTarget $treatment")
                                continue
                            }
                        }
                        storeDataForDb.temporaryTargets.add(treatment.toTemporaryTarget())
                    }
                /*
                // Convert back emulated TBR -> EB
                if (eventType == TherapyEvent.Type.TEMPORARY_BASAL.text && json.has("extendedEmulated")) {
                    val ebJson = json.getJSONObject("extendedEmulated")
                    ebJson.put("_id", json.getString("_id"))
                    ebJson.put("isValid", json.getBoolean("isValid"))
                    ebJson.put("mills", mills)
                    json = ebJson
                    eventType = JsonHelper.safeGetString(json, "eventType")
                    virtualPumpPlugin.fakeDataDetected = true
                }
                */
                is NSTemporaryBasal         ->
                    if (buildHelper.isEngineeringMode() && sp.getBoolean(R.string.key_ns_receive_tbr_eb, false) || config.NSCLIENT)
                        storeDataForDb.temporaryBasals.add(treatment.toTemporaryBasal())

                is NSEffectiveProfileSwitch ->
                    if (sp.getBoolean(R.string.key_ns_receive_profile_switch, false) || config.NSCLIENT) {
                        treatment.toEffectiveProfileSwitch(dateUtil)?.let { effectiveProfileSwitch ->
                            storeDataForDb.effectiveProfileSwitches.add(effectiveProfileSwitch)
                        }
                    }

                is NSProfileSwitch          ->
                    if (sp.getBoolean(R.string.key_ns_receive_profile_switch, false) || config.NSCLIENT) {
                        treatment.toProfileSwitch(activePlugin, dateUtil)?.let { profileSwitch ->
                            storeDataForDb.profileSwitches.add(profileSwitch)
                        }
                    }

                is NSBolusWizard            ->
                    treatment.toBolusCalculatorResult()?.let { bolusCalculatorResult ->
                        storeDataForDb.bolusCalculatorResults.add(bolusCalculatorResult)
                    }

                is NSTherapyEvent           ->
                    treatment.toTherapyEvent().let { therapyEvent ->
                        storeDataForDb.therapyEvents.add(therapyEvent)
                    }
            }
            /*
                        when {

                            eventType == TherapyEvent.Type.COMBO_BOLUS.text                             ->
                                if (buildHelper.isEngineeringMode() && sp.getBoolean(R.string.key_ns_receive_tbr_eb, false) || config.NSCLIENT) {
                                    extendedBolusFromJson(json)?.let { extendedBolus ->
                                        repository.runTransactionForResult(SyncNsExtendedBolusTransaction(extendedBolus))
                                            .doOnError {
                                                aapsLogger.error(LTag.DATABASE, "Error while saving extended bolus", it)
                                                ret = Result.failure(workDataOf("Error" to it.toString()))
                                            }
                                            .blockingGet()
                                            .also { result ->
                                                result.inserted.forEach {
                                                    uel.log(
                                                        Action.EXTENDED_BOLUS, Sources.NSClient,
                                                        ValueWithUnit.Timestamp(it.timestamp),
                                                        ValueWithUnit.Insulin(it.amount),
                                                        ValueWithUnit.UnitPerHour(it.rate),
                                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                                    )
                                                    aapsLogger.debug(LTag.DATABASE, "Inserted ExtendedBolus $it")
                                                }
                                                result.invalidated.forEach {
                                                    uel.log(
                                                        Action.EXTENDED_BOLUS_REMOVED, Sources.NSClient,
                                                        ValueWithUnit.Timestamp(it.timestamp),
                                                        ValueWithUnit.Insulin(it.amount),
                                                        ValueWithUnit.UnitPerHour(it.rate),
                                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                                    )
                                                    aapsLogger.debug(LTag.DATABASE, "Invalidated ExtendedBolus $it")
                                                }
                                                result.ended.forEach {
                                                    uel.log(
                                                        Action.CANCEL_EXTENDED_BOLUS, Sources.NSClient,
                                                        ValueWithUnit.Timestamp(it.timestamp),
                                                        ValueWithUnit.Insulin(it.amount),
                                                        ValueWithUnit.UnitPerHour(it.rate),
                                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                                    )
                                                    aapsLogger.debug(LTag.DATABASE, "Updated ExtendedBolus $it")
                                                }
                                                result.updatedNsId.forEach {
                                                    aapsLogger.debug(LTag.DATABASE, "Updated nsId ExtendedBolus $it")
                                                }
                                                result.updatedDuration.forEach {
                                                    aapsLogger.debug(LTag.DATABASE, "Updated duration ExtendedBolus $it")
                                                }
                                            }
                                    } ?: aapsLogger.error("Error parsing ExtendedBolus json $json")
                                }

                            eventType == TherapyEvent.Type.APS_OFFLINE.text                             ->
                                if (sp.getBoolean(R.string.key_ns_receive_offline_event, false) && buildHelper.isEngineeringMode() || config.NSCLIENT) {
                                    offlineEventFromJson(json)?.let { offlineEvent ->
                                        repository.runTransactionForResult(SyncNsOfflineEventTransaction(offlineEvent))
                                            .doOnError {
                                                aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
                                                ret = Result.failure(workDataOf("Error" to it.toString()))
                                            }
                                            .blockingGet()
                                            .also { result ->
                                                result.inserted.forEach { oe ->
                                                    uel.log(
                                                        Action.LOOP_CHANGE, Sources.NSClient,
                                                        ValueWithUnit.OfflineEventReason(oe.reason),
                                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                                                    )
                                                    aapsLogger.debug(LTag.DATABASE, "Inserted OfflineEvent $oe")
                                                }
                                                result.invalidated.forEach { oe ->
                                                    uel.log(
                                                        Action.LOOP_REMOVED, Sources.NSClient,
                                                        ValueWithUnit.OfflineEventReason(oe.reason),
                                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                                                    )
                                                    aapsLogger.debug(LTag.DATABASE, "Invalidated OfflineEvent $oe")
                                                }
                                                result.ended.forEach { oe ->
                                                    uel.log(
                                                        Action.LOOP_CHANGE, Sources.NSClient,
                                                        ValueWithUnit.OfflineEventReason(oe.reason),
                                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                                                    )
                                                    aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $oe")
                                                }
                                                result.updatedNsId.forEach {
                                                    aapsLogger.debug(LTag.DATABASE, "Updated nsId OfflineEvent $it")
                                                }
                                                result.updatedDuration.forEach {
                                                    aapsLogger.debug(LTag.DATABASE, "Updated duration OfflineEvent $it")
                                                }
                                            }
                                    } ?: aapsLogger.error("Error parsing OfflineEvent json $json")
                                }
                        }
                        if (sp.getBoolean(R.string.key_ns_receive_therapy_events, false) || config.NSCLIENT)
                            if (eventType == TherapyEvent.Type.ANNOUNCEMENT.text) {
                                val date = safeGetLong(json, "mills")
                                val now = System.currentTimeMillis()
                                val enteredBy = JsonHelper.safeGetString(json, "enteredBy", "")
                                val notes = JsonHelper.safeGetString(json, "notes", "")
                                if (date > now - 15 * 60 * 1000L && notes.isNotEmpty()
                                    && enteredBy != sp.getString("careportal_enteredby", "AndroidAPS")
                                ) {
                                    val defaultVal = config.NSCLIENT
                                    if (sp.getBoolean(R.string.key_ns_announcements, defaultVal)) {
                                        val announcement = Notification(Notification.NS_ANNOUNCEMENT, notes, Notification.ANNOUNCEMENT, 60)
                                        rxBus.send(EventNewNotification(announcement))
                                    }
                                }
                            }

                         */
        }
        activePlugin.activeNsClient?.updateLatestTreatmentReceivedIfNewer(latestDateInReceivedData)
//        xDripBroadcast.sendTreatments(treatments)
        return ret
    }

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }
}