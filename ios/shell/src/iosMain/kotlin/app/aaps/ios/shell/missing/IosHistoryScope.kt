package app.aaps.ios.shell.missing

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.ui.compose.history.HistoryScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Placeholder for the history browser, and the one place here where the easy shortcut would be a
 * real bug rather than a small lie.
 *
 * A history window recalculates over a **different time range** than the running loop, so it needs
 * its own `OverviewData`, its own signals, its own cache and its own calculator. On Android that is
 * `HistoryWindowGraph`, a graph extension with a scope of its own, and its whole reason for existing
 * is to stop history browsing writing into the state the loop is running on.
 *
 * Handing back the app-scoped objects here would satisfy the type and reintroduce exactly that bug,
 * invisibly. So this refuses instead: opening the history browser on iOS throws, with a message
 * saying why, until iOS has a graph extension of its own.
 *
 * That is a screen that does not open. The alternative is a screen that opens and quietly corrupts
 * the loop's calculations.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosHistoryScope @Inject constructor(
    private val aapsLogger: AAPSLogger
) : HistoryScope {

    override val overviewData: OverviewData get() = refuse()
    override val signals: CalculationSignalsEmitter get() = refuse()
    override val cache: OverviewDataCache get() = refuse()
    override val iobCobCalculator: IobCobCalculator get() = refuse()

    override fun onDestroy() = aapsLogger.notOnIosYet("HistoryScope.onDestroy")

    private fun refuse(): Nothing = aapsLogger.failNotOnIosYet(
        "HistoryScope needs a scope of its own on iOS - sharing the loop's calculation state would corrupt it"
    )
}
