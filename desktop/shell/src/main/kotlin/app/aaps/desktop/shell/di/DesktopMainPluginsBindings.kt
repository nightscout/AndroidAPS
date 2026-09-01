package app.aaps.desktop.shell.di

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.main.iob.iobCobCalculator.IobCobCalculatorPlugin
import app.aaps.workflow.CalculationExecutor
import app.aaps.workflow.LazyCalculationExecutor
import app.aaps.workflow.PostCalculationRunner
import app.aaps.workflow.PrepareGraphDataRunner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

/**
 * Shared AAPS classes that carry no DI annotations, stated for the desktop graph.
 *
 * The desktop counterpart of `MainPluginsBindings` in `:app` and `IosMainPluginsBindings` in
 * `:ios:shell`, and it is deliberately a near copy of the Apple one - the same classes need the same
 * saying-so on any platform that is not Android.
 *
 * Nothing here is a placeholder. These are the real AAPS implementations, and they were only missing
 * because the classes are built by hand rather than by annotation: `IobCobCalculatorPlugin` is built
 * twice on purpose, once for the live loop and once for a history browser at its own scope, so a
 * `@SingleIn` on the class itself would hide which one a window is looking at.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object DesktopMainPluginsBindings {

    /**
     * The live loop's calculator.
     *
     * The calculator and the cache need each other; [Provider] defers that lookup so the graph
     * accepts what a direct reference would reject as a cycle.
     */
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

    @Provides
    @IntoMap
    @IntKey(10)
    fun iobCobCalculatorEntry(plugin: IobCobCalculatorPlugin): PluginBase = plugin

    /** Bolus progress. Stated rather than annotated, for the same reason as the calculator. */
    @Provides
    @SingleIn(AppScope::class)
    fun bolusProgressData(
        concentrationHelper: ConcentrationHelper,
        @ApplicationScope scope: CoroutineScope
    ): BolusProgressData = BolusProgressData(concentrationHelper, scope)

    /**
     * The calculation executor, deferred so the graph accepts the runner cycle.
     *
     * See [LazyCalculationExecutor] for why the cycle exists here and not on Android. It lives in
     * `:workflow` because iOS needs exactly the same thing for exactly the same reason.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun calculationExecutor(
        @ApplicationScope scope: CoroutineScope,
        aapsLogger: AAPSLogger,
        prepare: Provider<PrepareGraphDataRunner>,
        post: Provider<PostCalculationRunner>
    ): CalculationExecutor = LazyCalculationExecutor(scope, aapsLogger, { prepare() }, { post() })
}
