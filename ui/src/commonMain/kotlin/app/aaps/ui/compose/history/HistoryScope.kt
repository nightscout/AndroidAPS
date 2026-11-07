package app.aaps.ui.compose.history

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter

/**
 * Surface the history-browser ViewModel consumes — isolates the
 * `HistoryViewModel` (and thus everything in `:ui`) from the concrete
 * `HistoryBrowserData` wiring (which lives in `:app` because it
 * instantiates `plugins:main` types).
 */
interface HistoryScope {

    val overviewData: OverviewData
    val signals: CalculationSignalsEmitter
    val cache: OverviewDataCache
    val iobCobCalculator: IobCobCalculator

    fun onDestroy()
}
