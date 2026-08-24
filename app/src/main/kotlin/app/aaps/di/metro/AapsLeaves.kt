package app.aaps.di.metro

import android.content.Context

import androidx.work.WorkManager
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.dst.DstHelper
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.storage.Storage
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.userEntry.UserEntryPresentationHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.receivers.DataInbox
import app.aaps.implementation.maintenance.cloud.CloudStorageManager
import app.aaps.implementation.scenes.ActiveSceneManager
import app.aaps.implementation.scenes.SceneExecutor
import app.aaps.implementation.scenes.SceneRepository
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryJob
import app.aaps.plugins.automation.services.LastLocationDataContainer
import app.aaps.plugins.constraints.objectives.SntpClient
import app.aaps.ui.compose.overview.OverviewDataCacheFactory
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
    private val aapsLoggerProvider: Provider<AAPSLogger>,
    private val rxBusProvider: Provider<RxBus>,
    private val activePluginProvider: Provider<ActivePlugin>,
    @ApplicationScope private val appScopeProvider: Provider<CoroutineScope>,
    private val fabricPrivacyProvider: Provider<FabricPrivacy>,
    private val runningModeExpiryJobProvider: Provider<RunningModeExpiryJob>,
    private val localAlertUtilsProvider: Provider<LocalAlertUtils>,
    private val persistenceLayerProvider: Provider<PersistenceLayer>,
    private val configProvider: Provider<Config>,
    private val iobCobCalculatorProvider: Provider<IobCobCalculator>,
    private val loopProvider: Provider<Loop>,
    private val dateUtilProvider: Provider<DateUtil>,
    private val profileFunctionProvider: Provider<ProfileFunction>,
    private val commandQueueProvider: Provider<CommandQueue>,
    private val maintenanceProvider: Provider<Maintenance>,
    private val rhProvider: Provider<ResourceHelper>,
    private val preferencesProvider: Provider<Preferences>,
    private val dstHelperProvider: Provider<DstHelper>,
    private val workManagerProvider: Provider<WorkManager>,
    private val concentrationHelperProvider: Provider<ConcentrationHelper>,
    private val notificationManagerProvider: Provider<NotificationManager>,
    private val activeSceneManagerProvider: Provider<ActiveSceneManager>,
    private val sceneExecutorProvider: Provider<SceneExecutor>,
    private val sceneRepositoryProvider: Provider<SceneRepository>,
    private val fileListProviderProvider: Provider<FileListProvider>,
    private val userEntryPresentationHelperProvider: Provider<UserEntryPresentationHelper>,
    private val dataInboxProvider: Provider<DataInbox>,
    private val cloudStorageManagerProvider: Provider<CloudStorageManager>,
    private val calculationWorkflowProvider: Provider<CalculationWorkflow>,
    private val processedTbrEbDataProvider: Provider<ProcessedTbrEbData>,
    private val overviewDataCacheFactoryProvider: Provider<OverviewDataCacheFactory>,
    // Needed by the feature extensions below the root, which no longer carry their own leaf lists.
    private val constraintsCheckerProvider: Provider<ConstraintsChecker>,
    private val uelProvider: Provider<UserEntryLogger>,
    private val automationProvider: Provider<Automation>,
    private val glucoseStatusProvider: Provider<GlucoseStatusProvider>,
    private val processedDeviceStatusDataProvider: Provider<ProcessedDeviceStatusData>,
    private val wizardBolusExecutorProvider: Provider<WizardBolusExecutor>,
    private val contextProvider: Provider<Context>,
    private val virtualPumpProvider: Provider<VirtualPump>,
    // Source plugins, still built by Dagger. They live here rather than in their own module because a
    // graph extension is generated in the parent's module, so Metro cannot read a container from the
    // module the extension is declared in.
    // Automation.
    private val uiInteractionProvider: Provider<UiInteraction>,
    private val notificationHolderProvider: Provider<NotificationHolder>,
    private val lastLocationDataContainerProvider: Provider<LastLocationDataContainer>,
    // Constraints.
    private val versionCheckerUtilsProvider: Provider<VersionCheckerUtils>,
    private val passwordCheckProvider: Provider<PasswordCheck>,
    // Still Dagger-owned, and needed by the scene classes that moved to Metro.
    // Dagger-owned on purpose: it is bound from XdripPlugin, which is still in the Dagger plugin list.
    // Contributing it would have Metro build a second copy of that plugin.
    private val xDripBroadcastProvider: Provider<XDripBroadcast>,
    private val nsClientProvider: Provider<NsClient>,
    private val clientControlActionDispatcherProvider: Provider<ClientControlActionDispatcher>,
    private val sntpClientProvider: Provider<SntpClient>
) {

    @Provides fun aapsLogger(): AAPSLogger = aapsLoggerProvider.get()
    @Provides fun rxBus(): RxBus = rxBusProvider.get()
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
    @Provides fun runningModeExpiryJob(): RunningModeExpiryJob = runningModeExpiryJobProvider.get()
    @Provides fun localAlertUtils(): LocalAlertUtils = localAlertUtilsProvider.get()
    @Provides fun persistenceLayer(): PersistenceLayer = persistenceLayerProvider.get()
    @Provides fun config(): Config = configProvider.get()
    @Provides fun iobCobCalculator(): IobCobCalculator = iobCobCalculatorProvider.get()
    @Provides fun loop(): Loop = loopProvider.get()
    @Provides fun dateUtil(): DateUtil = dateUtilProvider.get()
    @Provides fun profileFunction(): ProfileFunction = profileFunctionProvider.get()
    @Provides fun commandQueue(): CommandQueue = commandQueueProvider.get()
    @Provides fun maintenance(): Maintenance = maintenanceProvider.get()
    @Provides fun rh(): ResourceHelper = rhProvider.get()

    /** `ResourceHelper` is the Android implementation of the multiplatform [TextResolver]. */
    @Provides fun textResolver(rh: ResourceHelper): TextResolver = rh

    @Provides fun preferences(): Preferences = preferencesProvider.get()
    @Provides fun dstHelper(): DstHelper = dstHelperProvider.get()
    @Provides fun workManager(): WorkManager = workManagerProvider.get()
    @Provides fun concentrationHelper(): ConcentrationHelper = concentrationHelperProvider.get()
    @Provides fun notificationManager(): NotificationManager = notificationManagerProvider.get()
    @Provides fun activeSceneManager(): ActiveSceneManager = activeSceneManagerProvider.get()
    @Provides fun sceneExecutor(): SceneExecutor = sceneExecutorProvider.get()
    @Provides fun sceneRepository(): SceneRepository = sceneRepositoryProvider.get()
    @Provides fun fileListProvider(): FileListProvider = fileListProviderProvider.get()
    @Provides fun userEntryPresentationHelper(): UserEntryPresentationHelper = userEntryPresentationHelperProvider.get()
    @Provides fun dataInbox(): DataInbox = dataInboxProvider.get()
    @Provides fun cloudStorageManager(): CloudStorageManager = cloudStorageManagerProvider.get()
    @Provides fun calculationWorkflow(): CalculationWorkflow = calculationWorkflowProvider.get()
    @Provides fun processedTbrEbData(): ProcessedTbrEbData = processedTbrEbDataProvider.get()
    @Provides fun overviewDataCacheFactory(): OverviewDataCacheFactory = overviewDataCacheFactoryProvider.get()
    @Provides fun constraintsChecker(): ConstraintsChecker = constraintsCheckerProvider.get()
    @Provides fun uel(): UserEntryLogger = uelProvider.get()
    @Provides fun automation(): Automation = automationProvider.get()
    @Provides fun glucoseStatus(): GlucoseStatusProvider = glucoseStatusProvider.get()
    @Provides fun processedDeviceStatusData(): ProcessedDeviceStatusData = processedDeviceStatusDataProvider.get()
    @Provides fun wizardBolusExecutor(): WizardBolusExecutor = wizardBolusExecutorProvider.get()
    @Provides fun context(): Context = contextProvider.get()
    @Provides fun virtualPump(): VirtualPump = virtualPumpProvider.get()


    @Provides fun uiInteraction(): UiInteraction = uiInteractionProvider.get()
    @Provides fun notificationHolder(): NotificationHolder = notificationHolderProvider.get()
    @Provides fun lastLocationDataContainer(): LastLocationDataContainer = lastLocationDataContainerProvider.get()

    @Provides fun versionCheckerUtils(): VersionCheckerUtils = versionCheckerUtilsProvider.get()
    @Provides fun passwordCheck(): PasswordCheck = passwordCheckProvider.get()
    @Provides fun sntpClient(): SntpClient = sntpClientProvider.get()
    @Provides fun xDripBroadcast(): XDripBroadcast = xDripBroadcastProvider.get()
    @Provides fun nsClient(): NsClient = nsClientProvider.get()
    @Provides fun clientControlActionDispatcher(): ClientControlActionDispatcher = clientControlActionDispatcherProvider.get()
}
