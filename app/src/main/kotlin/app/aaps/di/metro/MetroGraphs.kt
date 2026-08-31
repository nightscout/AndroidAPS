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
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.DeferredRef
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
import app.aaps.core.interfaces.resources.ResourceHelper
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
import app.aaps.core.objects.workflow.MetroWorkerCreator
import app.aaps.core.utils.receivers.DataInbox
import app.aaps.database.AppRepository
import app.aaps.implementation.maintenance.cloud.CloudStorageManager
import app.aaps.implementation.plugin.PluginStore
import app.aaps.implementation.profile.ProfileSwitchExpiryScheduler
import app.aaps.implementation.profile.ProfileSwitchSilentGate
import app.aaps.implementation.scenes.ActiveSceneManager
import app.aaps.implementation.scenes.SceneExecutor
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryJob
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryScheduler
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
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
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
import dev.zacsweers.metro.MembersInjector
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The Metro half of the object graph, beside Dagger. Counterpart of `KoinGraph` on `koin-spike` and
 * `KotlinInjectGraph` on `kotlin-inject-spike`, written the same way so the three can be compared.
 *
 * This is now a thin reader. It builds [AppRootGraph] once and hands out what the extensions below it
 * contain; the only graph it still creates separately is Open Humans, which stays a root for the reason
 * written up in [OpenHumansMetroBridge].
 *
 * ## The hazard this shape exists to avoid
 *
 * Creating a graph must not resolve Dagger providers, because Dagger reaches back - `Loop` leads to the
 * plugin list, which asks these graphs. On the kotlin-inject branch that cycle across the framework
 * boundary showed up as a StackOverflowError on device; it happened here too, when the guess that
 * nothing would touch [coreObjects] until well after startup turned out to be wrong. Three frameworks,
 * three identical StackOverflowErrors: the hazard belongs to Dagger-plus-anything coexistence, not to
 * any one framework, and compile-time checking does not help because the cycle is invisible to both
 * sides.
 *
 * [AapsLeaves] is what breaks it now. Its `@Provides` functions are called only when something asks for
 * that type, so building the root resolves nothing. [DeferredRef], the wrapper that used to do this by
 * hand, survives only for Open Humans - a plain `() -> T` cannot be used because Metro treats a
 * parameterless function type as its own provider type and rejects it as a factory parameter.
 */
@Singleton
class MetroGraphs @Inject constructor(

    private val leaves: Provider<AapsLeaves>,
    @ApplicationContext private val contextProvider: Provider<Context>,
    private val pumpLeaves: Provider<PumpLeaves>
) {


    /**
     * Workers Metro can build, keyed by class name because that is all WorkManager gives us.
     *
     * Resolved on each call rather than cached, so nothing here runs until a worker is really built -
     * WorkManager can initialise during startup and this must not resolve Dagger providers then.
     */
    /** Handed to Dagger consumers - it is built by Metro, in the module that owns the worker. */
    val runningModeExpiryScheduler: RunningModeExpiryScheduler get() = workers.runningModeExpiryScheduler

    fun workerCreators(): Map<String, MetroWorkerCreator> =
        (workers.workerCreators + openHumans.workerCreators + source.workerCreators)
            .mapKeys { (klass, _) -> klass.java.name }

    /**
     * The application scope. Built here rather than borrowed from Dagger, which is what `AapsLeaves`
     * used to do; Dagger consumers now get this same instance back through `CoreObjectsModule`.
     */
    private val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The one Metro root. Sub-graphs are extensions of it rather than roots of their own. */
    private val root: AppRootGraph by lazy {
        createGraphFactory<AppRootGraph.Factory>().create(applicationScope, contextProvider.get(), leaves.get(), CoreObjectsGraph, pumpLeaves.get())
    }

    /**
     * Metro-owned pump types, for the Dagger side.
     *
     * Declared in the flavour source sets rather than here, because `src/main` has no pump module on
     * its classpath - the same split [PumpLeaves] uses, in the opposite direction. Empty in a follower.
     */
    val pumps: PumpAccessors get() = root

    private val source: SourceMetroGraph get() = root.sourceGraph

    private val receivers: AppReceiversGraph get() = root.receiversGraph
    private val workers: AppWorkersGraph get() = root.workersGraph

    // The module owns its own bridge, because its DI qualifiers are internal to it.
    // Metro owns the bridge now, so it comes from the root graph rather than being handed in by Dagger.
    private val openHumans: OpenHumansMetroBridge get() = root.openHumansMetroBridge
    private val automationGraph: AutomationMetroGraph get() = root.automationGraph

    /**
     * Builds one history browsing window, with its own calculation objects.
     *
     * A new graph each call, on purpose - the graph instance is what makes the window's objects its
     * own. Everything the window shares with the app arrives deferred, because [ActivePlugin] leads
     * back to the plugin list and so back into these graphs.
     */
    fun newHistoryWindow(): HistoryWindowGraph = root.historyWindowFactory.create()

    /**
     * Fills the `@Inject` fields of an Android class Metro knows about - the `HasAndroidInjector`
     * replacement. Returns false when the class has not been converted yet, so the caller falls back
     * to dagger.android.
     */
    @Suppress("UNCHECKED_CAST")
    fun injectMembers(target: Any): Boolean {
        val injector = receivers.memberInjectors[target::class]
            ?: openHumans.memberInjectors[target::class]
            ?: automationGraph.memberInjectors[target::class]
            ?: source.memberInjectors[target::class]
            // Contributed straight into the root, which is how a pump module reaches this map without
            // MetroGraphs naming it - see `contributedMemberInjectors`.
            ?: root.contributedMemberInjectors[target::class]
            ?: return false
        (injector as MembersInjector<Any>).injectMembers(target)
        return true
    }

    /**
     * The `@HiltViewModel` replacement. Built once, from every module graph that contributes view
     * models - one map, the same way Hilt presents one factory for the whole app.
     */
    val viewModelFactory: MetroViewModelFactory by lazy { AapsViewModelFactory(root, openHumans) }

    /** Handed back to Dagger consumers that have not moved - Dagger delegates, never constructs. */
    val runningModeGuard: RunningModeGuard get() = root.runningModeGuard
    val quickWizard: QuickWizard get() = root.quickWizard
    val bolusWizard: BolusWizard get() = root.bolusWizard

    /**
     * Plugins contributed by Metro graphs, keyed by order.
     *
     * A real `@IntoMap @IntKey(n)` multibinding built this map at compile time - the same annotation
     * shape the Dagger module used, unlike the Koin branch which had to invent a registration object.
     */
    fun plugins(): Map<Int, PluginBase> =
        root.contributedPlugins

    /**
     * Plugins that must NOT appear in an AAPSCLIENT build.
     *
     * Kept apart from [plugins] because the Dagger bindings these replace carried a `@NotNSClient`
     * qualifier, and `AppModule.providesPlugins` merges that bucket only when the build is not a
     * follower. Merging it unconditionally would quietly add Open Humans to follower builds.
     */
    fun notNsClientPlugins(): Map<Int, PluginBase> =
        openHumans.notNsClientPlugins + root.contributedNotNsClientPlugins

    /**
     * Plugins that only belong in a build that runs the loop.
     *
     * Same reasoning as [notNsClientPlugins], for the `@APS` qualifier. Objectives, the signature
     * verifier and the storage constraint have no meaning in a build that never makes a decision.
     */
    fun apsPlugins(): Map<Int, PluginBase> = root.contributedApsPlugins

    /**
     * Pump drivers, merged only by a build that has them.
     *
     * Empty in a follower, because no pump module is on that classpath to contribute - so the caller can
     * merge this under the same `config.PUMPDRIVERS` condition as the Dagger bucket beside it.
     */
    fun pumpDriverPlugins(): Map<Int, PluginBase> = root.contributedPumpDriverPlugins

    /**
     * Constraint plugins that are also bound to an interface, handed to Dagger in `CoreObjectsModule`.
     *
     * Metro builds these, so Dagger must delegate rather than construct - see there for what goes
     * wrong otherwise.
     */
    val xDripSource: XDripSource get() = root.xdripSourcePlugin
    val nsClientSource: NSClientSource get() = root.nsClientSource
    val dexcomBoyda: DexcomBoyda get() = root.dexcomPlugin
    val bgQualityCheck: BgQualityCheck get() = root.bgQualityCheckPlugin
    val dstHelper: DstHelper get() = root.dstHelper
    val objectives: Objectives get() = root.objectivesPlugin

    val profileFunction: ProfileFunction get() = root.profileFunction
    val versionCheckerUtils: VersionCheckerUtils get() = root.versionCheckerUtils
    val nsClient: NsClient get() = root.nsClient
    val clientControlActionDispatcher: ClientControlActionDispatcher get() = root.clientControlActionDispatcher
    val workManager: WorkManager get() = root.workManager
    val nsClientV3Plugin: NSClientV3Plugin get() = root.nsClientV3Plugin
    val receiverDelegate: ReceiverDelegate get() = root.receiverDelegate
    val authorizedClientsRepository: AuthorizedClientsRepository get() = root.authorizedClientsRepository
    val clientControlPublisher: ClientControlPublisher get() = root.clientControlPublisher
    val clientPairingRepository: ClientPairingRepository get() = root.clientPairingRepository
    val pairingOfferFetcher: PairingOfferFetcher get() = root.pairingOfferFetcher
    val pairingOfferPublisher: PairingOfferPublisher get() = root.pairingOfferPublisher
    val smsCommunicatorPlugin: SmsCommunicatorPlugin get() = root.smsCommunicatorPlugin
    val smsCommunicatorRepository: SmsCommunicatorRepository get() = root.smsCommunicatorRepository
    val authFlowOut: AuthFlowOut get() = root.authFlowOut
    val tidepoolUploader: TidepoolUploader get() = root.tidepoolUploader
    val tidepoolRepository: TidepoolRepository get() = root.tidepoolRepository
    val rateLimit: RateLimit get() = root.rateLimit
    val wearPlugin: WearPlugin get() = root.wearPlugin
    val xdripMvvmRepository: XdripMvvmRepository get() = root.xdripMvvmRepository
    val nsIncomingDataProcessor: NsIncomingDataProcessor get() = root.nsIncomingDataProcessor
    val automation: Automation get() = root.automation
    val automationRuntime: AutomationRuntime get() = root.automationRuntime
    val permissionProviders: Set<PermissionProvider> get() = root.permissionProviders

    /** Same plugin as [objectives], by class. The instrumented tests ask for the concrete type. */
    val objectivesPlugin: ObjectivesPlugin get() = root.objectivesPlugin

    /** The live loop's calculator, for the Dagger consumers - not the history browser's. */
    val iobCobCalculator: IobCobCalculator get() = root.iobCobCalculator

    /** The loop, for the Dagger half. */
    val loop: Loop get() = root.loop
    val autotune: Autotune get() = root.autotune
    val runningModeReconciler: RunningModeReconciler get() = root.runningModeReconciler
    val runningModeExpiryJob: RunningModeExpiryJob get() = root.runningModeExpiryJob

    /** openAPS pieces Metro builds; only those the instrumented APS tests inject through Dagger. */
    val glucoseStatusCalculatorSMB: GlucoseStatusCalculatorSMB get() = root.glucoseStatusCalculatorSMB
    val determineBasalSMB: DetermineBasalSMB get() = root.determineBasalSMB
    val determineBasalAMA: DetermineBasalAMA get() = root.determineBasalAMA
    val determineBasalAutoISF: DetermineBasalAutoISF get() = root.determineBasalAutoISF
    val glucoseStatusCalculatorAutoIsf: GlucoseStatusCalculatorAutoIsf get() = root.glucoseStatusCalculatorAutoIsf
    val deltaCalculator: DeltaCalculator get() = root.deltaCalculator
    val signatureVerifier: SignatureVerifierPlugin get() = root.signatureVerifierPlugin
    val trendCalculator: TrendCalculator get() = root.trendCalculator
    val resourceHelper: ResourceHelper get() = root.resourceHelper
    val fabricPrivacy: FabricPrivacy get() = root.fabricPrivacy
    val uiInteraction: UiInteraction get() = root.uiInteraction
    val carbSuggestionActions: CarbSuggestionActions get() = root.carbSuggestionActions
    val temporaryBasalStorage: TemporaryBasalStorage get() = root.temporaryBasalStorage
    val detailedBolusInfoStorage: DetailedBolusInfoStorage get() = root.detailedBolusInfoStorage
    val blePreCheck: BlePreCheck get() = root.blePreCheck
    val visibilityContext: VisibilityContext get() = root.visibilityContext
    val cloudDirectoryManager: CloudDirectoryManager get() = root.cloudDirectoryManager
    val graphConfigRepository: GraphConfigRepository get() = root.graphConfigRepository
    val batchExecutor: BatchExecutor get() = root.batchExecutor
    val wizardBolusExecutor: WizardBolusExecutor get() = root.wizardBolusExecutor
    val loggerUtils: LoggerUtils get() = root.loggerUtils
    val importExportPrefs: ImportExportPrefs get() = root.importExportPrefs
    val preferences: Preferences get() = root.preferences
    val pumpWithConcentration: PumpWithConcentration get() = root.pumpWithConcentration
    val calculationWorkflow: CalculationWorkflow get() = root.calculationWorkflow
    val workflowChainData: WorkflowChainData get() = root.workflowChainData
    val aapsLogger: AAPSLogger get() = root.aapsLogger
    val rxBus: RxBus get() = root.rxBus
    val dateUtil: DateUtil get() = root.dateUtil
    val l: L get() = root.l
    val aapsSchedulers: AapsSchedulers get() = root.aapsSchedulers
    val sp: SP get() = root.sp
    val widgetUpdater: WidgetUpdater get() = root.widgetUpdater
    val sceneIconResolver: SceneIconResolver get() = root.sceneIconResolver
    val processedDeviceStatusData: ProcessedDeviceStatusData get() = root.processedDeviceStatusData
    val lastLocationDataContainer: LastLocationDataContainer get() = root.lastLocationDataContainer
    val storeDataForDb: StoreDataForDb get() = root.storeDataForDb
    val sceneExecutor: SceneExecutor get() = root.sceneExecutor
    val dataInbox: DataInbox get() = root.dataInbox
    val autosensData: AutosensData get() = root.autosensData
    val commandQueue: CommandQueue get() = root.commandQueue
    val localAlertUtils: LocalAlertUtils get() = root.localAlertUtils
    val bolusProgressData: BolusProgressData get() = root.bolusProgressData
    val persistenceLayer: PersistenceLayer get() = root.persistenceLayer
    val cloudStorageManager: CloudStorageManager get() = root.cloudStorageManager
    val overviewDataCache: OverviewDataCache get() = root.overviewDataCache
    val calculationSignals: CalculationSignals get() = root.calculationSignals
    val calculationSignalsEmitter: CalculationSignalsEmitter get() = root.calculationSignalsEmitter
    val appRepository: AppRepository get() = root.appRepository
    val cloudStorageProviders: Set<CloudStorageProvider> get() = root.cloudStorageProviders
    val constraintsChecker: ConstraintsChecker get() = root.constraintsChecker
    val nsClientRepository: NSClientRepository get() = root.nsClientRepository
    val builtInSearchables: BuiltInSearchables get() = root.builtInSearchables
    val activityMonitor: ActivityMonitor get() = root.activityMonitor
    val appScope: CoroutineScope get() = root.appScope
    val notificationManager: NotificationManager get() = root.notificationManager
    val apsResult: APSResult get() = root.apsResult
    val pumpEnactResult: PumpEnactResult get() = root.pumpEnactResult
    val profileSwitchSilentGate: ProfileSwitchSilentGate get() = root.profileSwitchSilentGate
    val profileSwitchExpiryScheduler: ProfileSwitchExpiryScheduler get() = root.profileSwitchExpiryScheduler
    val runningConfiguration: RunningConfiguration get() = root.runningConfiguration
    val runningConfigurationKeys: RunningConfigurationKeys get() = root.runningConfigurationKeys
    val activePlugin: ActivePlugin get() = root.activePlugin
    val pluginPermissions: PluginPermissions get() = root.pluginPermissions
    val pluginStore: PluginStore get() = root.pluginStore
    val xDripBroadcast: XDripBroadcast get() = root.xDripBroadcast
    val maintenance: Maintenance get() = root.maintenance
    val fileListProvider: FileListProvider get() = root.fileListProvider
    val lastBgData: LastBgData get() = root.lastBgData
    val localeDependentSetting: LocaleDependentSetting get() = root.localeDependentSetting
    val pumpStatusProvider: PumpStatusProvider get() = root.pumpStatusProvider
    val passwordCheck: PasswordCheck get() = root.passwordCheck
    val overviewData: OverviewData get() = root.overviewData
    val sharedPreferences: SharedPreferences get() = root.sharedPreferences
    val exportPasswordDataStore: ExportPasswordDataStore get() = root.exportPasswordDataStore
    val secureEncrypt: SecureEncrypt get() = root.secureEncrypt
    val cryptoUtil: CryptoUtil get() = root.cryptoUtil
    val concentrationHelper: ConcentrationHelper get() = root.concentrationHelper
    val processedTbrEbData: ProcessedTbrEbData get() = root.processedTbrEbData
    val userEntryLogger: UserEntryLogger get() = root.userEntryLogger
    val glucoseStatusProvider: GlucoseStatusProvider get() = root.glucoseStatusProvider
    val notificationHolder: NotificationHolder get() = root.notificationHolder
    val userEntryPresentationHelper: UserEntryPresentationHelper get() = root.userEntryPresentationHelper
    val profiler: Profiler get() = root.profiler
    val alarmSoundPlayer: AlarmSoundPlayer get() = root.alarmSoundPlayer
    val wizardExecutor: WizardExecutor get() = root.wizardExecutor
    val configBuilder: ConfigBuilder get() = root.configBuilder
    val dataSyncSelectorXdrip: DataSyncSelectorXdrip get() = root.dataSyncSelectorXdrip
    val activeSceneSync: ActiveSceneSync get() = root.activeSceneSync
    val sceneChainResolver: SceneChainResolver get() = root.sceneChainResolver
    val sceneStore: SceneStore get() = root.sceneStore
    val scenes: Scenes get() = root.scenes
    val sceneActions: SceneActions get() = root.sceneActions

    /** Metro's one scene state holder, for the Dagger-built classes that ask for the concrete type. */
    val activeSceneManager: ActiveSceneManager get() = root.activeSceneManager
    val sceneAutomationApi: SceneAutomationApi get() = root.sceneAutomationApi
    val decimalFormatter: DecimalFormatter get() = root.decimalFormatter
    val profileUtil: ProfileUtil get() = root.profileUtil
    val hardLimits: HardLimits get() = root.hardLimits
    val storage: Storage get() = root.storage
    val receiverStatusStore: ReceiverStatusStore get() = root.receiverStatusStore

    val translator: Translator get() = root.translator
    val protectionCheck: ProtectionCheck get() = root.protectionCheck
    val tddCalculator: TddCalculator get() = root.tddCalculator
    val tirCalculator: TirCalculator get() = root.tirCalculator
    val dexcomTirCalculator: DexcomTirCalculator get() = root.dexcomTirCalculator
    val pumpSync: PumpSync get() = root.pumpSync
    val iconsProvider: IconsProvider get() = root.iconsProvider
    val insulinManager: InsulinManager get() = root.insulinManager
    val profileRepository: ProfileRepository get() = root.profileRepository
    val profileStore: ProfileStore get() = root.profileStore
}
