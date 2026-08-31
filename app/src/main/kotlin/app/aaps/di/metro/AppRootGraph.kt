package app.aaps.di.metro

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.configuration.RunningConfigurationKeys
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.APS
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.core.interfaces.di.NotNSClient
import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.dst.DstHelper
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.local.LocaleDependentSetting
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.LoggerUtils
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.maintenance.CloudDirectoryManager
import app.aaps.core.interfaces.maintenance.CloudStorageProvider
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.overview.LastBgData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.GraphConfigRepository
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PermissionProvider
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginPermissions
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpStatusProvider
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.scenes.ActiveSceneSync
import app.aaps.core.interfaces.scenes.SceneActions
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneChainResolver
import app.aaps.core.interfaces.scenes.SceneIconResolver
import app.aaps.core.interfaces.scenes.SceneStore
import app.aaps.core.interfaces.scenes.Scenes
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.stats.DexcomTirCalculator
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.storage.Storage
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.core.interfaces.ui.CarbSuggestionActions
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.userEntry.UserEntryPresentationHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.core.interfaces.workflow.CalculationSignals
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.utils.receivers.DataInbox
import app.aaps.database.AppRepository
import app.aaps.implementation.maintenance.cloud.CloudStorageManager
import app.aaps.implementation.plugin.PluginStore
import app.aaps.implementation.profile.ProfileSwitchExpiryScheduler
import app.aaps.implementation.profile.ProfileSwitchSilentGate
import app.aaps.implementation.scenes.ActiveSceneManager
import app.aaps.implementation.scenes.SceneExecutor
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryJob
import app.aaps.plugins.aps.loop.runningMode.RunningModeReconciler
import app.aaps.plugins.aps.openAPS.DeltaCalculator
import app.aaps.plugins.aps.openAPSAMA.DetermineBasalAMA
import app.aaps.plugins.aps.openAPSAutoISF.DetermineBasalAutoISF
import app.aaps.plugins.aps.openAPSAutoISF.GlucoseStatusCalculatorAutoIsf
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalSMB
import app.aaps.plugins.aps.openAPSSMB.GlucoseStatusCalculatorSMB
import app.aaps.plugins.automation.AutomationRuntime
import app.aaps.plugins.automation.di.AutomationMetroGraph
import app.aaps.plugins.automation.services.LastLocationDataContainer
import app.aaps.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import app.aaps.plugins.constraints.dstHelper.DstHelperPlugin
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.objectives.objectives.Objective
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.NSClientSourcePlugin
import app.aaps.plugins.source.XdripSourcePlugin
import app.aaps.plugins.source.di.SourceMetroGraph
import app.aaps.plugins.sync.di.OpenHumansMetroBridge
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclientV3.ReceiverDelegate
import app.aaps.plugins.sync.nsclientV3.clientcontrol.AuthorizedClientsRepository
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientControlPublisher
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientPairingRepository
import app.aaps.plugins.sync.nsclientV3.clientcontrol.PairingOfferFetcher
import app.aaps.plugins.sync.nsclientV3.clientcontrol.PairingOfferPublisher
import app.aaps.plugins.sync.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.sync.smsCommunicator.compose.SmsCommunicatorRepository
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import app.aaps.plugins.sync.tidepool.compose.TidepoolRepository
import app.aaps.plugins.sync.tidepool.utils.RateLimit
import app.aaps.plugins.sync.wear.WearPlugin
import app.aaps.plugins.sync.xdrip.compose.XdripMvvmRepository
import app.aaps.ui.activityMonitor.ActivityMonitor
import app.aaps.ui.search.BuiltInSearchables
import app.aaps.workflow.WorkflowChainData
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.MetroViewModelMultibindings
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass
import javax.inject.Singleton

/**
 * The one Metro root. Everything else hangs off it as a graph extension.
 *
 * Before this there were nine independent root graphs, each with its own factory restating the
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
 * The app-wide objects arrive as one [AapsLeaves] container because they still belong to Dagger. Its
 * `@Provides` functions run only on demand, which is the re-entrancy guard written up in [MetroGraphs].
 * When Dagger is gone the factory below goes with it and these become ordinary bindings.
 */
// Two scopes on purpose. AppScope is Metro's own; javax @Singleton is declared as well because, with
// Dagger interop on, Metro READS the javax scope on classes from other modules. Without this the graph
// refuses any @Singleton class with "may not reference bindings from different scopes" - and with it,
// an existing class can be contributed without retagging its scope annotation at all.
@Singleton
/**
 * [PumpAccessors] is a supertype rather than a `@ContributesTo` interface: a contributed interface
 * reaches the *generated* graph, so `root as PumpAccessors` would only work at runtime. Extending it
 * makes the accessors part of this type, and it compiles for every flavour because both flavour source
 * sets declare a `PumpAccessors` (empty in a follower), exactly as they both declare a `PumpLeaves`.
 */
@DependencyGraph(AppScope::class)
interface AppRootGraph : MetroViewModelMultibindings, PumpAccessors {

    /** Android classes that fill their own fields. */
    val receiversGraph: AppReceiversGraph

    /** Workers, which WorkManager builds through `MetroWorkerFactory`. */
    val workersGraph: AppWorkersGraph

    /**
     * Opens one history browsing window. A factory rather than an accessor, because each window is a
     * separate scope with its own calculation objects - see [HistoryWindowGraph].
     */
    val historyWindowFactory: HistoryWindowGraph.Factory

    /**
     * Feature modules, as extensions rather than roots of their own.
     *
     * Each of these used to be a second `@DependencyGraph(AppScope::class)`, built by `MetroGraphs`
     * with its own list of leaves. That was safe only by accident: two graphs both declaring `AppScope`
     * get a separate copy of anything scoped there, and nothing reports it. As extensions they share
     * this graph's bindings instead of restating them, so their factories take no arguments at all.
     */
    /**
     * The shared wizard and running-mode objects, from a binding container in `:core:objects`.
     *
     * It used to be a `@GraphExtension`, which put its bindings out of reach: an extension can see its
     * parent, but the parent cannot see the extension, so anything contributed here could not depend on
     * a wizard. As a container its bindings are part of this graph, and it still lives in commonMain
     * where the Apple targets can include it too.
     */
    val runningModeGuard: RunningModeGuard
    val quickWizard: QuickWizard
    val bolusWizard: BolusWizard

    /**
     * Plugins that register themselves with @ContributesIntoMap rather than being listed in a graph.
     *
     * Smoothing, sensitivity and calibration are on this shape: their plugins carry the binding on the
     * class, so those modules have no DI file at all. As other modules follow, their entries land here.
     */
    val contributedPlugins: Map<Int, PluginBase>

    /**
     * Contributed plugins that only belong in a build that runs the loop.
     *
     * The qualifier is the whole point: `:app` merges this bucket only when `config.APS`. Objectives,
     * the signature verifier and the storage constraint have no meaning in a build that never makes a
     * decision, and a plugin in the wrong bucket fails silently - a plugin list is just a list.
     */
    @APS
    val contributedApsPlugins: Map<Int, PluginBase>

    /** Contributed plugins that must NOT appear in a follower build. */
    @NotNSClient
    val contributedNotNsClientPlugins: Map<Int, PluginBase>

    /**
     * Pump drivers, which only a build with pump support merges.
     *
     * Safe to declare here even though this file is compiled for follower builds: the map is keyed on
     * `Int` and holds [PluginBase], so no pump type is named. A follower has no pump module on its
     * classpath, nothing contributes, and the map is empty - which is the correct answer for a follower.
     *
     * `@Multibinds(allowEmpty = true)` is what makes that legal. Without it Metro reports "no binding
     * found" for the follower flavours, because a map with no contributions has no binding at all.
     */
    @Multibinds(allowEmpty = true)
    @PumpDriver
    val contributedPumpDriverPlugins: Map<Int, PluginBase>

    /**
     * Member injectors contributed by feature modules, for classes the app constructs by hand.
     *
     * The four extensions below the root each carry their own such map, but an extension has to be named
     * by [MetroGraphs] to be read - and that file is compiled for follower builds, where no pump module
     * exists. A map declared here instead can be filled from anywhere with `@ContributesTo`, and nothing
     * pump-specific appears in its type, so a follower simply sees fewer entries.
     *
     * This is what replaces `dagger.android`'s `HasAndroidInjector` for the pump protocol classes: a
     * packet is built with `new`, then fills its own fields from this map.
     */
    @Multibinds(allowEmpty = true)
    @FeatureMemberInjectors
    val contributedMemberInjectors: Map<KClass<*>, MembersInjector<*>>

    /**
     * The ten objectives, in order. `ObjectivesPlugin` takes this list and is built here, so the
     * objectives are contributed to this graph too.
     */
    @Provides
    fun objectivesList(objectives: Map<Int, Objective>): List<Objective> =
        objectives.toList().sortedBy { it.first }.map { it.second }

    /**
     * Constraint plugins that are also bound to an interface, or injected directly. Dagger delegates
     * to these instances in `CoreObjectsModule` rather than building its own.
     */
    /**
     * Objects Metro builds that Dagger consumers still ask for. Each has a @Provides delegate in
     * `CoreObjectsModule`; Dagger must never construct its own, or there would be two.
     */
    val trendCalculator: TrendCalculator
    val resourceHelper: ResourceHelper
    val fabricPrivacy: FabricPrivacy
    val uiInteraction: UiInteraction
    val carbSuggestionActions: CarbSuggestionActions
    val temporaryBasalStorage: TemporaryBasalStorage
    val detailedBolusInfoStorage: DetailedBolusInfoStorage
    val blePreCheck: BlePreCheck
    val visibilityContext: VisibilityContext
    val cloudDirectoryManager: CloudDirectoryManager
    val graphConfigRepository: GraphConfigRepository
    val batchExecutor: BatchExecutor
    val wizardBolusExecutor: WizardBolusExecutor
    val loggerUtils: LoggerUtils
    val importExportPrefs: ImportExportPrefs
    val preferences: Preferences
    val pumpWithConcentration: PumpWithConcentration
    val calculationWorkflow: CalculationWorkflow
    val workflowChainData: WorkflowChainData
    val aapsLogger: AAPSLogger
    val rxBus: RxBus
    val dateUtil: DateUtil
    val l: L
    val aapsSchedulers: AapsSchedulers
    val sp: SP
    val widgetUpdater: WidgetUpdater
    val sceneIconResolver: SceneIconResolver
    val processedDeviceStatusData: ProcessedDeviceStatusData
    val lastLocationDataContainer: LastLocationDataContainer
    val storeDataForDb: StoreDataForDb
    val sceneExecutor: SceneExecutor
    val dataInbox: DataInbox
    val autosensData: AutosensData
    val commandQueue: CommandQueue
    val localAlertUtils: LocalAlertUtils
    val bolusProgressData: BolusProgressData
    val persistenceLayer: PersistenceLayer
    val cloudStorageManager: CloudStorageManager
    val overviewDataCache: OverviewDataCache
    val calculationSignals: CalculationSignals
    val calculationSignalsEmitter: CalculationSignalsEmitter
    val appRepository: AppRepository
    val cloudStorageProviders: Set<CloudStorageProvider>
    val constraintsChecker: ConstraintsChecker
    val nsClientRepository: NSClientRepository
    val builtInSearchables: BuiltInSearchables

    /** Metro builds it now, but `MainApp` still injects it through Dagger, so it is handed back. */
    val activityMonitor: ActivityMonitor

    /** The one application scope. Metro owns it; Dagger consumers get this same instance. */
    @ApplicationScope val appScope: CoroutineScope
    val notificationManager: NotificationManager
    val apsResult: APSResult
    val pumpEnactResult: PumpEnactResult
    val profileSwitchSilentGate: ProfileSwitchSilentGate
    val profileSwitchExpiryScheduler: ProfileSwitchExpiryScheduler
    val runningConfiguration: RunningConfiguration
    val runningConfigurationKeys: RunningConfigurationKeys
    val activePlugin: ActivePlugin
    val pluginPermissions: PluginPermissions
    val pluginStore: PluginStore
    val xDripBroadcast: XDripBroadcast
    val maintenance: Maintenance
    val fileListProvider: FileListProvider
    val lastBgData: LastBgData
    val localeDependentSetting: LocaleDependentSetting
    val pumpStatusProvider: PumpStatusProvider
    val passwordCheck: PasswordCheck
    val overviewData: OverviewData
    val sharedPreferences: SharedPreferences
    val exportPasswordDataStore: ExportPasswordDataStore
    val secureEncrypt: SecureEncrypt
    val cryptoUtil: CryptoUtil
    val concentrationHelper: ConcentrationHelper
    val processedTbrEbData: ProcessedTbrEbData
    val userEntryLogger: UserEntryLogger
    val glucoseStatusProvider: GlucoseStatusProvider
    val notificationHolder: NotificationHolder
    val userEntryPresentationHelper: UserEntryPresentationHelper
    val profiler: Profiler
    val alarmSoundPlayer: AlarmSoundPlayer
    val wizardExecutor: WizardExecutor
    val configBuilder: ConfigBuilder
    val dataSyncSelectorXdrip: DataSyncSelectorXdrip
    val activeSceneSync: ActiveSceneSync

    /**
     * The same object as [activeSceneSync], by class.
     *
     * `SceneExecutor`, `SceneAutomationApiImpl` and `SceneExpiryRunner` all ask for the concrete type and
     * are built by Dagger, so without this they got a copy of their own - and an unscoped one, since the
     * class carries only Metro's `@SingleIn`. The scene then activated on an object no screen was reading.
     */
    val activeSceneManager: ActiveSceneManager
    val sceneChainResolver: SceneChainResolver
    val sceneStore: SceneStore
    val scenes: Scenes
    val sceneActions: SceneActions
    val sceneAutomationApi: SceneAutomationApi
    val decimalFormatter: DecimalFormatter
    val profileUtil: ProfileUtil
    val hardLimits: HardLimits
    val nsIncomingDataProcessor: NsIncomingDataProcessor
    val openHumansMetroBridge: OpenHumansMetroBridge
    val xdripMvvmRepository: XdripMvvmRepository
    val wearPlugin: WearPlugin
    val rateLimit: RateLimit
    val tidepoolRepository: TidepoolRepository
    val tidepoolUploader: TidepoolUploader
    val authFlowOut: AuthFlowOut
    val smsCommunicatorRepository: SmsCommunicatorRepository
    val smsCommunicatorPlugin: SmsCommunicatorPlugin
    val pairingOfferPublisher: PairingOfferPublisher
    val pairingOfferFetcher: PairingOfferFetcher
    val clientPairingRepository: ClientPairingRepository
    val clientControlPublisher: ClientControlPublisher
    val authorizedClientsRepository: AuthorizedClientsRepository
    val receiverDelegate: ReceiverDelegate
    val nsClientV3Plugin: NSClientV3Plugin
    val workManager: WorkManager
    val clientControlActionDispatcher: ClientControlActionDispatcher
    val nsClient: NsClient
    val profileFunction: ProfileFunction
    val versionCheckerUtils: VersionCheckerUtils
    val storage: Storage
    val receiverStatusStore: ReceiverStatusStore

    val translator: Translator
    val protectionCheck: ProtectionCheck
    val tddCalculator: TddCalculator
    val tirCalculator: TirCalculator
    val dexcomTirCalculator: DexcomTirCalculator
    val pumpSync: PumpSync
    val iconsProvider: IconsProvider
    val insulinManager: InsulinManager
    val profileRepository: ProfileRepository
    val profileStore: ProfileStore

    val bgQualityCheckPlugin: BgQualityCheckPlugin
    val dstHelperPlugin: DstHelperPlugin
    val dstHelper: DstHelper
    val objectivesPlugin: ObjectivesPlugin

    /**
     * Automation, and the permission providers it is the only contributor to.
     *
     * Metro owns `AutomationRuntime` now, so Dagger reads it back through `CoreObjectsModule`
     * instead of the other way round.
     */
    val automation: Automation

    /** Same object as [automation], by class: ComposeMainActivity and AppNavGraph inject the concrete type. */
    val automationRuntime: AutomationRuntime
    val permissionProviders: Set<PermissionProvider>

    /** The live loop's calculator. A history window has its own, at `HistoryWindowScope`. */
    val iobCobCalculator: IobCobCalculator

    /** The loop. Built here now; Dagger receives it through `CoreObjectsModule.provideLoop`. */
    val loop: Loop

    /** Autotune, for the automation actions Dagger still builds. */
    val autotune: Autotune

    /** Running-mode helpers, from commonMain - `MainApp` still injects the reconciler through Dagger. */
    val runningModeReconciler: RunningModeReconciler
    val runningModeExpiryJob: RunningModeExpiryJob

    /**
     * openAPS pieces Metro builds, for the Dagger side to borrow.
     *
     * Only the four the instrumented APS tests inject. `DeltaCalculator` and the AutoISF glucose status
     * calculator are reached through these, so nothing outside Metro ever asks for them by name.
     */
    val glucoseStatusCalculatorSMB: GlucoseStatusCalculatorSMB
    val determineBasalSMB: DetermineBasalSMB
    val determineBasalAMA: DetermineBasalAMA
    val determineBasalAutoISF: DetermineBasalAutoISF
    val glucoseStatusCalculatorAutoIsf: GlucoseStatusCalculatorAutoIsf
    val deltaCalculator: DeltaCalculator
    val signatureVerifierPlugin: SignatureVerifierPlugin

    /**
     * Source plugins that are also bound to an interface for other callers.
     *
     * Metro builds these, so Dagger must delegate rather than construct - see `CoreObjectsModule`.
     */
    val xdripSourcePlugin: XdripSourcePlugin
    val nsClientSourcePlugin: NSClientSourcePlugin
    val nsClientSource: NSClientSource
    val dexcomPlugin: DexcomPlugin

    val sourceGraph: SourceMetroGraph
    val automationGraph: AutomationMetroGraph

    /**
     * Interfaces backed by a plugin this graph already builds. A `@Provides` rather than a delegate,
     * because the instance is the plugin - binding it any other way would make a second one.
     */
    @Provides fun dexcomBoyda(plugin: DexcomPlugin): DexcomBoyda = plugin

    /** Metro already builds the plugin; the openAPS plugins ask for the interface. */
    @Provides fun bgQualityCheck(plugin: BgQualityCheckPlugin): BgQualityCheck = plugin

    @Provides fun xDripSource(plugin: XdripSourcePlugin): XDripSource = plugin

    @DependencyGraph.Factory
    fun interface Factory {

        /**
         * One parameter, not thirty-five. [AapsLeaves] carries everything Dagger still owns, and its
         * @Provides functions are called only when something needs that type - so the deferral that
         * DeferredRef used to do by hand is now just the shape of a binding container.
         */
        fun create(
            /**
             * The application scope, passed in rather than built here so the caller decides its
             * dispatcher: production uses `Dispatchers.Default`, the unit tests an Unconfined one, so
             * that work started while the graph is being built runs on the calling thread.
             */
            @Provides @ApplicationScope appScope: CoroutineScope,

            /** The application context. Android owns it, so it is passed in rather than bound. */
            @Provides context: Context,
            @Includes leaves: AapsLeaves,
            @Includes coreObjects: CoreObjectsGraph
        ): AppRootGraph
    }
}
