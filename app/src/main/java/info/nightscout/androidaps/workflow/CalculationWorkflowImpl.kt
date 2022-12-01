package info.nightscout.androidaps.workflow

import android.content.Context
import android.os.SystemClock
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkContinuation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.core.graph.OverviewData
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.core.workflow.CalculationWorkflow
import info.nightscout.core.workflow.CalculationWorkflow.Companion.JOB
import info.nightscout.core.workflow.CalculationWorkflow.Companion.MAIN_CALCULATION
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.Event
import info.nightscout.rx.events.EventAppInitialized
import info.nightscout.rx.events.EventNewHistoryData
import info.nightscout.rx.events.EventOfflineChange
import info.nightscout.rx.events.EventPreferenceChange
import info.nightscout.rx.events.EventTherapyEventChange
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import info.nightscout.workflow.DummyWorker
import info.nightscout.workflow.InvokeLoopWorker
import info.nightscout.workflow.LoadBgDataWorker
import info.nightscout.workflow.PrepareBasalDataWorker
import info.nightscout.workflow.PrepareBgDataWorker
import info.nightscout.workflow.PrepareBucketedDataWorker
import info.nightscout.workflow.PrepareIobAutosensGraphDataWorker
import info.nightscout.workflow.PreparePredictionsWorker
import info.nightscout.workflow.PrepareTemporaryTargetDataWorker
import info.nightscout.workflow.PrepareTreatmentsDataWorker
import info.nightscout.workflow.UpdateGraphWorker
import info.nightscout.workflow.UpdateIobCobSensWorker
import info.nightscout.workflow.iob.IobCobOref1Worker
import info.nightscout.workflow.iob.IobCobOrefWorker
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalculationWorkflowImpl @Inject constructor(
    aapsSchedulers: AapsSchedulers,
    rh: ResourceHelper,
    rxBus: RxBus,
    private val context: Context,
    private val injector: HasAndroidInjector,
    private val aapsLogger: AAPSLogger,
    private val fabricPrivacy: FabricPrivacy,
    private val dateUtil: DateUtil,
    private val dataWorkerStorage: DataWorkerStorage,
    private val activePlugin: ActivePlugin
) : CalculationWorkflow {

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val iobCobCalculator: IobCobCalculator
        get() = activePlugin.activeIobCobCalculator // cross-dependency CalculationWorkflow x IobCobCalculator
    private val overviewData: OverviewData
        get() = (iobCobCalculator as IobCobCalculatorPlugin).overviewData

    init {
        // Verify definition
        var sumPercent = 0
        for (pass in CalculationWorkflow.ProgressData.values()) sumPercent += pass.percentOfTotal
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
                           if (event.isChanged(rh.gs(R.string.key_units))) {
                               overviewData.reset()
                               rxBus.send(EventNewHistoryData(0, false))
                           }
                           if (event.isChanged(rh.gs(R.string.key_rangetodisplay))) {
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

    override fun stopCalculation(job: String, from: String) {
        aapsLogger.debug(LTag.AUTOSENS, "Stopping calculation thread: $from")
        WorkManager.getInstance(context).cancelUniqueWork(job)
        val workStatus = WorkManager.getInstance(context).getWorkInfosForUniqueWork(job).get()
        while (workStatus.size >= 1 && workStatus[0].state == WorkInfo.State.RUNNING)
            SystemClock.sleep(100)
        aapsLogger.debug(LTag.AUTOSENS, "Calculation thread stopped: $from")
    }

    override fun runCalculation(
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
                if (bgDataReload) OneTimeWorkRequest.Builder(LoadBgDataWorker::class.java).setInputData(dataWorkerStorage.storeInputData(LoadBgDataWorker.LoadBgData(iobCobCalculator, end))).build()
                else OneTimeWorkRequest.Builder(DummyWorker::class.java).build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareBucketedDataWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PrepareBucketedDataWorker.PrepareBucketedData(iobCobCalculator, overviewData)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareBgDataWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PrepareBgDataWorker.PrepareBgData(iobCobCalculator, overviewData)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .setInputData(Data.Builder().putString(JOB, job).build())
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareTreatmentsDataWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PrepareTreatmentsDataWorker.PrepareTreatmentsData(overviewData)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareBasalDataWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PrepareBasalDataWorker.PrepareBasalData(iobCobCalculator, overviewData)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareTemporaryTargetDataWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PrepareTemporaryTargetDataWorker.PrepareTemporaryTargetData(overviewData)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .setInputData(Data.Builder().putString(JOB, job).build())
                    .build()
            )
            .then(
                if (activePlugin.activeSensitivity.isOref1)
                    OneTimeWorkRequest.Builder(IobCobOref1Worker::class.java)
                        .setInputData(dataWorkerStorage.storeInputData(IobCobOref1Worker.IobCobOref1WorkerData(injector, iobCobCalculator, from, end, limitDataToOldestAvailable, cause)))
                        .build()
                else
                    OneTimeWorkRequest.Builder(IobCobOrefWorker::class.java)
                        .setInputData(dataWorkerStorage.storeInputData(IobCobOrefWorker.IobCobOrefWorkerData(injector, iobCobCalculator, from, end, limitDataToOldestAvailable, cause)))
                        .build()
            )
            .then(OneTimeWorkRequest.Builder(UpdateIobCobSensWorker::class.java).build())
            .then(
                OneTimeWorkRequest.Builder(PrepareIobAutosensGraphDataWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PrepareIobAutosensGraphDataWorker.PrepareIobAutosensData(iobCobCalculator, overviewData)))
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
                    .setInputData(dataWorkerStorage.storeInputData(InvokeLoopWorker.InvokeLoopData(cause)))
                    .build()
            )
            .then(
                runLoop,
                OneTimeWorkRequest.Builder(PreparePredictionsWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PreparePredictionsWorker.PreparePredictionsData(overviewData)))
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
                    .setInputData(dataWorkerStorage.storeInputData(PrepareTreatmentsDataWorker.PrepareTreatmentsData(overviewData)))
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
                    .setInputData(dataWorkerStorage.storeInputData(PrepareBucketedDataWorker.PrepareBucketedData(iobCobCalculator, overviewData)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareBgDataWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PrepareBgDataWorker.PrepareBgData(iobCobCalculator, overviewData)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .build()
            )
            .enqueue()
    }
}