package info.nightscout.androidaps.plugins.general.nsclient

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.*
import info.nightscout.androidaps.extensions.*
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.JsonHelper.safeGetLong
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class NSClientAddUpdateWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var nsClientPlugin: NSClientPlugin
    @Inject lateinit var dataWorker: DataWorker
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var uel: UserEntryLogger

    override fun doWork(): Result {
        val treatments = dataWorker.pickupJSONArray(inputData.getLong(DataWorker.STORE_KEY, -1))
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        var ret = Result.success()
        var latestDateInReceivedData = 0L

        for (i in 0 until treatments.length()) {
            var json = treatments.getJSONObject(i)
            // new DB model
            val insulin = JsonHelper.safeGetDouble(json, "insulin")
            val carbs = JsonHelper.safeGetDouble(json, "carbs")
            val eventType = JsonHelper.safeGetString(json, "eventType")
            if (eventType == null) {
                aapsLogger.debug(LTag.NSCLIENT, "Wrong treatment. Ignoring : $json")
                continue
            }

            //Find latest date in treatment
            val mills = safeGetLong(json, "mills")
            if (mills != 0L && mills < dateUtil.now())
                if (mills > latestDateInReceivedData) latestDateInReceivedData = mills

            if (insulin > 0) {
                if (sp.getBoolean(R.string.key_ns_receive_insulin, false) && buildHelper.isEngineeringMode() || config.NSCLIENT) {
                    bolusFromJson(json)?.let { bolus ->
                        repository.runTransactionForResult(SyncNsBolusTransaction(bolus, invalidateByNsOnly = false))
                            .doOnError {
                                aapsLogger.error(LTag.DATABASE, "Error while saving bolus", it)
                                ret = Result.failure(workDataOf("Error" to it.toString()))
                            }
                            .blockingGet()
                            .also { result ->
                                result.inserted.forEach {
                                    uel.log(Action.BOLUS, Sources.NSClient,
                                        ValueWithUnit.Timestamp(it.timestamp),
                                        ValueWithUnit.Insulin(it.amount)
                                    )
                                    aapsLogger.debug(LTag.DATABASE, "Inserted bolus $it")
                                }
                                result.invalidated.forEach {
                                    uel.log(Action.BOLUS_REMOVED, Sources.NSClient,
                                        ValueWithUnit.Timestamp(it.timestamp),
                                        ValueWithUnit.Insulin(it.amount)
                                    )
                                    aapsLogger.debug(LTag.DATABASE, "Invalidated bolus $it")
                                }
                                result.updatedNsId.forEach {
                                    aapsLogger.debug(LTag.DATABASE, "Updated nsId bolus $it")
                                }
                            }
                    } ?: aapsLogger.error("Error parsing bolus json $json")
                }
            }
            if (carbs > 0) {
                if (sp.getBoolean(R.string.key_ns_receive_carbs, false) && buildHelper.isEngineeringMode() || config.NSCLIENT) {
                    carbsFromJson(json)?.let { carb ->
                        repository.runTransactionForResult(SyncNsCarbsTransaction(carb, invalidateByNsOnly = false))
                            .doOnError {
                                aapsLogger.error(LTag.DATABASE, "Error while saving carbs", it)
                                ret = Result.failure(workDataOf("Error" to it.toString()))
                            }
                            .blockingGet()
                            .also { result ->
                                result.inserted.forEach {
                                    uel.log(Action.CARBS, Sources.NSClient,
                                        ValueWithUnit.Timestamp(it.timestamp),
                                        ValueWithUnit.Gram(it.amount.toInt())
                                    )
                                    aapsLogger.debug(LTag.DATABASE, "Inserted carbs $it")
                                }
                                result.invalidated.forEach {
                                    uel.log(Action.CARBS_REMOVED, Sources.NSClient,
                                        ValueWithUnit.Timestamp(it.timestamp),
                                        ValueWithUnit.Gram(it.amount.toInt())
                                    )
                                    aapsLogger.debug(LTag.DATABASE, "Invalidated carbs $it")
                                }
                                result.updatedNsId.forEach {
                                    aapsLogger.debug(LTag.DATABASE, "Updated nsId carbs $it")
                                }
                            }
                    } ?: aapsLogger.error("Error parsing bolus json $json")
                }
            }
            // Convert back emulated TBR -> EB
            if (eventType == TherapyEvent.Type.TEMPORARY_BASAL.text && json.has("extendedEmulated")) {
                val ebJson = json.getJSONObject("extendedEmulated")
                ebJson.put("_id", json.getString("_id"))
                ebJson.put("isValid", json.getBoolean("isValid"))
                json = ebJson
            }
            when {
                insulin > 0 || carbs > 0                                    -> Any()
                eventType == TherapyEvent.Type.TEMPORARY_TARGET.text        ->
                    if (sp.getBoolean(R.string.key_ns_receive_temp_target, false) && buildHelper.isEngineeringMode() || config.NSCLIENT) {
                        temporaryTargetFromJson(json)?.let { temporaryTarget ->
                            repository.runTransactionForResult(SyncNsTemporaryTargetTransaction(temporaryTarget, invalidateByNsOnly = false))
                                .doOnError {
                                    aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                                    ret = Result.failure(workDataOf("Error" to it.toString()))
                                }
                                .blockingGet()
                                .also { result ->
                                    result.inserted.forEach { tt ->
                                        uel.log(Action.TT, Sources.NSClient,
                                            ValueWithUnit.TherapyEventTTReason(tt.reason),
                                            ValueWithUnit.fromGlucoseUnit(tt.lowTarget, Constants.MGDL),
                                            ValueWithUnit.fromGlucoseUnit(tt.highTarget, Constants.MGDL).takeIf { tt.lowTarget != tt.highTarget },
                                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                                        )
                                        aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryTarget $tt")
                                    }
                                    result.invalidated.forEach { tt ->
                                        uel.log(Action.TT_REMOVED, Sources.NSClient,
                                            ValueWithUnit.TherapyEventTTReason(tt.reason),
                                            ValueWithUnit.Mgdl(tt.lowTarget),
                                            ValueWithUnit.Mgdl(tt.highTarget).takeIf { tt.lowTarget != tt.highTarget },
                                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                                        )
                                        aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryTarget $tt")
                                    }
                                    result.ended.forEach { tt ->
                                        uel.log(Action.CANCEL_TT, Sources.NSClient,
                                            ValueWithUnit.TherapyEventTTReason(tt.reason),
                                            ValueWithUnit.Mgdl(tt.lowTarget),
                                            ValueWithUnit.Mgdl(tt.highTarget).takeIf { tt.lowTarget != tt.highTarget },
                                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                                        )
                                        aapsLogger.debug(LTag.DATABASE, "Updated TemporaryTarget $tt")
                                    }
                                    result.updatedNsId.forEach {
                                        aapsLogger.debug(LTag.DATABASE, "Updated nsId TemporaryTarget $it")
                                    }
                                }
                        } ?: aapsLogger.error("Error parsing TT json $json")
                    }
                eventType == TherapyEvent.Type.CANNULA_CHANGE.text ||
                    eventType == TherapyEvent.Type.INSULIN_CHANGE.text ||
                    eventType == TherapyEvent.Type.SENSOR_CHANGE.text ||
                    eventType == TherapyEvent.Type.FINGER_STICK_BG_VALUE.text ||
                    eventType == TherapyEvent.Type.NONE.text ||
                    eventType == TherapyEvent.Type.ANNOUNCEMENT.text ||
                    eventType == TherapyEvent.Type.QUESTION.text ||
                    eventType == TherapyEvent.Type.EXERCISE.text ||
                    eventType == TherapyEvent.Type.NOTE.text ||
                    eventType == TherapyEvent.Type.PUMP_BATTERY_CHANGE.text ->
                    if (sp.getBoolean(R.string.key_ns_receive_therapy_events, false) || config.NSCLIENT) {
                        therapyEventFromJson(json)?.let { therapyEvent ->
                            repository.runTransactionForResult(SyncNsTherapyEventTransaction(therapyEvent, invalidateByNsOnly = false))
                                .doOnError {
                                    aapsLogger.error(LTag.DATABASE, "Error while saving therapy event", it)
                                    ret = Result.failure(workDataOf("Error" to it.toString()))
                                }
                                .blockingGet()
                                .also { result ->
                                    val action = when (eventType) {
                                        TherapyEvent.Type.CANNULA_CHANGE.text -> Action.SITE_CHANGE
                                        TherapyEvent.Type.INSULIN_CHANGE.text -> Action.RESERVOIR_CHANGE
                                        else                                  -> Action.CAREPORTAL
                                    }
                                    result.inserted.forEach {
                                        uel.log(action, Sources.NSClient,
                                            it.note ?: "",
                                            ValueWithUnit.Timestamp(it.timestamp),
                                            ValueWithUnit.TherapyEventType(it.type)
                                        )
                                        aapsLogger.debug(LTag.DATABASE, "Inserted TherapyEvent $it")
                                    }
                                    result.invalidated.forEach {
                                        uel.log(Action.CAREPORTAL_REMOVED, Sources.NSClient,
                                            it.note ?: "",
                                            ValueWithUnit.Timestamp(it.timestamp),
                                            ValueWithUnit.TherapyEventType(it.type)
                                        )
                                        aapsLogger.debug(LTag.DATABASE, "Invalidated TherapyEvent $it")
                                    }
                                    result.updatedNsId.forEach {
                                        aapsLogger.debug(LTag.DATABASE, "Updated nsId TherapyEvent $it")
                                    }
                                }
                        } ?: aapsLogger.error("Error parsing TherapyEvent json $json")
                    }
                eventType == TherapyEvent.Type.COMBO_BOLUS.text             ->
                    if (config.NSCLIENT) {
                        extendedBolusFromJson(json)?.let { extendedBolus ->
                            repository.runTransactionForResult(SyncNsExtendedBolusTransaction(extendedBolus, invalidateByNsOnly = false))
                                .doOnError {
                                    aapsLogger.error(LTag.DATABASE, "Error while saving extended bolus", it)
                                    ret = Result.failure(workDataOf("Error" to it.toString()))
                                }
                                .blockingGet()
                                .also { result ->
                                    result.inserted.forEach {
                                        uel.log(Action.EXTENDED_BOLUS, Sources.NSClient,
                                            ValueWithUnit.Timestamp(it.timestamp),
                                            ValueWithUnit.Insulin(it.amount),
                                            ValueWithUnit.UnitPerHour(it.rate),
                                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                        )
                                        aapsLogger.debug(LTag.DATABASE, "Inserted ExtendedBolus $it")
                                    }
                                    result.invalidated.forEach {
                                        uel.log(Action.EXTENDED_BOLUS_REMOVED, Sources.NSClient,
                                            ValueWithUnit.Timestamp(it.timestamp),
                                            ValueWithUnit.Insulin(it.amount),
                                            ValueWithUnit.UnitPerHour(it.rate),
                                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                        )
                                        aapsLogger.debug(LTag.DATABASE, "Invalidated ExtendedBolus $it")
                                    }
                                    result.ended.forEach {
                                        uel.log(Action.CANCEL_EXTENDED_BOLUS, Sources.NSClient,
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
                                }
                        } ?: aapsLogger.error("Error parsing ExtendedBolus json $json")
                    }
                eventType == TherapyEvent.Type.TEMPORARY_BASAL.text         ->
                    if (config.NSCLIENT) {
                        temporaryBasalFromJson(json)?.let { temporaryBasal ->
                            repository.runTransactionForResult(SyncNsTemporaryBasalTransaction(temporaryBasal, invalidateByNsOnly = false))
                                .doOnError {
                                    aapsLogger.error(LTag.DATABASE, "Error while saving temporary basal", it)
                                    ret = Result.failure(workDataOf("Error" to it.toString()))
                                }
                                .blockingGet()
                                .also { result ->
                                    result.inserted.forEach {
                                        uel.log(Action.TEMP_BASAL, Sources.NSClient,
                                            ValueWithUnit.Timestamp(it.timestamp),
                                            if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                        )
                                        aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryBasal $it")
                                    }
                                    result.invalidated.forEach {
                                        uel.log(Action.TEMP_BASAL_REMOVED, Sources.NSClient,
                                            ValueWithUnit.Timestamp(it.timestamp),
                                            if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                        )
                                        aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryBasal $it")
                                    }
                                    result.ended.forEach {
                                        uel.log(Action.CANCEL_TEMP_BASAL, Sources.NSClient,
                                            ValueWithUnit.Timestamp(it.timestamp),
                                            if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                        )
                                        aapsLogger.debug(LTag.DATABASE, "Ended TemporaryBasal $it")
                                    }
                                    result.updatedNsId.forEach {
                                        aapsLogger.debug(LTag.DATABASE, "Updated nsId TemporaryBasal $it")
                                    }
                                }
                        } ?: aapsLogger.error("Error parsing TemporaryBasal json $json")
                    }
                eventType == TherapyEvent.Type.PROFILE_SWITCH.text          ->
                    if (sp.getBoolean(R.string.key_ns_receive_profile_switch, false) && buildHelper.isEngineeringMode() || config.NSCLIENT) {
                        profileSwitchFromJson(json, dateUtil, activePlugin)?.let { profileSwitch ->
                            repository.runTransactionForResult(SyncNsProfileSwitchTransaction(profileSwitch, invalidateByNsOnly = false))
                                .doOnError {
                                    aapsLogger.error(LTag.DATABASE, "Error while saving ProfileSwitch", it)
                                    ret = Result.failure(workDataOf("Error" to it.toString()))
                                }
                                .blockingGet()
                                .also { result ->
                                    result.inserted.forEach {
                                        uel.log(Action.PROFILE_SWITCH, Sources.NSClient,
                                            ValueWithUnit.Timestamp(it.timestamp))
                                        aapsLogger.debug(LTag.DATABASE, "Inserted ProfileSwitch $it")
                                    }
                                    result.invalidated.forEach {
                                        uel.log(Action.PROFILE_SWITCH_REMOVED, Sources.NSClient,
                                            ValueWithUnit.Timestamp(it.timestamp))
                                        aapsLogger.debug(LTag.DATABASE, "Invalidated ProfileSwitch $it")
                                    }
                                    result.updatedNsId.forEach {
                                        aapsLogger.debug(LTag.DATABASE, "Updated nsId ProfileSwitch $it")
                                    }
                                }
                        } ?: aapsLogger.error("Error parsing ProfileSwitch json $json")
                    }
                eventType == TherapyEvent.Type.APS_OFFLINE.text          ->
                    if (sp.getBoolean(R.string.key_ns_receive_offline_event, false) && buildHelper.isEngineeringMode() || config.NSCLIENT) {
                        offlineEventFromJson(json)?.let { offlineEvent ->
                            repository.runTransactionForResult(SyncNsOfflineEventTransaction(offlineEvent, invalidateByNsOnly = false))
                                .doOnError {
                                    aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
                                    ret = Result.failure(workDataOf("Error" to it.toString()))
                                }
                                .blockingGet()
                                .also { result ->
                                    result.inserted.forEach { oe ->
                                        uel.log(Action.LOOP_CHANGE, Sources.NSClient,
                                            ValueWithUnit.OfflineEventReason(oe.reason),
                                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                                        )
                                        aapsLogger.debug(LTag.DATABASE, "Inserted OfflineEvent $oe")
                                    }
                                    result.invalidated.forEach { oe ->
                                        uel.log(Action.LOOP_REMOVED, Sources.NSClient,
                                            ValueWithUnit.OfflineEventReason(oe.reason),
                                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                                        )
                                        aapsLogger.debug(LTag.DATABASE, "Invalidated OfflineEvent $oe")
                                    }
                                    result.ended.forEach { oe ->
                                        uel.log(Action.LOOP_CHANGE, Sources.NSClient,
                                            ValueWithUnit.OfflineEventReason(oe.reason),
                                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                                        )
                                        aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $oe")
                                    }
                                    result.updatedNsId.forEach {
                                        aapsLogger.debug(LTag.DATABASE, "Updated nsId OfflineEvent $it")
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
                        && enteredBy != sp.getString("careportal_enteredby", "AndroidAPS")) {
                        val defaultVal = config.NSCLIENT
                        if (sp.getBoolean(R.string.key_ns_announcements, defaultVal)) {
                            val announcement = Notification(Notification.NS_ANNOUNCEMENT, notes, Notification.ANNOUNCEMENT, 60)
                            rxBus.send(EventNewNotification(announcement))
                        }
                    }
                }
        }
        nsClientPlugin.updateLatestDateReceivedIfNewer(latestDateInReceivedData)
        return ret
    }

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }
}