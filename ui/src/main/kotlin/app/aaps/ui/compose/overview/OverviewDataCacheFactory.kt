package app.aaps.ui.compose.overview

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.workflow.CalculationSignals
import dagger.assisted.AssistedFactory

/**
 * Factory for per-scope [OverviewDataCacheImpl] instances.
 *
 * Each scope (live overview, history browser) owns its own cache wired
 * to its own [IobCobCalculator] and [CalculationSignals].
 *
 * @param observeDatabase true for the live pipeline (cache reacts to DB changes),
 *   false for history (cache is populated only by workers through [CalculationSignals]).
 */
// Stays on Dagger on purpose: this is an AapsLeaves leaf, so Dagger owns it and Metro borrows it. A
// Dagger @AssistedFactory can only build a target that has a Dagger @AssistedInject constructor,
// which is why OverviewDataCacheImpl stays on Dagger with it.
@AssistedFactory
interface OverviewDataCacheFactory {

    fun create(
        iobCobCalculatorProvider: () -> IobCobCalculator,
        signals: CalculationSignals,
        observeDatabase: Boolean
    ): OverviewDataCacheImpl
}
