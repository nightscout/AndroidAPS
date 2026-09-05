package app.aaps.desktop.shell.di

import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.ui.UiRestart
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.ui.UrlOpener
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.PrefsFileInfo
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginPermissions
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.PasswordHasher
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.automation.AutomationRuntime
import app.aaps.plugins.configuration.setupwizard.SWDefinition
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.search.BuiltInSearchables
import dev.zacsweers.metrox.viewmodel.MetroViewModelMultibindings
import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.implementation.maintenance.PeriodicMaintenance
import app.aaps.shared.clientbindings.ClientGraphBindings
import kotlinx.coroutines.CoroutineScope
import app.aaps.database.AppRepository
import app.aaps.database.di.JvmAppDatabaseBuilder
import app.aaps.implementation.maintenance.DesktopFolders
import java.io.File
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The real app's object graph on desktop - the counterpart of `AppRootGraph` in `:app` and
 * `IosAppGraph` in `:ios:shell`.
 *
 * The accessors that [app.aaps.appshell.AapsAppRoot] needs are listed below rather than declared,
 * because Metro validates a graph as a whole: adding them before their bindings exist stops the
 * module compiling instead of leaving something that can be grown one binding at a time. The list is
 * kept by declaring them, reading what Metro reports and taking them out again.
 *
 * ## Where this stands
 *
 * The shell depends on the same 26 modules `:ios:shell` does, so every plugin that registers itself
 * with `@ContributesBinding` is already in the graph, and the classes in `app.aaps.desktop.shell.platform`
 * answer the platform half. Measured at **1**, down from 32:
 *
 * **`Autotune`** needs its plugin ported out of androidMain: about 2,700 lines of arithmetic whose
 * only Android parts are `Calendar`/`TimeZone` and a file dump, but it computes basal, ISF and ICR,
 * so the date maths wants golden vectors rather than a quick swap.
 *
 * Nightscout sync is done, websocket included. `SocketNsConnection` in `:shared:clientbindings` is
 * shared with iOS and carries the remote-control channel, so a command reaches this client as fast
 * as it reaches a phone. `DesktopNsLoadExecutor` still runs the REST round the plugin asks for.
 *
 * **Needs a port rather than an implementation:** `Autotune`, whose `AutotunePlugin` is arithmetic
 * over treatment history sitting in androidMain.
 *
 * Settings import and export is no longer on this list. `LocalImportExportPrefs` in `commonMain`
 * carries the whole flow, and desktop supplies only the file access - see `_docs/desktop_alignment.md`.
 *
 * Everything that was "absent by nature" is now answered rather than missing - see
 * `DesktopAutomationInputs` and `DesktopAbsentIntegrations`. Two of that group turned out to be real
 * work rather than refusals: `ReminderScheduler` and `SceneExpiryScheduler` both run on a coroutine
 * timer, and the second one has to, because scene expiry reverts an SMB toggle and a profile switch.
 *
 * The pattern to follow for the first group is `app.aaps.ios.shell.missing`: nothing returns a
 * plausible value, and nothing is silent.
 */
@DependencyGraph(AppScope::class)
interface DesktopAppGraph : MetroViewModelMultibindings {

    // Everything AapsAppRoot takes.
    val config: Config
    val preferences: Preferences
    val dateUtil: DateUtil
    val decimalFormatter: DecimalFormatter
    val profileUtil: ProfileUtil
    val passwordHasher: PasswordHasher
    val passwordCheck: PasswordCheck
    val protectionCheck: ProtectionCheck
    val exportPasswordDataStore: ExportPasswordDataStore
    val visibilityContext: VisibilityContext
    val nsClient: NsClient
    val rxBus: RxBus
    val clientControlActionDispatcher: ClientControlActionDispatcher

    // Everything appNavGraph takes.
    val activePlugin: ActivePlugin
    val pluginPermissions: PluginPermissions
    val textResolver: TextResolver
    val configBuilder: ConfigBuilder
    // The periodic housekeeping shared with Android, and the scope the shell drives it on.
    val periodicMaintenance: PeriodicMaintenance
    val appScope: CoroutineScope

    /** Collected by the composition root, which rebuilds when it moves. */
    val uiRestart: UiRestart
    val prefsFileInfo: PrefsFileInfo
    val persistenceLayer: PersistenceLayer
    val logger: AAPSLogger
    val pluginStore: PluginStore
    val contributedPlugins: Map<Int, PluginBase>
    val automationRuntime: AutomationRuntime
    val swDefinition: SWDefinition
    val builtInSearchables: BuiltInSearchables

    // What OverviewScreen needs beyond the above.
    val objectives: Objectives
    val bgQualityCheck: BgQualityCheck
    val notificationManager: NotificationManager
    val uiInteraction: UiInteraction
    val bolusProgressData: BolusProgressData
    val commandQueue: CommandQueue
    val pumpCommunicationStatus: PumpCommunicationStatus
    val urlOpener: UrlOpener
    val dexcomBoyda: DexcomBoyda

    /**
     * Assisted rather than contributed: both are built with the overview data cache instead of
     * coming from the view model map, so `metroViewModel()` cannot find them. Android and iOS
     * reach them the same way, through their own factories.
     */
    val graphViewModelFactory: GraphViewModel.Factory
    val chipsViewModelFactory: ChipsViewModel.Factory
    val overviewDataCache: OverviewDataCache

    /** The app's own database, inside this client's own data directory so two clients never share one. */
    @Provides
    @SingleIn(AppScope::class)
    fun repository(): AppRepository = JvmAppDatabaseBuilder().provideAppRepository(File(DesktopFolders.data, "aaps-desktop.db").absolutePath)

    /**
     * [CoreObjectsGraph] is a plain binding container rather than a contributed one, so every graph
     * has to include it by name. It holds the QuickWizard / QuickWizardEntry / BolusWizard cycle,
     * broken with deferred providers - the same shape `AppRootGraph` and `IosAppGraph` use.
     *
     * [ClientGraphBindings] is the same idea: the bindings iOS and desktop both need, in one place
     * rather than copied into each shell. Not contributed, because Android provides the same ones.
     */


    @DependencyGraph.Factory
    fun interface Factory {

        fun create(
            @Includes coreObjects: CoreObjectsGraph,
            @Includes clientBindings: ClientGraphBindings
        ): DesktopAppGraph
    }
}
