package app.aaps.di.metro

import android.content.Context
import androidx.work.WorkManager
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.dst.DstHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.configuration.RunningConfigurationKeys
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
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
import app.aaps.implementation.maintenance.cloud.CloudStorageManager
import app.aaps.implementation.scenes.SceneExecutor
import app.aaps.plugins.automation.services.LastLocationDataContainer
import app.aaps.plugins.constraints.objectives.SntpClient
import app.aaps.ui.compose.history.HistoryScope
import app.aaps.ui.compose.overview.OverviewDataCacheFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineScope
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
    private val nsClientRepositoryProvider: Provider<NSClientRepository>,
    private val runningConfigurationProvider: Provider<RunningConfiguration>,
    private val nsClientSourceProvider: Provider<NSClientSource>,
    private val runningConfigurationKeysProvider: Provider<RunningConfigurationKeys>,
    private val activePluginProvider: Provider<ActivePlugin>,
    @ApplicationScope private val appScopeProvider: Provider<CoroutineScope>,
    private val fabricPrivacyProvider: Provider<FabricPrivacy>,
    private val localAlertUtilsProvider: Provider<LocalAlertUtils>,
    private val persistenceLayerProvider: Provider<PersistenceLayer>,
    private val configProvider: Provider<Config>,
    // Dagger owns the app-wide emitter - `WorkflowModule.provideMainSignalsEmitter`. A history window
    // has its own, built in `HistoryWindowGraph`, which is why this one is not simply contributed.
    private val calculationSignalsEmitterProvider: Provider<CalculationSignalsEmitter>,
    // Both are Dagger @Binds in ImplementationModule, and the openAPS plugins need them now that Metro
    // builds those. APSResult is asked for through a Provider - one result object per loop run.
    private val apsResultProvider: Provider<APSResult>,
    // Dagger owns this one; LoopPlugin needs it and Metro builds LoopPlugin now.
    // The activities this app injects need these; all three are Dagger @Binds in their own modules.
    private val authFlowOutProvider: Provider<AuthFlowOut>,
    private val tidepoolUploaderProvider: Provider<TidepoolUploader>,
    private val profileFunctionProvider: Provider<ProfileFunction>,
    private val commandQueueProvider: Provider<CommandQueue>,
    private val rhProvider: Provider<ResourceHelper>,
    private val dstHelperProvider: Provider<DstHelper>,
    private val workManagerProvider: Provider<WorkManager>,
    private val notificationManagerProvider: Provider<NotificationManager>,
    private val cloudStorageManagerProvider: Provider<CloudStorageManager>,
    private val overviewDataCacheFactoryProvider: Provider<OverviewDataCacheFactory>,
    // Needed by the feature extensions below the root, which no longer carry their own leaf lists.
    private val constraintsCheckerProvider: Provider<ConstraintsChecker>,
    private val automationProvider: Provider<Automation>,
    private val contextProvider: Provider<Context>,
    // Source plugins, still built by Dagger. They live here rather than in their own module because a
    // graph extension is generated in the parent's module, so Metro cannot read a container from the
    // module the extension is declared in.
    // Automation.
    private val uiInteractionProvider: Provider<UiInteraction>,
    // Constraints.
    private val versionCheckerUtilsProvider: Provider<VersionCheckerUtils>,
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
    private val searchableProvidersProvider: Provider<Set<SearchableProvider>>,
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
    private val bolusProgressDataProvider: Provider<BolusProgressData>,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val historyScopeProvider: Provider<HistoryScope>,
    private val overviewDataCacheProvider: Provider<OverviewDataCache>,
    // Same object as ActivePlugin above (PluginStore), under its other interface.
    private val pluginPermissionsProvider: Provider<PluginPermissions>,
    @ApplicationContext private val appContextProvider: Provider<Context>,
    private val nsClientProvider: Provider<NsClient>,
    private val clientControlActionDispatcherProvider: Provider<ClientControlActionDispatcher>,
    private val sntpClientProvider: Provider<SntpClient>
) {
    @Provides fun activePlugin(): ActivePlugin = activePluginProvider.get()

    /**
     * The application scope, qualified.
     *
     * It used to be bound unqualified, because without Dagger interop Metro ignored the javax
     * @Qualifier entirely. With interop on, consumers ask for the qualified type and get it.
     */
    @Provides @ApplicationScope fun appScope(): CoroutineScope = appScopeProvider.get()

    /**
     * The same scope again, unqualified, for the multiplatform classes that take a plain
     * `CoroutineScope` - `@ApplicationScope` is a javax qualifier and cannot appear in commonMain.
     * Same instance either way.
     */
    @Provides fun unqualifiedAppScope(): CoroutineScope = appScopeProvider.get()

    @Provides fun fabricPrivacy(): FabricPrivacy = fabricPrivacyProvider.get()
    // No runningModeExpiryJob() leaf: Metro builds it now (commonMain, Metro @Inject), and Dagger gets
    // both running-mode classes from `CoreObjectsModule` instead.
    @Provides fun localAlertUtils(): LocalAlertUtils = localAlertUtilsProvider.get()
    @Provides fun persistenceLayer(): PersistenceLayer = persistenceLayerProvider.get()
    @Provides fun config(): Config = configProvider.get()
    // No iobCobCalculator() any more: Metro builds it now, in `MainPluginsBindings`, and Dagger receives
    // it through `CoreObjectsModule.provideIobCobCalculator`.
    @Provides fun calculationSignalsEmitter(): CalculationSignalsEmitter = calculationSignalsEmitterProvider.get()
    @Provides fun apsResult(): APSResult = apsResultProvider.get()
    // No loop() leaf any more: Metro builds LoopPlugin, so Loop travels the other way, through
    // `CoreObjectsModule.provideLoop`.
    @Provides fun authFlowOut(): AuthFlowOut = authFlowOutProvider.get()
    @Provides fun tidepoolUploader(): TidepoolUploader = tidepoolUploaderProvider.get()
    @Provides fun profileFunction(): ProfileFunction = profileFunctionProvider.get()
    @Provides fun commandQueue(): CommandQueue = commandQueueProvider.get()
    @Provides fun rh(): ResourceHelper = rhProvider.get()

    /** `ResourceHelper` is the Android implementation of the multiplatform [TextResolver]. */
    @Provides fun textResolver(rh: ResourceHelper): TextResolver = rh
    @Provides fun dstHelper(): DstHelper = dstHelperProvider.get()
    @Provides fun workManager(): WorkManager = workManagerProvider.get()
    @Provides fun notificationManager(): NotificationManager = notificationManagerProvider.get()
    // No activeSceneManager() here on purpose: Metro owns it (@SingleIn on the class), so this leaf would
    // push a SECOND one in from Dagger - and an unscoped one, because the class carries no javax scope, so
    // every call built another. `CoreObjectsModule.provideActiveSceneManager` hands Metro's instance the
    // other way, which is the direction the class itself documents.
    // No sceneRepository() either, same reason as activeSceneManager above: Metro owns it (@SingleIn +
    // two @ContributesBinding), so this leaf pushed an unscoped Dagger copy back in.
    @Provides fun cloudStorageManager(): CloudStorageManager = cloudStorageManagerProvider.get()
    @Provides fun overviewDataCacheFactory(): OverviewDataCacheFactory = overviewDataCacheFactoryProvider.get()
    @Provides fun constraintsChecker(): ConstraintsChecker = constraintsCheckerProvider.get()
    @Provides fun automation(): Automation = automationProvider.get()
    @Provides fun context(): Context = contextProvider.get()


    @Provides fun uiInteraction(): UiInteraction = uiInteractionProvider.get()

    @Provides fun versionCheckerUtils(): VersionCheckerUtils = versionCheckerUtilsProvider.get()
    @Provides fun sntpClient(): SntpClient = sntpClientProvider.get()
    @Provides fun bolusProgressData(): BolusProgressData = bolusProgressDataProvider.get()

    /** A value object: unscoped, as its Dagger binding is. */
    @Provides fun pumpEnactResult(): PumpEnactResult = pumpEnactResultProvider.get()
    @Provides fun historyScope(): HistoryScope = historyScopeProvider.get()
    @Provides fun searchableProviders(): Set<SearchableProvider> = searchableProvidersProvider.get()
    @Provides fun overviewDataCache(): OverviewDataCache = overviewDataCacheProvider.get()
    @Provides fun pluginPermissions(): PluginPermissions = pluginPermissionsProvider.get()

    /** Hilt's qualifier, read now that interop is on. Same Context as the unqualified binding. */
    @Provides @ApplicationContext fun appContext(): Context = appContextProvider.get()
    /**
     * The graph's own member injector, handed back to it.
     *
     * A service or receiver that Android constructs fills its own fields from this, and the classes it
     * builds by hand do the same. The loop is only apparent: `MetroGraphs` holds the graph and the
     * @Provides functions here run on demand, so nothing is asked for while the graph is being built.
     */
    @Provides fun metroMemberInjector(): MetroMemberInjector = metroMemberInjectorProvider.get()

    @Provides fun nsClient(): NsClient = nsClientProvider.get()

    // What NSClientV3Service needs beyond the usual leaves. All three are Dagger @Binds in :plugins:sync.
    @Provides fun nsClientRepository(): NSClientRepository = nsClientRepositoryProvider.get()
    @Provides fun runningConfiguration(): RunningConfiguration = runningConfigurationProvider.get()
    @Provides fun nsClientSource(): NSClientSource = nsClientSourceProvider.get()
    @Provides fun runningConfigurationKeys(): RunningConfigurationKeys = runningConfigurationKeysProvider.get()
    @Provides fun clientControlActionDispatcher(): ClientControlActionDispatcher = clientControlActionDispatcherProvider.get()
}
