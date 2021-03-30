package info.nightscout.androidaps.plugins.general.nsclient

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.XXXValueWithUnit
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.UserEntry.Units
import info.nightscout.androidaps.database.entities.UserEntry.ValueWithUnit
import info.nightscout.androidaps.database.transactions.SyncNsBolusTransaction
import info.nightscout.androidaps.database.transactions.SyncNsCarbsTransaction
import info.nightscout.androidaps.database.transactions.SyncNsTemporaryTargetTransaction
import info.nightscout.androidaps.database.transactions.SyncNsTherapyEventTransaction
import info.nightscout.androidaps.interfaces.ConfigInterface
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface
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
import info.nightscout.androidaps.utils.extensions.bolusFromJson
import info.nightscout.androidaps.utils.extensions.carbsFromJson
import info.nightscout.androidaps.utils.extensions.temporaryTargetFromJson
import info.nightscout.androidaps.utils.extensions.therapyEventFromJson
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
    @Inject lateinit var dateutil: DateUtil
    @Inject lateinit var config: ConfigInterface
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var databaseHelper: DatabaseHelperInterface
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var uel: UserEntryLogger

    override fun doWork(): Result {
        val acceptNSData = !sp.getBoolean(R.string.key_ns_upload_only, true) && buildHelper.isEngineeringMode() || config.NSCLIENT
        if (!acceptNSData) return Result.failure()

        val treatments = dataWorker.pickupJSONArray(inputData.getLong(DataWorker.STORE_KEY, -1))
            ?: return Result.failure()

        var ret = Result.success()
        var latestDateInReceivedData = 0L

        for (i in 0 until treatments.length()) {
            val json = treatments.getJSONObject(i)
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
            if (mills != 0L && mills < dateutil._now())
                if (mills > latestDateInReceivedData) latestDateInReceivedData = mills

            if (insulin > 0) {
                bolusFromJson(json)?.let { bolus ->
                    repository.runTransactionForResult(SyncNsBolusTransaction(bolus))
                        .doOnError {
                            aapsLogger.error(LTag.DATABASE, "Error while saving bolus", it)
                            ret = Result.failure()
                        }
                        .blockingGet()
                        .also { result ->
                            result.inserted.forEach {
                                uel.log(Action.CAREPORTAL,
                                    ValueWithUnit(Sources.NSClient),
                                    ValueWithUnit(it.timestamp, UserEntry.Units.Timestamp, true),
                                    ValueWithUnit(it.amount, UserEntry.Units.U)
                                )
                            }
                            result.invalidated.forEach {
                                uel.log(Action.CAREPORTAL_REMOVED,
                                    ValueWithUnit(Sources.NSClient),
                                    ValueWithUnit(it.timestamp, UserEntry.Units.Timestamp, true),
                                    ValueWithUnit(it.amount, UserEntry.Units.U)
                                )
                            }
                        }
                } ?: aapsLogger.error("Error parsing bolus json $json")
            }
            if (carbs > 0) {
                carbsFromJson(json)?.let { carb ->
                    repository.runTransactionForResult(SyncNsCarbsTransaction(carb))
                        .doOnError {
                            aapsLogger.error(LTag.DATABASE, "Error while saving carbs", it)
                            ret = Result.failure()
                        }
                        .blockingGet()
                        .also { result ->
                            result.inserted.forEach {
                                uel.log(Action.CAREPORTAL,
                                    ValueWithUnit(Sources.NSClient),
                                    ValueWithUnit(it.timestamp, Units.Timestamp, true),
                                    ValueWithUnit(it.amount, Units.G)
                                )
                            }
                            result.invalidated.forEach {
                                uel.log(Action.CAREPORTAL,
                                    ValueWithUnit(Sources.NSClient),
                                    ValueWithUnit(it.timestamp, Units.Timestamp, true),
                                    ValueWithUnit(it.amount, Units.G)
                                )
                            }
                        }
                } ?: aapsLogger.error("Error parsing bolus json $json")
            }
            when {
                insulin > 0 || carbs > 0                                    -> Any()
                eventType == TherapyEvent.Type.TEMPORARY_TARGET.text        ->
                    temporaryTargetFromJson(json)?.let { temporaryTarget ->
                        repository.runTransactionForResult(SyncNsTemporaryTargetTransaction(temporaryTarget))
                            .doOnError {
                                aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                                ret = Result.failure()
                            }
                            .blockingGet()
                            .also { result ->
                                /*result.inserted.forEach { tt ->
                                    uel.log(UserEntry.Action.TT_FROM_NS,
                                        XXXValueWithUnit.TherapyEventTTReason(tt.reason),
                                        XXXValueWithUnit.Mgdl(tt.lowTarget),
                                        XXXValueWithUnit.Mgdl(tt.highTarget).takeIf { tt.lowTarget != tt.highTarget },
                                        XXXValueWithUnit.Minute(tt.duration.toInt() / 60000)*/
                                result.inserted.forEach {
                                    uel.log(Action.TT,
                                        ValueWithUnit(Sources.NSClient),
                                        ValueWithUnit(it.reason.text, Units.TherapyEvent),
                                        ValueWithUnit(it.lowTarget, Units.Mg_Dl, true),
                                        ValueWithUnit(it.highTarget, Units.Mg_Dl, it.lowTarget != it.highTarget),
                                        ValueWithUnit(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt(), Units.M, true)
                                    )
                                }
                                /*result.invalidated.forEach { tt ->
                                    uel.log(UserEntry.Action.TT_DELETED_FROM_NS,
                                        XXXValueWithUnit.TherapyEventTTReason(tt.reason),
                                        XXXValueWithUnit.Mgdl(tt.lowTarget),
                                        XXXValueWithUnit.Mgdl(tt.highTarget).takeIf { tt.lowTarget != tt.highTarget },
                                        XXXValueWithUnit.Minute(tt.duration.toInt() / 60000)*/
                                result.invalidated.forEach {
                                    uel.log(Action.TT_REMOVED, ValueWithUnit(Sources.NSClient),
                                        ValueWithUnit(it.reason.text, Units.TherapyEvent),
                                        ValueWithUnit(it.lowTarget, Units.Mg_Dl, true),
                                        ValueWithUnit(it.highTarget, Units.Mg_Dl, it.lowTarget != it.highTarget),
                                        ValueWithUnit(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt(), Units.M, true)
                                    )
                                }
                                /*result.ended.forEach { tt ->
                                    uel.log(UserEntry.Action.TT_CANCELED_FROM_NS,
                                        XXXValueWithUnit.TherapyEventTTReason(tt.reason),
                                        XXXValueWithUnit.Mgdl(tt.lowTarget),
                                        XXXValueWithUnit.Mgdl(tt.highTarget).takeIf { tt.lowTarget != tt.highTarget },
                                        XXXValueWithUnit.Minute(tt.duration.toInt() / 60000)
                                 */
                                result.ended.forEach {
                                    uel.log(Action.CANCEL_TT, ValueWithUnit(Sources.NSClient),
                                        ValueWithUnit(it.reason.text, Units.TherapyEvent),
                                        ValueWithUnit(it.lowTarget, Units.Mg_Dl, true),
                                        ValueWithUnit(it.highTarget, Units.Mg_Dl, it.lowTarget != it.highTarget),
                                        ValueWithUnit(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt(), Units.M, true)
                                    )
                                }
                            }
                    } ?: aapsLogger.error("Error parsing TT json $json")
                eventType == TherapyEvent.Type.CANNULA_CHANGE.text ||
                    eventType == TherapyEvent.Type.INSULIN_CHANGE.text ||
                    eventType == TherapyEvent.Type.SENSOR_CHANGE.text ||
                    eventType == TherapyEvent.Type.FINGER_STICK_BG_VALUE.text ||
                    eventType == TherapyEvent.Type.NONE.text ||
                    eventType == TherapyEvent.Type.ANNOUNCEMENT.text ||
                    eventType == TherapyEvent.Type.QUESTION.text ||
                    eventType == TherapyEvent.Type.EXERCISE.text ||
                    eventType == TherapyEvent.Type.APS_OFFLINE.text ||
                    eventType == TherapyEvent.Type.PUMP_BATTERY_CHANGE.text ->
                    therapyEventFromJson(json)?.let { therapyEvent ->
                        repository.runTransactionForResult(SyncNsTherapyEventTransaction(therapyEvent))
                            .doOnError {
                                aapsLogger.error(LTag.DATABASE, "Error while saving therapy event", it)
                                ret = Result.failure()
                            }
                            .blockingGet()
                            .also { result ->
                                /*result.inserted.forEach {
                                    uel.log(UserEntry.Action.CAREPORTAL_FROM_NS,
                                        it.note ?: "",
                                        XXXValueWithUnit.Timestamp(it.timestamp),
                                        XXXValueWithUnit.TherapyEventType(it.type)
                                  */
                                 result.inserted.forEach {
                                     uel.log(Action.CAREPORTAL,
                                        it.note ?: "",
                                        ValueWithUnit(Sources.NSClient),
                                        ValueWithUnit(it.timestamp, Units.Timestamp, true),
                                        ValueWithUnit(it.type.text, Units.TherapyEvent)
                                    )
                                }
                                /*result.invalidated.forEach {
                                    uel.log(UserEntry.Action.CAREPORTAL_DELETED_FROM_NS,
                                        it.note ?: "",
                                        XXXValueWithUnit.Timestamp(it.timestamp),
                                        XXXValueWithUnit.TherapyEventType(it.type)
                                 */
                                result.invalidated.forEach {
                                    uel.log(UserEntry.Action.CAREPORTAL_REMOVED,
                                        it.note ?: "",
                                        ValueWithUnit(Sources.NSClient),
                                        ValueWithUnit(it.timestamp, Units.Timestamp, true),
                                        ValueWithUnit(it.type.text, Units.TherapyEvent)
                                    )
                                }
                            }
                    } ?: aapsLogger.error("Error parsing TherapyEvent json $json")
                eventType == TherapyEvent.Type.TEMPORARY_BASAL.text         ->
                    databaseHelper.createTempBasalFromJsonIfNotExists(json)
                eventType == TherapyEvent.Type.COMBO_BOLUS.text             ->
                    databaseHelper.createExtendedBolusFromJsonIfNotExists(json)
                eventType == TherapyEvent.Type.PROFILE_SWITCH.text          ->
                    databaseHelper.createProfileSwitchFromJsonIfNotExists(json)
            }
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