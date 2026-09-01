package app.aaps.workflow

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter

/**
 * The input of one calculation chain.
 *
 * Top level rather than nested in a worker: they are plain data holders read by [WorkflowChainData],
 * which the workers only look up their slot in, so they belong beside it.
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
