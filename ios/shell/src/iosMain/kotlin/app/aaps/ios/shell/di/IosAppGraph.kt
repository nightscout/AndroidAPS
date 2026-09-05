package app.aaps.ios.shell.di

import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.interfaces.ui.UrlOpener
import app.aaps.implementation.maintenance.cloud.AuthBrowser
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.PrefsFileInfo
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginPermissions
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.PasswordHasher
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.interfaces.ui.UiRestart
import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.implementation.receivers.IosReceiverStatusStore
import app.aaps.plugins.sync.nsclientV3.ws.NsSocketFactory
import app.aaps.implementation.maintenance.PeriodicMaintenance
import app.aaps.shared.clientbindings.ClientGraphBindings
import kotlinx.coroutines.CoroutineScope
import app.aaps.plugins.automation.AutomationRuntime
import app.aaps.plugins.configuration.setupwizard.SWDefinition
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.search.BuiltInSearchables
import dev.zacsweers.metrox.viewmodel.MetroViewModelMultibindings
import app.aaps.database.AppRepository
import app.aaps.database.di.IosAppDatabaseBuilder
import app.aaps.implementation.plugin.PluginStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The real app's object graph on iOS - the counterpart of `AppRootGraph` in `:app`.
 *
 * [IosProbeGraph] answers "can Metro build this class at all". This one answers the question after
 * it: can the app itself be assembled and shown. They share every leaf through
 * [IosPlatformBindings] and differ only in what they expose and which database file they open.
 *
 * ## What is real and what is not
 *
 * Almost everything below is production AAPS code. The exceptions live in
 * `app.aaps.ios.shell.missing` and are there so this graph can be built at all: each one satisfies
 * a binding iOS has no implementation for yet, logs loudly when touched, and refuses to invent an
 * answer that a screen would act on. They are listed in `_docs/ios_blockers.md` and are meant to be
 * deleted one at a time.
 */
@DependencyGraph(AppScope::class)
interface IosAppGraph : MetroViewModelMultibindings {

    // Everything AapsAppRoot needs.
    val config: Config
    val preferences: Preferences

    /** Collected by the composition root, which rebuilds when it moves. */
    val uiRestart: UiRestart
    val dateUtil: DateUtil
    val decimalFormatter: DecimalFormatter
    val profileUtil: ProfileUtil
    val passwordHasher: PasswordHasher
    val passwordCheck: PasswordCheck

    /**
     * Real protection now, not the placeholder that granted everything.
     * `ProtectionCheckImpl` moved to commonMain, and `AapsAppRoot` places `ProtectionHost`, so the
     * request published here is rendered rather than left waiting.
     */
    val protectionCheck: ProtectionCheck
    val exportPasswordDataStore: ExportPasswordDataStore
    val visibilityContext: VisibilityContext
    val nsClient: NsClient
    val rxBus: RxBus
    val clientControlActionDispatcher: ClientControlActionDispatcher

    // Shared pieces the navigation graph reads.
    val activePlugin: ActivePlugin
    val pluginPermissions: PluginPermissions
    val textResolver: TextResolver
    val configBuilder: ConfigBuilder
    val prefsFileInfo: PrefsFileInfo
    val persistenceLayer: PersistenceLayer
    val logger: AAPSLogger

    /**
     * The factory for a history browsing window.
     *
     * Declared here rather than discovered: a `@GraphExtension` is only reachable through an
     * accessor its parent graph states, the same way `AppRootGraph` states this one. Without it the
     * factory looks like an ordinary missing binding, which is exactly how it first failed.
     *
     * Each window it creates is a separate scope with its own calculation objects - see
     * [IosHistoryWindowGraph] for why that matters.
     */
    val historyWindowFactory: IosHistoryWindowGraph.Factory

    /** The loop's own calculator and cache, exposed so a test can prove a window does not share them. */
    val iobCobCalculator: IobCobCalculator

    /**
     * The plugin registry itself, not just the [ActivePlugin] view of it.
     *
     * `PluginStore.plugins` is a `lateinit` that something has to fill at start up - `MainApp` does
     * it on Android, `IosAppStartup` here. Until it is filled, the first call that asks for the
     * active pump throws, which is what the app did before this existed.
     */
    val pluginStore: PluginStore

    /**
     * Plugins that register themselves with `@ContributesIntoMap`, keyed by their order.
     *
     * Only this one bucket, unlike Android, which also merges `@APS`, `@PumpDriver` and
     * `@NotNSClient` sets. An iOS build is a follower client: `config.APS`, `config.PUMPDRIVERS`
     * are false and `config.AAPSCLIENT` is true, so all three of those buckets are excluded by the
     * same rules `:app` applies. One bucket also means no key can collide, which is what
     * `mergePlugins` exists to detect on Android.
     */
    val contributedPlugins: Map<Int, PluginBase>
    val automationRuntime: AutomationRuntime
    // The periodic housekeeping shared with Android, and the scope the shell drives it on.
    val periodicMaintenance: PeriodicMaintenance
    val appScope: CoroutineScope
    val swDefinition: SWDefinition
    val builtInSearchables: BuiltInSearchables

    /**
     * These two view models are assisted, not contributed: they are built with the overview data
     * cache rather than from the map, so `metroViewModel()` cannot find them. Android reaches them
     * the same way, through their own factories.
     */
    val graphViewModelFactory: GraphViewModel.Factory
    val chipsViewModelFactory: ChipsViewModel.Factory
    val overviewDataCache: OverviewDataCache

    // What the overview needs beyond the above. `Objectives` and `PumpCommunicationStatus` became
    // available when ObjectivesPlugin and the pump status moved to commonMain.
    val objectives: Objectives
    val bgQualityCheck: BgQualityCheck
    val uiInteraction: UiInteraction
    val bolusProgressData: BolusProgressData
    val commandQueue: CommandQueue
    val pumpCommunicationStatus: PumpCommunicationStatus
    val dexcomBoyda: DexcomBoyda
    /** Concrete, not the interface: the app has to start the battery watch, which is iOS-only. */
    val receiverStatusStore: IosReceiverStatusStore

    val urlOpener: UrlOpener

    /** Where a Google sign in is shown. Not [urlOpener] - see `AuthBrowser` for why they differ. */
    val authBrowser: AuthBrowser
    val notificationManager: NotificationManager

    /**
     * The view model maps come from [MetroViewModelMultibindings], the same interface `AppRootGraph`
     * implements. `ClientViewModelFactory` turns them into the factory `metroViewModel()` asks for:
     * Android reaches that through the `Application` object, and iOS has no ambient equivalent, so
     * the host provides it as `LocalMetroViewModelFactory` around the shared UI.
     */

    /**
     * The app's own database, under the name the app uses rather than the probe's.
     *
     * The logger is passed in so that a database that could not be moved out of Documents says so in
     * `aaps.log`, where it will actually be read. `:database:impl` cannot see `AAPSLogger` itself.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun repository(aapsLogger: AAPSLogger): AppRepository =
        IosAppDatabaseBuilder(
            log = { failed, message ->
                if (failed) aapsLogger.error(LTag.DATABASE, message) else aapsLogger.debug(LTag.DATABASE, message)
            }
        ).provideAppRepository("aaps-ios.db")

    @DependencyGraph.Factory
    fun interface Factory {

        /**
         * [CoreObjectsGraph] is a `@BindingContainer`, so its `@Provides` are only visible to a
         * graph that includes it - the same way `AppRootGraph` takes it on Android.
         */
        fun create(
            @Includes coreObjects: CoreObjectsGraph,
            @Includes clientBindings: ClientGraphBindings,
            /**
             * The Nightscout socket, handed in by the app because it is written in Swift.
             *
             * Every other platform contributes its own: `SocketIoNsSocketFactory` in `jvmSharedMain`
             * carries `@ContributesBinding` and Android and desktop pick it up for free. iOS cannot,
             * because the implementation is `SwiftNsSocketFactory` on `socket.io-client-swift` - the
             * same project's client as the Java one, which is what keeps both platforms speaking to
             * Nightscout identically - and a Swift class cannot carry a Kotlin annotation.
             *
             * So it arrives here instead. Until it did, iOS had a placeholder that returned null and
             * the client simply never connected, while desktop worked.
             */
            @Provides nsSocketFactory: NsSocketFactory
        ): IosAppGraph
    }
}
