package app.aaps.workflow

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter

/**
 * The input of one calculation chain.
 *
 * These used to be nested in the two WorkManager workers, which put a plain data holder behind an
 * Android class. They are read by [WorkflowChainData], which the workers only look up their slot in,
 * so they belong beside it rather than inside a worker.
 */
class PrepareGraphData(
    val iobCobCalculator: IobCobCalculator, // cannot be injected : HistoryBrowser uses different instance
    val overviewData: OverviewData,
    val cache: OverviewDataCache,
    val signals: CalculationSignalsEmitter,
    val reason: String,
    val end: Long,
    val bgDataReload: Boolean,
    val limitDataToOldestAvailable: Boolean,
    val triggeredByNewBG: Boolean,
    val emitFinalProgress: Boolean
)

/** The input of the phase that runs after [PrepareGraphData]. */
class PostCalculationData(
    val overviewData: OverviewData,
    val cache: OverviewDataCache,
    val signals: CalculationSignalsEmitter,
    val triggeredByNewBG: Boolean,
    val runLoopAndWidgetPhase: Boolean
)
