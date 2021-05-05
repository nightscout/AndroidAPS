package info.nightscout.androidaps.plugins.general.nsclient

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.*
import info.nightscout.androidaps.extensions.*
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.concurrent.TimeUnit
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
    @Inject lateinit var config: Config
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var uel: UserEntryLogger

    override fun doWork(): Result {
        // Do not accept removed data over WS. Only invalidated trough NSClient
        @Suppress("ConstantConditionIf")
        if (true) return Result.success()

        var ret = Result.success()

        val treatments = dataWorker.pickupJSONArray(inputData.getLong(DataWorker.STORE_KEY, -1))
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        for (i in 0 until treatments.length()) {
            val json = treatments.getJSONObject(i)
            val nsId = JsonHelper.safeGetString(json, "_id") ?: continue

            // room  Temporary target
            val temporaryTarget = temporaryTargetFromNsIdForInvalidating(nsId)
            repository.runTransactionForResult(SyncNsTemporaryTargetTransaction(temporaryTarget, invalidateByNsOnly = true))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while invalidating temporary target", it)
                    ret = Result.failure(workDataOf("Error" to it.toString()))
                }
                .blockingGet()
                .also { result ->
                    result.invalidated.forEach { tt ->
                        uel.log(
                            Action.TT_REMOVED, Sources.NSClient,
                            ValueWithUnit.TherapyEventTTReason(tt.reason),
                            ValueWithUnit.Mgdl(tt.lowTarget),
                            ValueWithUnit.Mgdl(tt.highTarget).takeIf { tt.lowTarget != tt.highTarget },
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt()).takeIf { tt.duration != 0L }
                        )
                    }
                }

            // room  Therapy Event
            val therapyEvent = therapyEventFromNsIdForInvalidating(nsId)
            repository.runTransactionForResult(SyncNsTherapyEventTransaction(therapyEvent, invalidateByNsOnly = true))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while invalidating therapy event", it)
                    ret = Result.failure(workDataOf("Error" to it.toString()))
                }
                .blockingGet()
                .also { result ->
                    result.invalidated.forEach {
                        uel.log(Action.CAREPORTAL_REMOVED, Sources.NSClient,
                            (it.note ?: ""),
                            ValueWithUnit.Timestamp(it.timestamp),
                            ValueWithUnit.TherapyEventType(it.type))
                    }
                }

            // room  Bolus
            val bolus = bolusFromNsIdForInvalidating(nsId)
            repository.runTransactionForResult(SyncNsBolusTransaction(bolus, invalidateByNsOnly = true))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while invalidating bolus", it)
                    ret = Result.failure(workDataOf("Error" to it.toString()))
                }
                .blockingGet()
                .also { result ->
                    result.invalidated.forEach {
                        uel.log(Action.CAREPORTAL_REMOVED, Sources.NSClient,
                            ValueWithUnit.Timestamp(it.timestamp),
                            ValueWithUnit.Insulin(it.amount))
                    }
                }

            // room  Carbs
            val carbs = carbsFromNsIdForInvalidating(nsId)
            repository.runTransactionForResult(SyncNsCarbsTransaction(carbs, invalidateByNsOnly = true))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while invalidating carbs", it)
                    ret = Result.failure(workDataOf("Error" to it.toString()))
                }
                .blockingGet()
                .also { result ->
                    result.invalidated.forEach {
                        uel.log(Action.CAREPORTAL_REMOVED, Sources.NSClient,
                            ValueWithUnit.Timestamp(it.timestamp),
                            ValueWithUnit.Gram(it.amount.toInt()))
                    }
                }

            // room  TemporaryBasal
            val temporaryBasal = temporaryBasalFromNsIdForInvalidating(nsId)
            repository.runTransactionForResult(SyncNsTemporaryBasalTransaction(temporaryBasal, invalidateByNsOnly = true))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while invalidating temporary basal", it)
                    ret = Result.failure(workDataOf("Error" to it.toString()))
                }
                .blockingGet()
                .also { result ->
                    result.invalidated.forEach {
                        uel.log(
                            Action.CAREPORTAL_REMOVED, Sources.NSClient,
                            ValueWithUnit.Timestamp(it.timestamp),
                            ValueWithUnit.UnitPerHour(it.rate))
                    }
                }
            // room  ExtendedBolus
            val extendedBolus = extendedBolusFromNsIdForInvalidating(nsId)
            repository.runTransactionForResult(SyncNsExtendedBolusTransaction(extendedBolus, invalidateByNsOnly = true))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while invalidating extended bolus", it)
                    ret = Result.failure(workDataOf("Error" to it.toString()))
                }
                .blockingGet()
                .also { result ->
                    result.invalidated.forEach {
                        uel.log(
                            Action.CAREPORTAL_REMOVED, Sources.NSClient,
                            ValueWithUnit.Timestamp(it.timestamp),
                            ValueWithUnit.UnitPerHour(it.rate))
                    }
                }

            // room  ProfileSwitch
            repository.runTransactionForResult(InvalidateNsIdProfileSwitchTransaction(nsId))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while invalidating ProfileSwitch", it)
                    ret = Result.failure(workDataOf("Error" to it.toString()))
                }
                .blockingGet()
                .also { result ->
                    result.invalidated.forEach {
                        uel.log(
                            Action.CAREPORTAL_REMOVED, Sources.NSClient,
                            ValueWithUnit.Timestamp(it.timestamp))
                    }
                }
        }

        return ret
    }

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }
}