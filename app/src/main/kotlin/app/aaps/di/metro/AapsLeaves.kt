package app.aaps.di.metro

import androidx.work.WorkManager
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.dst.DstHelper
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.storage.Storage
import app.aaps.core.interfaces.userEntry.UserEntryPresentationHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.receivers.DataInbox
import app.aaps.implementation.maintenance.cloud.CloudStorageManager
import app.aaps.implementation.scenes.ActiveSceneManager
import app.aaps.implementation.scenes.SceneExecutor
import app.aaps.implementation.scenes.SceneRepository
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryJob
import app.aaps.ui.compose.overview.OverviewDataCacheFactory
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

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
@Singleton
@BindingContainer
class AapsLeaves @Inject constructor(
    private val aapsLoggerProvider: Provider<AAPSLogger>,
    private val receiverStatusStoreProvider: Provider<ReceiverStatusStore>,
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
    private val profileUtilProvider: Provider<ProfileUtil>,
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
    private val storageProvider: Provider<Storage>,
    private val userEntryPresentationHelperProvider: Provider<UserEntryPresentationHelper>,
    private val dataInboxProvider: Provider<DataInbox>,
    private val cloudStorageManagerProvider: Provider<CloudStorageManager>,
    private val calculationWorkflowProvider: Provider<CalculationWorkflow>,
    private val decimalFormatterProvider: Provider<DecimalFormatter>,
    private val processedTbrEbDataProvider: Provider<ProcessedTbrEbData>,
    private val overviewDataCacheFactoryProvider: Provider<OverviewDataCacheFactory>
) {

    @Provides fun aapsLogger(): AAPSLogger = aapsLoggerProvider.get()
    @Provides fun receiverStatusStore(): ReceiverStatusStore = receiverStatusStoreProvider.get()
    @Provides fun rxBus(): RxBus = rxBusProvider.get()
    @Provides fun activePlugin(): ActivePlugin = activePluginProvider.get()

    /** The application scope. Bound unqualified because a graph holds only this one. */
    @Provides fun appScope(): CoroutineScope = appScopeProvider.get()

    @Provides fun fabricPrivacy(): FabricPrivacy = fabricPrivacyProvider.get()
    @Provides fun runningModeExpiryJob(): RunningModeExpiryJob = runningModeExpiryJobProvider.get()
    @Provides fun localAlertUtils(): LocalAlertUtils = localAlertUtilsProvider.get()
    @Provides fun persistenceLayer(): PersistenceLayer = persistenceLayerProvider.get()
    @Provides fun config(): Config = configProvider.get()
    @Provides fun iobCobCalculator(): IobCobCalculator = iobCobCalculatorProvider.get()
    @Provides fun loop(): Loop = loopProvider.get()
    @Provides fun dateUtil(): DateUtil = dateUtilProvider.get()
    @Provides fun profileFunction(): ProfileFunction = profileFunctionProvider.get()
    @Provides fun profileUtil(): ProfileUtil = profileUtilProvider.get()
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
    @Provides fun storage(): Storage = storageProvider.get()
    @Provides fun userEntryPresentationHelper(): UserEntryPresentationHelper = userEntryPresentationHelperProvider.get()
    @Provides fun dataInbox(): DataInbox = dataInboxProvider.get()
    @Provides fun cloudStorageManager(): CloudStorageManager = cloudStorageManagerProvider.get()
    @Provides fun calculationWorkflow(): CalculationWorkflow = calculationWorkflowProvider.get()
    @Provides fun decimalFormatter(): DecimalFormatter = decimalFormatterProvider.get()
    @Provides fun processedTbrEbData(): ProcessedTbrEbData = processedTbrEbDataProvider.get()
    @Provides fun overviewDataCacheFactory(): OverviewDataCacheFactory = overviewDataCacheFactoryProvider.get()
}
