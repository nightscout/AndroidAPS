package app.aaps.workflow

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
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Decides what each calculation run contains; [CalculationExecutor] decides how it is run.
 *
 * Everything here is plain Kotlin, so the chain is the same on every platform - only the executor
 * behind it differs.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class CalculationWorkflowImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val workflowChainData: WorkflowChainData,
    private val executor: CalculationExecutor,
    private val mainSignals: CalculationSignalsEmitter,
    // A plain factory, not a Provider: breaks the cycle OverviewDataCache → Loop → IobCobCalculator → CalculationWorkflow → OverviewDataCache.
    // Side methods that use mainCache run at runtime, never during construction.
    private val mainCacheProvider: () -> OverviewDataCache
) : CalculationWorkflow {

    private val mainCache: OverviewDataCache get() = mainCacheProvider()

    // Held across slot-write + start so both agree on which call won. Without it, two concurrent
    // runCalculation/runOnReceivedPredictions callers can interleave so the slot ends up with
    // generation N while the executor runs the phases tagged N-1 (replace honours call order, not
    // generation order). The phase's generation check then fails and the calculation is silently
    // dropped. A coroutine Mutex rather than a lock: `synchronized` is JVM only, and it is held for
    // microseconds.
    private val startMutex = Mutex()

    init {
        // Verify definition
        var sumPercent = 0
        for (pass in CalculationWorkflow.ProgressData.entries) sumPercent += pass.percentOfTotal
        require(sumPercent == 100)
    }

    override suspend fun stopCalculation(job: String, from: String) = executor.stop(job, from)

    override suspend fun waitForCalculationFinish(job: String, reason: String) = executor.waitForPrepare(job, reason)

    override suspend fun runCalculation(
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
        val prepare = PrepareGraphData(
            iobCobCalculator = iobCobCalculator,
            overviewData = overviewData,
            cache = cache,
            signals = signals,
            reason = reason,
            end = end,
            bgDataReload = bgDataReload,
            limitDataToOldestAvailable = isMain,
            triggeredByNewBG = triggeredByNewBG,
            // HISTORY ends here, so emit DRAW_FINAL inline. MAIN delegates to the post phase.
            emitFinalProgress = !isMain
        )
        startMutex.withLock {
            val generation = if (isMain) {
                val post = PostCalculationData(
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
            executor.start(job, generation, runPost = isMain)
        }
    }

    override suspend fun runOnReceivedPredictions(overviewData: OverviewData) {
        aapsLogger.debug(LTag.WORKER, "Starting updateReceivedPredictions worker")

        startMutex.withLock {
            val generation = workflowChainData.startPredictions(
                PostCalculationData(
                    overviewData = overviewData,
                    cache = mainCache,
                    signals = mainSignals,
                    triggeredByNewBG = false,
                    runLoopAndWidgetPhase = false
                )
            )
            executor.startPostOnly(UPDATE_PREDICTIONS, generation)
        }
    }
}
