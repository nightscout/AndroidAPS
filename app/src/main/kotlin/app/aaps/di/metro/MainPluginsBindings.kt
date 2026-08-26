package app.aaps.di.metro

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.main.iob.iobCobCalculator.IobCobCalculatorPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The live loop's `IobCobCalculatorPlugin`, replacing the hand-construction in `MainPluginsModule`.
 *
 * `:plugins:main` is multiplatform, so the plugin carries no DI annotation of its own and something has
 * to assemble it. That used to be Dagger, here it is Metro, and this file is the only thing about the
 * plugin that lives in `:app`.
 *
 * ## Why the class is not simply annotated
 *
 * `VirtualPumpPlugin` took `@Inject` and `@ContributesIntoMap` directly, which is tidier. This one
 * cannot: it is built **twice**, on purpose. `HistoryWindowGraph` builds a second one at
 * `HistoryWindowScope` over a fixed time range, so the history browser can calculate without disturbing
 * the live loop. A `@SingleIn(AppScope::class)` on the class would put a binding for the same type in
 * the parent of that extension, and which one a window saw would stop being obvious. Each graph naming
 * its own is what keeps the two apart - the shape `HistoryWindowGraph` already uses.
 *
 * ## The cycle
 *
 * The calculator and the cache need each other. [Provider] defers the lookup, so the graph accepts here
 * what a direct reference would reject, and `runCalculation` only ever runs after construction. The
 * plugin takes a plain lambda rather than a [Provider] because it is common code and Metro's own
 * `Provider` would be fine there, but the class predates this and the lambda costs nothing to adapt.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object MainPluginsBindings {

    @Suppress("LongParameterList")
    @Provides
    @SingleIn(AppScope::class)
    fun iobCobCalculatorPlugin(
        aapsLogger: AAPSLogger,
        rxBus: RxBus,
        preferences: Preferences,
        rh: TextResolver,
        profileFunction: ProfileFunction,
        activePlugin: ActivePlugin,
        dateUtil: DateUtil,
        persistenceLayer: PersistenceLayer,
        overviewData: OverviewData,
        calculationWorkflow: CalculationWorkflow,
        decimalFormatter: DecimalFormatter,
        processedTbrEbData: ProcessedTbrEbData,
        signals: CalculationSignalsEmitter,
        cache: Provider<OverviewDataCache>
    ): IobCobCalculatorPlugin = IobCobCalculatorPlugin(
        aapsLogger, rxBus, preferences, rh, profileFunction, activePlugin, dateUtil, persistenceLayer,
        overviewData, calculationWorkflow, decimalFormatter, processedTbrEbData, signals
    ) { cache() }

    @Provides
    fun iobCobCalculator(plugin: IobCobCalculatorPlugin): IobCobCalculator = plugin

    /**
     * Order key 10, as under Dagger.
     *
     * Unqualified, though the Dagger binding said `@AllConfigs`: `:app` merges this bucket
     * unconditionally, which is what that qualifier meant, and nothing reads a Metro `@AllConfigs` map -
     * so using it would drop the plugin from the list with no error at all.
     */
    @Provides
    @IntoMap
    @IntKey(10)
    fun iobCobCalculatorEntry(plugin: IobCobCalculatorPlugin): PluginBase = plugin
}
