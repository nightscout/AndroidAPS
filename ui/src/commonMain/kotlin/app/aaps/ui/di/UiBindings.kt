package app.aaps.ui.di

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.workflow.CalculationSignals
import app.aaps.ui.compose.overview.OverviewDataCacheFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The live overview cache - the one that observes the database.
 *
 * The history window builds its own with `observeDatabase = false` in `HistoryWindowGraph`; that
 * graph declares its own binding, which shadows this one.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object UiBindings {

    /**
     * The calculator arrives as a lambda to break the cycle
     * `IobCobCalculator -> CalculationWorkflow -> OverviewDataCache -> IobCobCalculator`. It is only
     * resolved on demand, on the database-observing paths.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun liveOverviewDataCache(
        factory: OverviewDataCacheFactory,
        iobCobCalculator: () -> IobCobCalculator,
        signals: CalculationSignals
    ): OverviewDataCache = factory.create({ iobCobCalculator() }, signals, observeDatabase = true)
}
