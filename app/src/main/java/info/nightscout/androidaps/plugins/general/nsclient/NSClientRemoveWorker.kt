package info.nightscout.androidaps.plugins.general.nsclient

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.ValueWithUnit
import info.nightscout.androidaps.database.transactions.SyncTemporaryTargetTransaction
import info.nightscout.androidaps.database.transactions.SyncTherapyEventTransaction
import info.nightscout.androidaps.events.EventNsTreatment
import info.nightscout.androidaps.events.EventNsTreatment.Companion.REMOVE
import info.nightscout.androidaps.interfaces.ConfigInterface
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.extensions.temporaryTargetFromNsIdForInvalidating
import info.nightscout.androidaps.utils.extensions.therapyEventFromNsIdForInvalidating
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject

// This will not be needed fpr NS v3
// Now NS provides on _id of removed records

class NSClientRemoveWorker(
    context: Context,
    params: WorkerParameters) : Worker(context, params) {

    @Inject lateinit var nsClientPlugin: NSClientPlugin
    @Inject lateinit var dataWorker: DataWorker
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var config: ConfigInterface
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var databaseHelper: DatabaseHelperInterface
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var uel: UserEntryLogger

    override fun doWork(): Result {
        val acceptNSData = !sp.getBoolean(R.string.key_ns_upload_only, true) && buildHelper.isEngineeringMode() || config.NSCLIENT
        if (!acceptNSData) return Result.failure()

        var ret = Result.success()

        val treatments = dataWorker.pickupJSONArray(inputData.getLong(DataWorker.STORE_KEY, -1))
            ?: return Result.failure()

        for (i in 0 until treatments.length()) {
            val json = treatments.getJSONObject(i)
            val nsId = JsonHelper.safeGetString(json, "_id") ?: continue

            // room  Temporary target
            val temporaryTarget = temporaryTargetFromNsIdForInvalidating(nsId)
            repository.runTransactionForResult(SyncTemporaryTargetTransaction(temporaryTarget))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while removing temporary target", it)
                    ret = Result.failure()
                }
                .blockingGet()
                .also { result ->
                    result.invalidated.forEach {
                        uel.log(
                            UserEntry.Action.TT_DELETED_FROM_NS,
                            ValueWithUnit(it.reason.text, UserEntry.Units.TherapyEvent),
                            ValueWithUnit(it.lowTarget, UserEntry.Units.Mg_Dl, true),
                            ValueWithUnit(it.highTarget, UserEntry.Units.Mg_Dl, it.lowTarget != it.highTarget),
                            ValueWithUnit(it.duration.toInt() / 60000, UserEntry.Units.M, it.duration != 0L)
                        )
                    }
                }

            // room  Therapy Event
            val therapyEvent = therapyEventFromNsIdForInvalidating(nsId)
            repository.runTransactionForResult(SyncTherapyEventTransaction(therapyEvent))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while removing therapy event", it)
                    ret = Result.failure()
                }
                .blockingGet()
                .also { result ->
                    result.invalidated.forEach {
                        uel.log(
                            UserEntry.Action.CAREPORTAL_DELETED_FROM_NS, (it.note ?: ""),
                            ValueWithUnit(it.timestamp, UserEntry.Units.Timestamp, true),
                            ValueWithUnit(it.type.text, UserEntry.Units.TherapyEvent))
                    }
                }

            // Insulin, carbs
            rxBus.send(EventNsTreatment(REMOVE, json))
            // old DB model
            databaseHelper.deleteTempBasalById(nsId)
            databaseHelper.deleteExtendedBolusById(nsId)
            databaseHelper.deleteProfileSwitchById(nsId)
        }

        return ret
    }

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }
}