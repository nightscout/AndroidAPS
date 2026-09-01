package app.aaps.ios.shell.platform

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.ios.shell.di.IosHistoryWindowGraph
import app.aaps.ui.compose.history.HistoryScope

/**
 * The calculation objects behind the History Browser on iOS, the counterpart of `HistoryBrowserData`.
 *
 * These are deliberately **not** the app's singletons. The History Browser recalculates over a
 * different time range, and sharing the running loop's objects would mean browsing history rewrites
 * the state the loop is calculating on.
 *
 * This replaced a placeholder that threw on every property. That was the right placeholder - handing
 * back the app-scoped objects would have satisfied the types and reintroduced exactly that bug,
 * invisibly - but it meant the history screen could not open at all. Now there is a real window to
 * hand back: [IosHistoryWindowGraph] is a graph extension with its own scope, so each window owns its
 * own calculator, cache, signals and overview data while still sharing the database and preferences
 * with the app.
 */
class IosHistoryScope(window: IosHistoryWindowGraph) : HistoryScope {

    override val overviewData: OverviewData = window.overviewData
    override val signals: CalculationSignalsEmitter = window.signals
    override val cache: OverviewDataCache = window.cache
    override val iobCobCalculator: IobCobCalculator = window.iobCobCalculator

    /**
     * Nothing to release. The window's objects are held only by this instance, so they go when it
     * does; the Android counterpart is empty for the same reason.
     */
    override fun onDestroy() {
    }
}
