package app.aaps.workflow

import android.content.Context
import android.os.SystemClock
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.rx.events.EventNewHistoryData
import app.aaps.core.interfaces.rx.events.EventOfflineChange
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventTherapyEventChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.main.graph.OverviewData
import app.aaps.core.main.utils.fabric.FabricPrivacy
import app.aaps.core.main.workflow.CalculationWorkflow
import app.aaps.core.main.workflow.CalculationWorkflow.Companion.JOB
import app.aaps.core.main.workflow.CalculationWorkflow.Companion.MAIN_CALCULATION
import app.aaps.core.main.workflow.CalculationWorkflow.Companion.PASS
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.core.utils.worker.then
import dagger.android.HasAndroidInjector
import info.nightscout.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
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
import info.nightscout.workflow.UpdateWidgetWorker
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
                           if (event.isChanged(rh.gs(info.nightscout.core.utils.R.string.key_units))) {
                               overviewData.reset()
                               rxBus.send(EventNewHistoryData(0, false))
                           }
                           if (event.isChanged(rh.gs(info.nightscout.core.utils.R.string.key_rangetodisplay))) {
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
                        cause = it
                    )
                },
                fabricPrivacy::logException
            )

    }

    override fun stopCalculation(job: String, from: String) {
        aapsLogger.debug(LTag.WORKER, "Stopping calculation thread: $from")
        WorkManager.getInstance(context).cancelUniqueWork(job)
        val workStatus = WorkManager.getInstance(context).getWorkInfosForUniqueWork(job).get()
        while (workStatus.size >= 1 && workStatus[0].state == WorkInfo.State.RUNNING)
            SystemClock.sleep(100)
        aapsLogger.debug(LTag.WORKER, "Calculation thread stopped: $from")
    }

    override fun runCalculation(
        job: String,
        iobCobCalculator: IobCobCalculator,
        overviewData: OverviewData,
        reason: String,
        end: Long,
        bgDataReload: Boolean,
        cause: Event?
    ) {
        aapsLogger.debug(LTag.WORKER, "Starting calculation worker: $reason to ${dateUtil.dateAndTimeAndSecondsString(end)}")

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
                    .setInputData(Data.Builder().putString(JOB, job).putInt(PASS, CalculationWorkflow.ProgressData.DRAW_BG.pass).build())
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
                    .setInputData(Data.Builder().putString(JOB, job).putInt(PASS, CalculationWorkflow.ProgressData.DRAW_TT.pass).build())
                    .build()
            )
            .then(
                if (activePlugin.activeSensitivity.isOref1)
                    OneTimeWorkRequest.Builder(IobCobOref1Worker::class.java)
                        .setInputData(dataWorkerStorage.storeInputData(IobCobOref1Worker.IobCobOref1WorkerData(injector, iobCobCalculator, reason, end, job == MAIN_CALCULATION, cause)))
                        .build()
                else
                    OneTimeWorkRequest.Builder(IobCobOrefWorker::class.java)
                        .setInputData(dataWorkerStorage.storeInputData(IobCobOrefWorker.IobCobOrefWorkerData(injector, iobCobCalculator, reason, end, job == MAIN_CALCULATION, cause)))
                        .build()
            )
            .then(OneTimeWorkRequest.Builder(UpdateIobCobSensWorker::class.java).build())
            .then(
                OneTimeWorkRequest.Builder(PrepareIobAutosensGraphDataWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PrepareIobAutosensGraphDataWorker.PrepareIobAutosensData(iobCobCalculator, overviewData)))
                    .build()
            )
            .then(
                runIf = job == MAIN_CALCULATION,
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .setInputData(Data.Builder().putString(JOB, job).putInt(PASS, CalculationWorkflow.ProgressData.DRAW_IOB.pass).build())
                    .build()
            )
            .then(
                runIf = job == MAIN_CALCULATION,
                OneTimeWorkRequest.Builder(InvokeLoopWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(InvokeLoopWorker.InvokeLoopData(cause)))
                    .build()
            )
            .then(
                runIf = job == MAIN_CALCULATION,
                OneTimeWorkRequest.Builder(UpdateWidgetWorker::class.java).build()
            )
            .then(
                runIf = job == MAIN_CALCULATION,
                OneTimeWorkRequest.Builder(PreparePredictionsWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PreparePredictionsWorker.PreparePredictionsData(overviewData)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .setInputData(Data.Builder().putString(JOB, job).putInt(PASS, CalculationWorkflow.ProgressData.DRAW_FINAL.pass).build())
                    .build()
            )
            .enqueue()
    }

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
                    .setInputData(Data.Builder().putInt(PASS, CalculationWorkflow.ProgressData.DRAW_FINAL.pass).build())
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
                    .setInputData(Data.Builder().putInt(PASS, CalculationWorkflow.ProgressData.DRAW_FINAL.pass).build())
                    .build()
            )
            .enqueue()
    }
}