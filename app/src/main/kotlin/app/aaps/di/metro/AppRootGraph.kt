package app.aaps.di.metro

import androidx.work.WorkManager
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.DeferredRef
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
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineScope

/**
 * The one Metro root. Everything else hangs off it as a graph extension.
 *
 * Before this there were seven independent root graphs, each with its own factory restating the
 * app-wide objects it needed and its own set of one-line functions unwrapping them - around 150 lines
 * of declare-then-unwrap. Worse, it was **unsafe by construction**: two root graphs that both scope the
 * same type get one instance each, silently. `MetroScopingTest` shows exactly that - two roots built
 * from the same factory share nothing at all. It only held together because every shared object still
 * belongs to Dagger, which keeps it single. The first `@SingleIn(AppScope::class)` binding to appear in
 * two roots would have been a quiet duplicate, and in this app a duplicated calculator or command queue
 * is not a cosmetic problem.
 *
 * With one root that cannot happen: a scoped binding lives here once and every extension sees the same
 * instance. An extension that declares its own scope gets its own instances of what it scopes, which is
 * what the history browser needs and what the same test verifies.
 *
 * The app-wide objects still arrive as [DeferredRef] because they still belong to Dagger. The deferral
 * is the re-entrancy guard written up in [MetroGraphs]. When Dagger is gone the factory below goes with
 * it and these become ordinary bindings.
 */
@DependencyGraph(AppScope::class)
interface AppRootGraph {

    /** Android classes that fill their own fields. */
    val receiversGraph: AppReceiversGraph

    /** Workers, which WorkManager builds through `MetroWorkerFactory`. */
    val workersGraph: AppWorkersGraph

    /**
     * Opens one history browsing window. A factory rather than an accessor, because each window is a
     * separate scope with its own calculation objects - see [HistoryWindowGraph].
     */
    val historyWindowFactory: HistoryWindowGraph.Factory

    @DependencyGraph.Factory
    fun interface Factory {

        @Suppress("LongParameterList")
        fun create(
            @Provides aapsLoggerRef: DeferredRef<AAPSLogger>,
            @Provides receiverStatusStoreRef: DeferredRef<ReceiverStatusStore>,
            @Provides rxBusRef: DeferredRef<RxBus>,
            @Provides activePluginRef: DeferredRef<ActivePlugin>,
            @Provides appScopeRef: DeferredRef<CoroutineScope>,
            @Provides fabricPrivacyRef: DeferredRef<FabricPrivacy>,
            @Provides runningModeExpiryJobRef: DeferredRef<RunningModeExpiryJob>,
            @Provides localAlertUtilsRef: DeferredRef<LocalAlertUtils>,
            @Provides persistenceLayerRef: DeferredRef<PersistenceLayer>,
            @Provides configRef: DeferredRef<Config>,
            @Provides iobCobCalculatorRef: DeferredRef<IobCobCalculator>,
            @Provides loopRef: DeferredRef<Loop>,
            @Provides dateUtilRef: DeferredRef<DateUtil>,
            @Provides profileFunctionRef: DeferredRef<ProfileFunction>,
            @Provides profileUtilRef: DeferredRef<ProfileUtil>,
            @Provides commandQueueRef: DeferredRef<CommandQueue>,
            @Provides maintenanceRef: DeferredRef<Maintenance>,
            @Provides rhRef: DeferredRef<ResourceHelper>,
            @Provides preferencesRef: DeferredRef<Preferences>,
            @Provides dstHelperRef: DeferredRef<DstHelper>,
            @Provides workManagerRef: DeferredRef<WorkManager>,
            @Provides concentrationHelperRef: DeferredRef<ConcentrationHelper>,
            @Provides notificationManagerRef: DeferredRef<NotificationManager>,
            @Provides activeSceneManagerRef: DeferredRef<ActiveSceneManager>,
            @Provides sceneExecutorRef: DeferredRef<SceneExecutor>,
            @Provides sceneRepositoryRef: DeferredRef<SceneRepository>,
            @Provides fileListProviderRef: DeferredRef<FileListProvider>,
            @Provides storageRef: DeferredRef<Storage>,
            @Provides userEntryPresentationHelperRef: DeferredRef<UserEntryPresentationHelper>,
            @Provides dataInboxRef: DeferredRef<DataInbox>,
            @Provides cloudStorageManagerRef: DeferredRef<CloudStorageManager>,
            @Provides calculationWorkflowRef: DeferredRef<CalculationWorkflow>,
            @Provides decimalFormatterRef: DeferredRef<DecimalFormatter>,
            @Provides processedTbrEbDataRef: DeferredRef<ProcessedTbrEbData>,
            @Provides overviewDataCacheFactoryRef: DeferredRef<OverviewDataCacheFactory>
        ): AppRootGraph
    }

    @Provides fun aapsLogger(r: DeferredRef<AAPSLogger>): AAPSLogger = r.get()
    @Provides fun receiverStatusStore(r: DeferredRef<ReceiverStatusStore>): ReceiverStatusStore = r.get()
    @Provides fun rxBus(r: DeferredRef<RxBus>): RxBus = r.get()
    @Provides fun activePlugin(r: DeferredRef<ActivePlugin>): ActivePlugin = r.get()
    @Provides fun fabricPrivacy(r: DeferredRef<FabricPrivacy>): FabricPrivacy = r.get()
    @Provides fun runningModeExpiryJob(r: DeferredRef<RunningModeExpiryJob>): RunningModeExpiryJob = r.get()
    @Provides fun localAlertUtils(r: DeferredRef<LocalAlertUtils>): LocalAlertUtils = r.get()
    @Provides fun persistenceLayer(r: DeferredRef<PersistenceLayer>): PersistenceLayer = r.get()
    @Provides fun config(r: DeferredRef<Config>): Config = r.get()
    @Provides fun iobCobCalculator(r: DeferredRef<IobCobCalculator>): IobCobCalculator = r.get()
    @Provides fun loop(r: DeferredRef<Loop>): Loop = r.get()
    @Provides fun dateUtil(r: DeferredRef<DateUtil>): DateUtil = r.get()
    @Provides fun profileFunction(r: DeferredRef<ProfileFunction>): ProfileFunction = r.get()
    @Provides fun profileUtil(r: DeferredRef<ProfileUtil>): ProfileUtil = r.get()
    @Provides fun commandQueue(r: DeferredRef<CommandQueue>): CommandQueue = r.get()
    @Provides fun maintenance(r: DeferredRef<Maintenance>): Maintenance = r.get()
    @Provides fun rh(r: DeferredRef<ResourceHelper>): ResourceHelper = r.get()

    /** `ResourceHelper` is the Android implementation of the multiplatform [TextResolver]. */
    @Provides fun textResolver(rh: ResourceHelper): TextResolver = rh

    @Provides fun preferences(r: DeferredRef<Preferences>): Preferences = r.get()
    @Provides fun dstHelper(r: DeferredRef<DstHelper>): DstHelper = r.get()
    @Provides fun workManager(r: DeferredRef<WorkManager>): WorkManager = r.get()
    @Provides fun concentrationHelper(r: DeferredRef<ConcentrationHelper>): ConcentrationHelper = r.get()
    @Provides fun notificationManager(r: DeferredRef<NotificationManager>): NotificationManager = r.get()
    @Provides fun activeSceneManager(r: DeferredRef<ActiveSceneManager>): ActiveSceneManager = r.get()
    @Provides fun sceneExecutor(r: DeferredRef<SceneExecutor>): SceneExecutor = r.get()
    @Provides fun sceneRepository(r: DeferredRef<SceneRepository>): SceneRepository = r.get()
    @Provides fun fileListProvider(r: DeferredRef<FileListProvider>): FileListProvider = r.get()
    @Provides fun storage(r: DeferredRef<Storage>): Storage = r.get()
    @Provides fun userEntryPresentationHelper(r: DeferredRef<UserEntryPresentationHelper>): UserEntryPresentationHelper = r.get()
    @Provides fun dataInbox(r: DeferredRef<DataInbox>): DataInbox = r.get()
    @Provides fun cloudStorageManager(r: DeferredRef<CloudStorageManager>): CloudStorageManager = r.get()
    @Provides fun calculationWorkflow(r: DeferredRef<CalculationWorkflow>): CalculationWorkflow = r.get()
    @Provides fun decimalFormatter(r: DeferredRef<DecimalFormatter>): DecimalFormatter = r.get()
    @Provides fun processedTbrEbData(r: DeferredRef<ProcessedTbrEbData>): ProcessedTbrEbData = r.get()
    @Provides fun overviewDataCacheFactory(r: DeferredRef<OverviewDataCacheFactory>): OverviewDataCacheFactory = r.get()

    /**
     * The application scope. Bound without a qualifier because this graph holds exactly one
     * `CoroutineScope`; there is nothing to tell apart.
     */
    @Provides fun appScope(r: DeferredRef<CoroutineScope>): CoroutineScope = r.get()
}
