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
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.MAIN_CALCULATION
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.UPDATE_PREDICTIONS
import app.aaps.core.utils.worker.then
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class CalculationWorkflowImpl @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val workflowChainData: WorkflowChainData,
    private val mainSignals: CalculationSignalsEmitter,
    // Lazy: breaks Dagger cycle OverviewDataCache → Loop → IobCobCalculator → CalculationWorkflow → OverviewDataCache.
    // Side methods that use mainCache run at runtime, never during construction.
    private val mainCacheProvider: Provider<OverviewDataCache>
) : CalculationWorkflow {

    private val mainCache: OverviewDataCache get() = mainCacheProvider.get()

    // Held across slot-write + WM enqueue so both systems agree on which call won. Without this,
    // two concurrent runCalculation/runOnReceivedPredictions threads can interleave so the slot
    // ends up with gen N but WM ends up running work tagged gen N-1 (because REPLACE honors call
    // order, not generation order). The worker's gen check then fails and the calculation is
    // silently dropped. Lock is held only across the enqueue itself — microseconds, no real
    // contention cost.
    private val enqueueLock = Any()

    init {
        // Verify definition
        var sumPercent = 0
        for (pass in CalculationWorkflow.ProgressData.entries) sumPercent += pass.percentOfTotal
        require(sumPercent == 100)
    }

    override fun stopCalculation(job: String, from: String) {
        aapsLogger.debug(LTag.WORKER, "Stopping calculation thread: $from")
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(job)
        val deadline = System.currentTimeMillis() + STOP_WAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val workStatus = workManager.getWorkInfosForUniqueWork(job).get()
            if (workStatus.isEmpty() || workStatus[0].state != WorkInfo.State.RUNNING) {
                aapsLogger.debug(LTag.WORKER, "Calculation thread stopped: $from")
                return
            }
            SystemClock.sleep(STOP_WAIT_POLL_MS)
        }
        aapsLogger.warn(LTag.WORKER, "Calculation thread did not stop within ${STOP_WAIT_TIMEOUT_MS}ms: $from")
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

        val isMain = job == MAIN_CALCULATION
        val prepare = PrepareGraphDataWorker.PrepareGraphData(
            iobCobCalculator = iobCobCalculator,
            overviewData = overviewData,
            cache = cache,
            signals = signals,
            reason = reason,
            end = end,
            bgDataReload = bgDataReload,
            limitDataToOldestAvailable = isMain,
            triggeredByNewBG = triggeredByNewBG,
            // HISTORY ends here, so emit DRAW_FINAL inline. MAIN delegates to PostCalculationWorker.
            emitFinalProgress = !isMain
        )
        synchronized(enqueueLock) {
            val generation = if (isMain) {
                val post = PostCalculationWorker.PostCalculationData(
                    overviewData = overviewData,
                    cache = cache,
                    signals = signals,
                    triggeredByNewBG = triggeredByNewBG,
                    runLoopAndWidgetPhase = true
                )
                workflowChainData.startMain(prepare, post)
            } else {
                workflowChainData.startHistory(prepare)
            }

            val jobData = dataForJob(job, generation)
            WorkManager.getInstance(context)
                .beginUniqueWork(
                    job, ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequest.Builder(PrepareGraphDataWorker::class.java)
                        .setInputData(jobData)
                        .build()
                )
                .then(
                    runIf = isMain,
                    OneTimeWorkRequest.Builder(PostCalculationWorker::class.java)
                        .setInputData(jobData)
                        .build()
                )
                .enqueue()
        }
    }

    override fun runOnReceivedPredictions(overviewData: OverviewData) {
        aapsLogger.debug(LTag.WORKER, "Starting updateReceivedPredictions worker")

        synchronized(enqueueLock) {
            val generation = workflowChainData.startPredictions(
                PostCalculationWorker.PostCalculationData(
                    overviewData = overviewData,
                    cache = mainCache,
                    signals = mainSignals,
                    triggeredByNewBG = false,
                    runLoopAndWidgetPhase = false
                )
            )

            WorkManager.getInstance(context).enqueueUniqueWork(
                UPDATE_PREDICTIONS, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(PostCalculationWorker::class.java)
                    .setInputData(dataForJob(UPDATE_PREDICTIONS, generation))
                    .build()
            )
        }
    }

    private fun dataForJob(job: String, generation: Long): Data =
        Data.Builder()
            .putString(WorkflowChainData.JOB_KEY, job)
            .putLong(WorkflowChainData.GEN_KEY, generation)
            .build()

    companion object {

        private const val STOP_WAIT_TIMEOUT_MS = 5_000L
        private const val STOP_WAIT_POLL_MS = 100L
    }
}
