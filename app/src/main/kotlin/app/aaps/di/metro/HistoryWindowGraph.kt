package app.aaps.di.metro

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.CalculationSignalsImpl
import app.aaps.implementation.overview.OverviewDataImpl
import app.aaps.plugins.main.iob.iobCobCalculator.IobCobCalculatorPlugin
import app.aaps.ui.compose.overview.OverviewDataCacheFactory
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Marks objects that belong to one history browsing window rather than to the running app.
 *
 * A separate scope, not [dev.zacsweers.metro.AppScope], because that is the whole point: the History
 * Browser recalculates over a different time range, and it must not touch the loop's own calculation
 * state while doing it.
 */
abstract class HistoryWindowScope private constructor()

/**
 * One history browsing window's calculation objects.
 *
 * The History Browser needs its own [OverviewData], [CalculationSignalsEmitter], [OverviewDataCache]
 * and [IobCobCalculator], while still sharing the app's logger, database and preferences. Before this
 * graph existed, `HistoryBrowserData` got that by taking fourteen dependencies it mostly passed
 * straight through, and constructing the four objects by hand.
 *
 * The isolation is what matters here and it is checked in `MetroScopingTest`: each graph instance owns
 * its `@SingleIn(HistoryWindowScope::class)` objects, so a second window cannot reach into the first
 * or into the live loop. Note the test also shows why the obvious shortcut is wrong - two *root*
 * graphs share nothing at all, which would hand the history window its own logger and its own database
 * handle. Shared leaves have to come from outside, which for now means from Dagger through
 * [DeferredRef], and later from a parent graph via `@GraphExtension`.
 *
 * [ActivePlugin] and [CalculationWorkflow] below are why the leaves are deferred rather than passed as
 * instances: `ActivePlugin` leads to the plugin list, which asks the Metro graphs, which is the
 * re-entrant cycle that crashed all three spike branches.
 */
@DependencyGraph(HistoryWindowScope::class)
interface HistoryWindowGraph {

    val overviewData: OverviewData
    val signals: CalculationSignalsEmitter
    val cache: OverviewDataCache
    val iobCobCalculator: IobCobCalculator

    @DependencyGraph.Factory
    fun interface Factory {

        @Suppress("LongParameterList")
        fun create(
            @Provides aapsLoggerRef: DeferredRef<AAPSLogger>,
            @Provides rxBusRef: DeferredRef<RxBus>,
            @Provides preferencesRef: DeferredRef<Preferences>,
            @Provides rhRef: DeferredRef<TextResolver>,
            @Provides profileFunctionRef: DeferredRef<ProfileFunction>,
            @Provides activePluginRef: DeferredRef<ActivePlugin>,
            @Provides dateUtilRef: DeferredRef<DateUtil>,
            @Provides persistenceLayerRef: DeferredRef<PersistenceLayer>,
            @Provides calculationWorkflowRef: DeferredRef<CalculationWorkflow>,
            @Provides decimalFormatterRef: DeferredRef<DecimalFormatter>,
            @Provides processedTbrEbDataRef: DeferredRef<ProcessedTbrEbData>,
            @Provides cacheFactoryRef: DeferredRef<OverviewDataCacheFactory>
        ): HistoryWindowGraph
    }

    @Provides fun aapsLogger(r: DeferredRef<AAPSLogger>): AAPSLogger = r.get()
    @Provides fun rxBus(r: DeferredRef<RxBus>): RxBus = r.get()
    @Provides fun preferences(r: DeferredRef<Preferences>): Preferences = r.get()
    @Provides fun rh(r: DeferredRef<TextResolver>): TextResolver = r.get()
    @Provides fun profileFunction(r: DeferredRef<ProfileFunction>): ProfileFunction = r.get()
    @Provides fun activePlugin(r: DeferredRef<ActivePlugin>): ActivePlugin = r.get()
    @Provides fun dateUtil(r: DeferredRef<DateUtil>): DateUtil = r.get()
    @Provides fun persistenceLayer(r: DeferredRef<PersistenceLayer>): PersistenceLayer = r.get()
    @Provides fun calculationWorkflow(r: DeferredRef<CalculationWorkflow>): CalculationWorkflow = r.get()
    @Provides fun decimalFormatter(r: DeferredRef<DecimalFormatter>): DecimalFormatter = r.get()
    @Provides fun processedTbrEbData(r: DeferredRef<ProcessedTbrEbData>): ProcessedTbrEbData = r.get()
    @Provides fun cacheFactory(r: DeferredRef<OverviewDataCacheFactory>): OverviewDataCacheFactory = r.get()

    @SingleIn(HistoryWindowScope::class)
    @Provides
    fun provideOverviewData(): OverviewData = OverviewDataImpl()

    @SingleIn(HistoryWindowScope::class)
    @Provides
    fun provideSignals(): CalculationSignalsEmitter = CalculationSignalsImpl()

    /**
     * The cache and the calculator need each other. Metro's [Provider] is a deferred lookup, so the
     * graph accepts it where a direct reference would be a cycle error - the same shape used for
     * `QuickWizard` in `CoreObjectsGraph`, instead of the hand-written lambda this replaces.
     */
    @SingleIn(HistoryWindowScope::class)
    @Provides
    fun provideCache(
        factory: OverviewDataCacheFactory,
        signals: CalculationSignalsEmitter,
        iobCobCalculator: Provider<IobCobCalculator>
    ): OverviewDataCache = factory.create(
        iobCobCalculatorProvider = { iobCobCalculator() },
        signals = signals,
        // A history window reads a fixed range. Following live database changes is the live loop's job.
        observeDatabase = false
    )

    @Suppress("LongParameterList")
    @SingleIn(HistoryWindowScope::class)
    @Provides
    fun provideIobCobCalculator(
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
    ): IobCobCalculator = IobCobCalculatorPlugin(
        aapsLogger, rxBus, preferences, rh, profileFunction, activePlugin, dateUtil, persistenceLayer,
        overviewData, calculationWorkflow, decimalFormatter, processedTbrEbData, signals
    ) { cache() }
}
