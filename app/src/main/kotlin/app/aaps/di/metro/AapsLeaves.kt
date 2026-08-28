package app.aaps.di.metro

import androidx.work.WorkManager
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.database.di.DatabaseConfig
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.configuration.RunningConfigurationKeys
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.PermissionProvider
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginPermissions
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.ui.search.SearchableProvider
import app.aaps.core.utils.receivers.DataInbox
import app.aaps.plugins.sync.wear.WearPlugin
import app.aaps.plugins.sync.nsclientV3.clientcontrol.AuthorizedClientsRepository
import app.aaps.plugins.sync.nsclientV3.clientcontrol.PairingOfferPublisher
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientPairingRepository
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientControlPublisher
import app.aaps.plugins.sync.nsclientV3.clientcontrol.PairingOfferFetcher
import app.aaps.plugins.sync.smsCommunicator.compose.SmsCommunicatorRepository
import app.aaps.plugins.sync.tidepool.compose.TidepoolRepository
import app.aaps.plugins.sync.xdrip.compose.XdripMvvmRepository
import app.aaps.plugins.sync.nsclientV3.ReceiverDelegate
import app.aaps.plugins.sync.tidepool.utils.RateLimit
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.implementation.maintenance.cloud.CloudStorageManager
import app.aaps.implementation.scenes.SceneExecutor
import app.aaps.plugins.automation.services.LastLocationDataContainer
import app.aaps.plugins.constraints.objectives.SntpClient
import app.aaps.ui.compose.history.HistoryScope
import app.aaps.ui.compose.overview.OverviewDataCacheFactory
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import javax.inject.Provider

/**
 * Every app-wide object Dagger still owns, offered to Metro as one thing.
 *
 * A graph includes this with a single `@Includes` parameter instead of listing what it needs one type
 * at a time. Before this, each graph restated its dependencies twice - once as a factory parameter and
 * once as a function unwrapping it - which is why the root graph alone carried seventy lines that said
 * nothing.
 *
 * ## Why there is no DeferredRef here
 *
 * The earlier factories took instances, so creating a graph resolved every Dagger provider at once -
 * and Dagger reaches back, because `Loop` leads to the plugin list, which asks these graphs. That
 * cycle crashed all three spike branches, and `DeferredRef` existed to break it.
 *
 * A binding container does not need it. These are `@Provides` **functions**: Metro calls one only when
 * something actually asks for that type, so the deferral is in the shape rather than in a wrapper. The
 * javax [Provider]s below are what make each call cheap and late.
 *
 * When Dagger is gone this class goes with it, and the graphs that include it lose one parameter each.
 */
@BindingContainer
class AapsLeaves(
    private val metroMemberInjectorProvider: Provider<MetroMemberInjector>,
    private val fabricPrivacyProvider: Provider<FabricPrivacy>,
    private val configProvider: Provider<Config>,
    private val databaseConfigProvider: Provider<DatabaseConfig>,
    // Both are Dagger @Binds in ImplementationModule, and the openAPS plugins need them now that Metro
    // builds those. APSResult is asked for through a Provider - one result object per loop run.
    // Dagger owns this one; LoopPlugin needs it and Metro builds LoopPlugin now.
    // The activities this app injects need these; all three are Dagger @Binds in their own modules.
    private val rhProvider: Provider<ResourceHelper>,
    // Needed by the feature extensions below the root, which no longer carry their own leaf lists.
    // Source plugins, still built by Dagger. They live here rather than in their own module because a
    // graph extension is generated in the parent's module, so Metro cannot read a container from the
    // module the extension is declared in.
    // Automation.
    private val uiInteractionProvider: Provider<UiInteraction>,
    // Constraints.
    // Still Dagger-owned, and needed by the scene classes that moved to Metro.
    // Dagger-owned on purpose: it is bound from XdripPlugin, which is still in the Dagger plugin list.
    // Contributing it would have Metro build a second copy of that plugin.
    /**
     * The APP-level overview data and cache.
     *
     * `HistoryWindowGraph` binds its own pair scoped to the window, and an extension's binding shadows
     * the parent's, so a history window still gets its own. That separation is what keeps history
     * browsing from computing into the running loop, and `HistoryBrowserDataTest` checks it.
     */
    // Dagger keeps building this one - see the note in MaintenanceImplModule.
    // A Dagger @IntoSet multibinding, handed over already assembled. Metro receives the Set as one
    // binding rather than re-declaring the multibinding on this side.
    /**
     * The history browser scope.
     *
     * A leaf rather than a contribution: `HistoryBrowserData` takes `MetroGraphs` to open its window,
     * so letting Metro build it would be a cycle back into the graph that builds it.
     *
     * There is one of these for the app lifetime, and it wraps its own `HistoryWindowGraph`. That
     * window is what keeps history browsing off the live loop's calculation objects, so an app-scoped
     * view model reading it is fine.
     */
    private val historyScopeProvider: Provider<HistoryScope>,
    // Same object as ActivePlugin above (PluginStore), under its other interface.
) {



    @Provides fun fabricPrivacy(): FabricPrivacy = fabricPrivacyProvider.get()
    // No runningModeExpiryJob() leaf: Metro builds it now (commonMain, Metro @Inject), and Dagger gets
    // both running-mode classes from `CoreObjectsModule` instead.
    @Provides fun config(): Config = configProvider.get()
    @Provides fun databaseConfig(): DatabaseConfig = databaseConfigProvider.get()
    // No iobCobCalculator() any more: Metro builds it now, in `MainPluginsBindings`, and Dagger receives
    // it through `CoreObjectsModule.provideIobCobCalculator`.
    // No loop() leaf any more: Metro builds LoopPlugin, so Loop travels the other way, through
    // `CoreObjectsModule.provideLoop`.
    @Provides fun rh(): ResourceHelper = rhProvider.get()

    /** `ResourceHelper` is the Android implementation of the multiplatform [TextResolver]. */
    @Provides fun textResolver(rh: ResourceHelper): TextResolver = rh


    @Provides fun uiInteraction(): UiInteraction = uiInteractionProvider.get()


    /** A value object: unscoped, as its Dagger binding is. */
    @Provides fun historyScope(): HistoryScope = historyScopeProvider.get()

    // Dagger owns these three, the same way it owns the pump drivers: AuthRequest, the nine
    // @HiltWorker loaders under nsclientV3 and the wear data layer all inject the concrete class, so
    // Dagger builds them and Metro borrows. See SyncPluginsBindings for how they reach the plugin map.

    // The repositories behind the :plugins:sync view models. Dagger-owned for the same reason the
    // plugins are: SmsCommunicatorPlugin, TidepoolPlugin, XdripPlugin, the xdrip worker and the
    // client-control receiver all inject them, so Dagger's copy is the one being written to.

    // Dagger-owned for the same reason as the plugins above: NSClientV3Plugin and TidepoolUploader are
    // built by Dagger and are what actually writes to these. Both hold state - ReceiverDelegate the
    // charging/network gate, RateLimit its map of last-run times - so a Metro-built second copy left
    // TidepoolPlugin reading a gate nobody updates and a rate limiter that never limits.

    /**
     * The graph's own member injector, handed back to it.
     *
     * A service or receiver that Android constructs fills its own fields from this, and the classes it
     * builds by hand do the same. The loop is only apparent: `MetroGraphs` holds the graph and the
     * @Provides functions here run on demand, so nothing is asked for while the graph is being built.
     */
    @Provides fun metroMemberInjector(): MetroMemberInjector = metroMemberInjectorProvider.get()


    // What NSClientV3Service needs beyond the usual leaves. All three are Dagger @Binds in :plugins:sync.
}
