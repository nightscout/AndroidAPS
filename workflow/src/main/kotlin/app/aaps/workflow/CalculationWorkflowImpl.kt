package app.aaps.workflow

import android.content.Context
import android.os.SystemClock
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.MAIN_CALCULATION
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.UPDATE_PREDICTIONS
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.core.utils.worker.then
import app.aaps.workflow.iob.IobCobOref1Worker
import app.aaps.workflow.iob.IobCobOrefWorker
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class CalculationWorkflowImpl @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val dataWorkerStorage: DataWorkerStorage,
    private val activePlugin: ActivePlugin,
    private val mainSignals: CalculationSignalsEmitter,
    // Lazy: breaks Dagger cycle OverviewDataCache → Loop → IobCobCalculator → CalculationWorkflow → OverviewDataCache.
    // Side methods that use mainCache run at runtime, never during construction.
    private val mainCacheProvider: Provider<OverviewDataCache>
) : CalculationWorkflow {

    private val mainCache: OverviewDataCache get() = mainCacheProvider.get()

    init {
        // Verify definition
        var sumPercent = 0
        for (pass in CalculationWorkflow.ProgressData.entries) sumPercent += pass.percentOfTotal
        require(sumPercent == 100)
    }

    override fun stopCalculation(job: String, from: String) {
        aapsLogger.debug(LTag.WORKER, "Stopping calculation thread: $from")
        WorkManager.getInstance(context).cancelUniqueWork(job)
        val workStatus = WorkManager.getInstance(context).getWorkInfosForUniqueWork(job).get()
        while (workStatus.isNotEmpty() && workStatus[0].state == WorkInfo.State.RUNNING)
            SystemClock.sleep(100)
        aapsLogger.debug(LTag.WORKER, "Calculation thread stopped: $from")
    }

    override fun runCalculation(
        job: String,
        iobCobCalculator: IobCobCalculator,
        overviewData: OverviewData,
        cache: OverviewDataCache,
        signals: CalculationSignalsEmitter,
        reason: String,
        end: Long,
        bgDataReload: Boolean,
        triggeredByNewBG: Boolean
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
                    .setInputData(dataWorkerStorage.storeInputData(PrepareBucketedDataWorker.PrepareBucketedData(iobCobCalculator, overviewData, cache)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareBgDataWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PrepareBgDataWorker.PrepareBgData(iobCobCalculator, overviewData, cache)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(UpdateGraphWorker.UpdateGraphData(signals, CalculationWorkflow.ProgressData.DRAW_BG)))
                    .build()
            )
            .then(
                if (activePlugin.activeSensitivity.isOref1)
                    OneTimeWorkRequest.Builder(IobCobOref1Worker::class.java)
                        .setInputData(dataWorkerStorage.storeInputData(IobCobOref1Worker.IobCobOref1WorkerData(iobCobCalculator, signals, reason, end, job == MAIN_CALCULATION, triggeredByNewBG)))
                        .build()
                else
                    OneTimeWorkRequest.Builder(IobCobOrefWorker::class.java)
                        .setInputData(dataWorkerStorage.storeInputData(IobCobOrefWorker.IobCobOrefWorkerData(iobCobCalculator, signals, reason, end, job == MAIN_CALCULATION, triggeredByNewBG)))
                        .build()
            )
            .then(OneTimeWorkRequest.Builder(UpdateIobCobSensWorker::class.java).build())
            .then(
                OneTimeWorkRequest.Builder(PrepareIobAutosensGraphDataWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PrepareIobAutosensGraphDataWorker.PrepareIobAutosensData(iobCobCalculator, overviewData, cache, signals)))
                    .build()
            )
            .then(
                runIf = job == MAIN_CALCULATION,
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(UpdateGraphWorker.UpdateGraphData(signals, CalculationWorkflow.ProgressData.DRAW_IOB)))
                    .build()
            )
            .then(
                runIf = job == MAIN_CALCULATION,
                OneTimeWorkRequest.Builder(InvokeLoopWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(InvokeLoopWorker.InvokeLoopData(triggeredByNewBG)))
                    .build()
            )
            .then(
                runIf = job == MAIN_CALCULATION,
                OneTimeWorkRequest.Builder(UpdateWidgetWorker::class.java).build()
            )
            .then(
                runIf = job == MAIN_CALCULATION,
                OneTimeWorkRequest.Builder(PreparePredictionsWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PreparePredictionsWorker.PreparePredictionsData(overviewData, cache)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(UpdateGraphWorker.UpdateGraphData(signals, CalculationWorkflow.ProgressData.DRAW_FINAL)))
                    .build()
            )
            .enqueue()
    }

    override fun runOnReceivedPredictions(
        overviewData: OverviewData
    ) {
        aapsLogger.debug(LTag.WORKER, "Starting updateReceivedPredictions worker")

        WorkManager.getInstance(context)
            .beginUniqueWork(
                UPDATE_PREDICTIONS, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(PreparePredictionsWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PreparePredictionsWorker.PreparePredictionsData(overviewData, mainCache)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(UpdateGraphWorker.UpdateGraphData(mainSignals, CalculationWorkflow.ProgressData.DRAW_FINAL)))
                    .build()
            )
            .enqueue()
    }

    override fun runOnScaleChanged(iobCobCalculator: IobCobCalculator, overviewData: OverviewData) {
        WorkManager.getInstance(context)
            .beginUniqueWork(
                MAIN_CALCULATION, ExistingWorkPolicy.APPEND,
                OneTimeWorkRequest.Builder(PrepareBucketedDataWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PrepareBucketedDataWorker.PrepareBucketedData(iobCobCalculator, overviewData, mainCache)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareBgDataWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PrepareBgDataWorker.PrepareBgData(iobCobCalculator, overviewData, mainCache)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(UpdateGraphWorker.UpdateGraphData(mainSignals, CalculationWorkflow.ProgressData.DRAW_FINAL)))
                    .build()
            )
            .enqueue()
    }
}