package app.aaps.shared.clientbindings

import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.implementation.notifications.CommonNotificationManager
import app.aaps.implementation.resources.GeneratedTextResolver
import app.aaps.implementation.resources.isCompactScreen
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.objectives.objectives.DurationText
import app.aaps.plugins.constraints.objectives.objectives.Objective
import app.aaps.plugins.constraints.objectives.objectives.PlainDurationText
import app.aaps.plugins.main.iob.iobCobCalculator.IobCobCalculatorPlugin
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.ws.NsConnection
import app.aaps.plugins.sync.nsclientV3.ws.SocketNsConnection
import app.aaps.shared.impl.logging.LImpl
import app.aaps.shared.impl.rx.bus.RxBusImpl
import app.aaps.workflow.CalculationExecutor
import app.aaps.workflow.LazyCalculationExecutor
import app.aaps.workflow.PostCalculationRunner
import app.aaps.workflow.PrepareGraphDataRunner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The bindings iOS and desktop both need, said once.
 *
 * Every provider here is **real AAPS code, not a placeholder**. They exist because the classes
 * behind them are built by hand rather than by annotation, so each graph has to say so: the
 * calculator is deliberately built twice at different scopes, the Nightscout plugin defeats the
 * annotation processor, and the objectives plugin carries a qualifier that would leak into the
 * interface.
 *
 * Android says the same things in `:app`. This is the copy for the platforms that are not Android.
 *
 * ## What is here and what is not
 *
 * Here: anything whose construction is identical off Android. Not here: anything a platform has to
 * answer for itself - the logger, the preference store, the config, the system notification
 * platform, the date formatter. Those stay in each shell's own container, and the providers below
 * simply depend on them.
 *
 * Include it by name from the graph factory, next to `CoreObjectsGraph`. It is deliberately not
 * contributed - see the module KDoc.
 */
@BindingContainer
object ClientGraphBindings {

    // ---- Core objects with no platform half ----

    /**
     * The app-lifetime scope. `Dispatchers.Default` rather than a UI one: nothing here draws, and
     * work that must touch the UI hops to it itself.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @SingleIn(AppScope::class)
    @ApplicationScope
    fun qualifiedAppScope(scope: CoroutineScope): CoroutineScope = scope

    @Provides
    @SingleIn(AppScope::class)
    fun rxBus(logger: AAPSLogger): RxBus = RxBusImpl(logger)

    @Provides
    @SingleIn(AppScope::class)
    fun l(preferences: Preferences): L = LImpl { preferences }

    /**
     * Text from the generated English string maps.
     *
     * The owners have to be registered before anything asks for text; each shell does that at start
     * up from its own generated `GeneratedStringOwners`, which is per shell because the object is
     * generated into the shell's own package.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun textResolver(): TextResolver = GeneratedTextResolver(compactScreen = isCompactScreen())

    /** The shared registry decides what exists; each platform's own class only shows it. */
    @Provides
    @SingleIn(AppScope::class)
    fun notificationManager(
        logger: AAPSLogger,
        textResolver: TextResolver,
        platform: SystemNotificationPlatform,
        scope: CoroutineScope
    ): NotificationManager = CommonNotificationManager(logger, textResolver, platform, scope)

    // ---- Plugins that cannot annotate themselves ----

    /**
     * The live loop's calculator.
     *
     * Built here rather than annotated for the same reason as on Android: the class is constructed
     * twice on purpose, once for the live loop and once for a history browser at its own scope, so a
     * `@SingleIn` on the class would hide which one a window is looking at.
     *
     * The calculator and the cache need each other; a `() -> T` defers that lookup so the graph
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
        cache: () -> OverviewDataCache
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

    /** The ten objectives in order, for `ObjectivesPlugin` to read. */
    @Provides
    fun objectivesList(objectives: Map<Int, Objective>): List<Objective> =
        objectives.toList().sortedBy { it.first }.map { it.second }

    /**
     * The `Objectives` interface, unqualified.
     *
     * `ObjectivesPlugin` carries `@APS` on the class for the plugin-list multibinding, and a second
     * `@ContributesBinding` there would inherit it - the interface would then only be readable as
     * `@APS Objectives`, which is not what a reader asks for.
     *
     * Metro documents the way out: put the qualifier on the bound type instead, `binding<@APS
     * PluginBase>()`. The version pinned here rejects that form outright -
     * `Inapplicable candidate(s): constructor(scope: KClass<*>, binding: binding<*> = ...)` - which is
     * the same wall `SyncPluginsBindings` hits for its qualified entry. So this stays stated, and
     * hands out the same scoped instance either way. Retry both when Metro leaves the snapshot.
     */
    @Provides
    fun objectives(plugin: ObjectivesPlugin): Objectives = plugin

    /**
     * Durations without plural forms.
     *
     * Android has real plural resources and keeps them; nothing off Android has a plural table, so
     * both platforms here say it plainly.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun durationText(): DurationText = PlainDurationText()

    // ---- Things a service would own on Android ----

    /**
     * The Nightscout websocket.
     *
     * `SocketNsConnection` is owned by a scope rather than by a service, which is the situation on
     * both platforms here. Android keeps its service-backed connection for the wake lock.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun nsConnection(connection: SocketNsConnection): NsConnection = connection

    /**
     * The calculation executor, deferred so the graph accepts the runner cycle.
     *
     * See [LazyCalculationExecutor] for why the cycle exists here and not on Android: WorkManager
     * builds the runners itself there, so they are not graph nodes at all.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun calculationExecutor(
        @ApplicationScope scope: CoroutineScope,
        aapsLogger: AAPSLogger,
        prepare: () -> PrepareGraphDataRunner,
        post: () -> PostCalculationRunner
    ): CalculationExecutor = LazyCalculationExecutor(scope, aapsLogger, { prepare() }, { post() })

    /** Bolus progress. Stated rather than annotated, for the same reason as the calculator. */
    @Provides
    @SingleIn(AppScope::class)
    fun bolusProgressData(
        concentrationHelper: ConcentrationHelper,
        @ApplicationScope scope: CoroutineScope
    ): BolusProgressData = BolusProgressData(concentrationHelper, scope)

    /**
     * The pump communication banner, built by hand exactly as the Android activity builds it.
     *
     * A plain class in `:core:ui` rather than a binding, so every host constructs its own. The
     * application scope is the right lifetime here: the window lives as long as the process.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun pumpCommunicationStatus(
        rxBus: RxBus,
        commandQueue: CommandQueue,
        rh: TextResolver,
        @ApplicationScope scope: CoroutineScope
    ): PumpCommunicationStatus = PumpCommunicationStatus(rxBus, commandQueue, rh, scope)
}
