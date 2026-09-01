package app.aaps.history

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.di.metro.HistoryWindowGraph
import app.aaps.ui.compose.history.HistoryScope

/**
 * The calculation objects behind the History Browser.
 *
 * These are deliberately **not** the app's singletons. The History Browser recalculates over a
 * different time range, and sharing the live loop's objects would mean browsing history rewrites the
 * state the loop is running on.
 *
 * It asks for one history window and hands back what the window contains. `HistoryWindowGraph` owns
 * the wiring, `MetroScopingTest` checks the isolation those objects rely on, and the cycle between the
 * cache and the calculator is expressed with Metro's `Provider` rather than a hand-written lambda.
 */
/*
 * Takes the window itself rather than `MetroGraphs`: asking the graph holder for one would make a
 * domain class depend on the DI plumbing to reach a single object. `AppAndroidBindings` calls the
 * extension factory instead, and the scope lives on that provider.
 */
class HistoryBrowserData(
    window: HistoryWindowGraph
) : HistoryScope {

    override val overviewData: OverviewData = window.overviewData
    override val signals: CalculationSignalsEmitter = window.signals
    override val cache: OverviewDataCache = window.cache
    override val iobCobCalculator: IobCobCalculator = window.iobCobCalculator

    override fun onDestroy() {
    }
}
