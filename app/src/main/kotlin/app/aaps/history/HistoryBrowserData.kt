package app.aaps.history

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.di.metro.MetroGraphs
import app.aaps.ui.compose.history.HistoryScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The calculation objects behind the History Browser.
 *
 * These are deliberately **not** the app's singletons. The History Browser recalculates over a
 * different time range, and sharing the live loop's objects would mean browsing history rewrites the
 * state the loop is running on.
 *
 * That isolation used to be arranged by hand: this class took fourteen dependencies, passed most of
 * them straight through, and constructed four objects itself. Now it asks for one history window and
 * hands back what the window contains. `HistoryWindowGraph` owns the wiring, `MetroScopingTest` checks
 * the isolation those objects rely on, and the cycle between the cache and the calculator is expressed
 * with Metro's `Provider` rather than a hand-written lambda.
 */
@Singleton
class HistoryBrowserData @Inject constructor(
    metroGraphs: MetroGraphs
) : HistoryScope {

    private val window = metroGraphs.newHistoryWindow()

    override val overviewData: OverviewData = window.overviewData
    override val signals: CalculationSignalsEmitter = window.signals
    override val cache: OverviewDataCache = window.cache
    override val iobCobCalculator: IobCobCalculator = window.iobCobCalculator

    override fun onDestroy() {
    }
}
