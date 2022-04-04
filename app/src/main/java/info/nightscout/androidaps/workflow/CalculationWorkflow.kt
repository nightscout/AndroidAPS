package info.nightscout.androidaps.workflow

import android.content.Context
import android.os.SystemClock
import androidx.work.*
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.events.EventAppInitialized
import info.nightscout.androidaps.events.EventOfflineChange
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.events.EventTherapyEventChange
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.OverviewData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobOref1Worker
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobOrefWorker
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventNewHistoryData
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalculationWorkflow @Inject constructor(
    aapsSchedulers: AapsSchedulers,
    rh: ResourceHelper,
    rxBus: RxBus,
    private val context: Context,
    private val injector: HasAndroidInjector,
    private val aapsLogger: AAPSLogger,
    private val fabricPrivacy: FabricPrivacy,
    private val dateUtil: DateUtil,
    private val sensitivityOref1Plugin: SensitivityOref1Plugin,
    private val dataWorker: DataWorker,
    private val activePlugin: ActivePlugin
) {

    companion object {

        const val MAIN_CALCULATION = "calculation"
        const val HISTORY_CALCULATION = "history_calculation"
        const val JOB = "job"
    }

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val iobCobCalculator: IobCobCalculator
        get() = activePlugin.activeIobCobCalculator // cross-dependency CalculationWorkflow x IobCobCalculator
    private val overviewData: OverviewData
        get() = (iobCobCalculator as IobCobCalculatorPlugin).overviewData

    enum class ProgressData(val pass: Int, val percentOfTotal: Int) {
        PREPARE_BASAL_DATA(0, 5),
        PREPARE_TEMPORARY_TARGET_DATA(1, 5),
        PREPARE_TREATMENTS_DATA(2, 5),
        IOB_COB_OREF(3, 75),
        PREPARE_IOB_AUTOSENS_DATA(4, 10);

        fun finalPercent(progress: Int): Int {
            var total = 0
            for (i in values()) if (i.pass < pass) total += i.percentOfTotal
            total += (percentOfTotal.toDouble() * progress / 100.0).toInt()
            return total
        }
    }

    init {
        // Verify definition
        var sumPercent = 0
        for (pass in ProgressData.values()) sumPercent += pass.percentOfTotal
        require(sumPercent == 100)

        disposable += rxBus
            .toObservable(EventTherapyEventChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ runOnEventTherapyEventChange() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventOfflineChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ runOnEventTherapyEventChange() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                           if (event.isChanged(rh, R.string.key_units)) {
                               overviewData.reset()
                               rxBus.send(EventNewHistoryData(0, false))
                           }
                           if (event.isChanged(rh, R.string.key_rangetodisplay)) {
                               overviewData.initRange()
                               runOnScaleChanged()
                               rxBus.send(EventNewHistoryData(0, false))
                           }
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAppInitialized::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe(
                {
                    runCalculation(
                        MAIN_CALCULATION,
                        iobCobCalculator,
                        overviewData,
                        "onEventAppInitialized",
                        System.currentTimeMillis(),
                        bgDataReload = true,
                        limitDataToOldestAvailable = true,
                        cause = it,
                        runLoop = true
                    )
                },
                fabricPrivacy::logException
            )

    }

    fun stopCalculation(job: String, from: String) {
        aapsLogger.debug(LTag.AUTOSENS, "Stopping calculation thread: $from")
        WorkManager.getInstance(context).cancelUniqueWork(job)
        val workStatus = WorkManager.getInstance(context).getWorkInfosForUniqueWork(job).get()
        while (workStatus.size >= 1 && workStatus[0].state == WorkInfo.State.RUNNING)
            SystemClock.sleep(100)
        aapsLogger.debug(LTag.AUTOSENS, "Calculation thread stopped: $from")
    }

    fun runCalculation(
        job: String,
        iobCobCalculator: IobCobCalculator,
        overviewData: OverviewData,
        from: String,
        end: Long,
        bgDataReload: Boolean,
        limitDataToOldestAvailable: Boolean,
        cause: Event?,
        runLoop: Boolean
    ) {
        aapsLogger.debug(LTag.AUTOSENS, "Starting calculation worker: $from to ${dateUtil.dateAndTimeAndSecondsString(end)}")

        WorkManager.getInstance(context)
            .beginUniqueWork(
                job, ExistingWorkPolicy.REPLACE,
                if (bgDataReload) OneTimeWorkRequest.Builder(LoadBgDataWorker::class.java).setInputData(dataWorker.storeInputData(LoadBgDataWorker.LoadBgData(iobCobCalculator, end))).build()
                else OneTimeWorkRequest.Builder(DummyWorker::class.java).build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareBucketedDataWorker::class.java)
                    .setInputData(dataWorker.storeInputData(PrepareBucketedDataWorker.PrepareBucketedData(iobCobCalculator, overviewData)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareBgDataWorker::class.java)
                    .setInputData(dataWorker.storeInputData(PrepareBgDataWorker.PrepareBgData(iobCobCalculator, overviewData)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .setInputData(Data.Builder().putString(JOB, job).build())
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareTreatmentsDataWorker::class.java)
                    .setInputData(dataWorker.storeInputData(PrepareTreatmentsDataWorker.PrepareTreatmentsData(overviewData)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareBasalDataWorker::class.java)
                    .setInputData(dataWorker.storeInputData(PrepareBasalDataWorker.PrepareBasalData(iobCobCalculator, overviewData)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareTemporaryTargetDataWorker::class.java)
                    .setInputData(dataWorker.storeInputData(PrepareTemporaryTargetDataWorker.PrepareTemporaryTargetData(overviewData)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .setInputData(Data.Builder().putString(JOB, job).build())
                    .build()
            )
            .then(
                if (sensitivityOref1Plugin.isEnabled())
                    OneTimeWorkRequest.Builder(IobCobOref1Worker::class.java)
                        .setInputData(dataWorker.storeInputData(IobCobOref1Worker.IobCobOref1WorkerData(injector, iobCobCalculator, from, end, limitDataToOldestAvailable, cause)))
                        .build()
                else
                    OneTimeWorkRequest.Builder(IobCobOrefWorker::class.java)
                        .setInputData(dataWorker.storeInputData(IobCobOrefWorker.IobCobOrefWorkerData(injector, iobCobCalculator, from, end, limitDataToOldestAvailable, cause)))
                        .build()
            )
            .then(OneTimeWorkRequest.Builder(UpdateIobCobSensWorker::class.java).build())
            .then(
                OneTimeWorkRequest.Builder(PrepareIobAutosensGraphDataWorker::class.java)
                    .setInputData(dataWorker.storeInputData(PrepareIobAutosensGraphDataWorker.PrepareIobAutosensData(iobCobCalculator, overviewData)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .setInputData(Data.Builder().putString(JOB, job).build())
                    .build()
            )
            .then(
                runLoop,
                OneTimeWorkRequest.Builder(InvokeLoopWorker::class.java)
                    .setInputData(dataWorker.storeInputData(InvokeLoopWorker.InvokeLoopData(cause)))
                    .build()
            )
            .then(
                runLoop,
                OneTimeWorkRequest.Builder(PreparePredictionsWorker::class.java)
                    .setInputData(dataWorker.storeInputData(PreparePredictionsWorker.PreparePredictionsData(overviewData)))
                    .build()
            )
            .then(
                runLoop, OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .setInputData(Data.Builder().putString(JOB, job).build())
                    .build()
            )
            .enqueue()
    }

    fun WorkContinuation.then(shouldAdd: Boolean, work: OneTimeWorkRequest): WorkContinuation =
        if (shouldAdd) then(work) else this

    private fun runOnEventTherapyEventChange() {
        WorkManager.getInstance(context)
            .beginUniqueWork(
                MAIN_CALCULATION, ExistingWorkPolicy.APPEND,
                OneTimeWorkRequest.Builder(PrepareTreatmentsDataWorker::class.java)
                    .setInputData(dataWorker.storeInputData(PrepareTreatmentsDataWorker.PrepareTreatmentsData(overviewData)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .build()
            )
            .enqueue()

    }

    private fun runOnScaleChanged() {
        WorkManager.getInstance(context)
            .beginUniqueWork(
                MAIN_CALCULATION, ExistingWorkPolicy.APPEND,
                OneTimeWorkRequest.Builder(PrepareBucketedDataWorker::class.java)
                    .setInputData(dataWorker.storeInputData(PrepareBucketedDataWorker.PrepareBucketedData(iobCobCalculator, overviewData)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareBgDataWorker::class.java)
                    .setInputData(dataWorker.storeInputData(PrepareBgDataWorker.PrepareBgData(iobCobCalculator, overviewData)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .build()
            )
            .enqueue()
    }
}