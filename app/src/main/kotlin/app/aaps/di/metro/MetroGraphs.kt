package app.aaps.di.metro

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.scenes.Scenes
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.storage.Storage
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.database.di.DatabaseConfig
import app.aaps.di.ExternalOptionsOverride
import app.aaps.di.PluginSource
import app.aaps.di.mergePlugins
import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.core.objects.workflow.MetroWorkerCreator
import app.aaps.database.AppRepository
import app.aaps.implementation.lifecycle.ProcessLifecycleListener
import app.aaps.implementation.resources.ResourceHelperImpl
import app.aaps.implementation.utils.fabric.FabricPrivacyImpl
import app.aaps.implementation.plugin.PluginStore
import app.aaps.implementation.profile.ProfileSwitchExpiryScheduler
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryScheduler
import app.aaps.plugins.aps.loop.runningMode.RunningModeReconciler
import app.aaps.plugins.aps.openAPSAMA.DetermineBasalAMA
import app.aaps.plugins.aps.openAPSAutoISF.DetermineBasalAutoISF
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalSMB
import app.aaps.plugins.automation.AutomationRuntime
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.plugins.source.di.SourceMetroGraph
import app.aaps.plugins.sync.di.OpenHumansMetroBridge
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import app.aaps.ui.activityMonitor.ActivityMonitor
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The object graph, owned by the Application.
 *
 * A thin reader: it builds [AppRootGraph] once and hands out what the extensions below it contain. The
 * only graph it creates separately is Open Humans, which stays a root for the reason written up in
 * [OpenHumansMetroBridge].
 *
 * A single instance is guaranteed by there being one Application, rather than by a scope annotation.
 */
class MetroGraphs(

    private val context: Context,
    private val memberInjector: MetroMemberInjector,
    private val databaseConfig: DatabaseConfig,
    private val externalOptionsOverride: ExternalOptionsOverride
) {


    /**
     * Workers Metro can build, keyed by class name because that is all WorkManager gives us.
     *
     * Resolved on each call rather than cached, so nothing here runs until a worker is really built -
     * WorkManager can initialise during startup, so nothing here runs until a worker is really built.
     */
    /** Built in the module that owns the worker. */
    val runningModeExpiryScheduler: RunningModeExpiryScheduler get() = workers.runningModeExpiryScheduler

    fun workerCreators(): Map<String, MetroWorkerCreator> =
        (workers.workerCreators + openHumans.workerCreators + source.workerCreators)
            .mapKeys { (klass, _) -> klass.java.name }

    /**
     * The one application scope.
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The one Metro root. Sub-graphs are extensions of it rather than roots of their own. */
    private val root: AppRootGraph by lazy {
        createGraphFactory<AppRootGraph.Factory>()
            .create(applicationScope, context, memberInjector, databaseConfig, externalOptionsOverride, CoreObjectsGraph)
    }

    /**
     * The pump types, for code outside `src/withPumps`.
     *
     * Declared in the flavour source sets rather than here, because `src/main` has no pump module on
     * its classpath. Empty in a follower.
     */
    val pumps: PumpAccessors get() = root

    private val source: SourceMetroGraph get() = root.sourceGraph

    private val workers: AppWorkersGraph get() = root.workersGraph

    // The module owns its own bridge, because its DI qualifiers are internal to it.
    // The bridge comes from the root graph.
    private val openHumans: OpenHumansMetroBridge get() = root.openHumansMetroBridge
    /**
     * Builds one history browsing window, with its own calculation objects.
     *
     * A new graph each call, on purpose - the graph instance is what makes the window's objects its
     * own. Everything the window shares with the app arrives deferred, because [ActivePlugin] leads
     * back to the plugin list and so back into these graphs.
     */
    fun newHistoryWindow(): HistoryWindowGraph = root.historyWindowFactory.create()

    /**
     * Fills the `@Inject` fields of an Android class the graph knows about.
     */
    @Suppress("UNCHECKED_CAST")
    fun injectMembers(target: Any): Boolean {
        val injector = openHumans.memberInjectors[target::class]
            ?: source.memberInjectors[target::class]
            // Contributed straight into the root, which is how a pump module reaches this map without
            // MetroGraphs naming it - see `contributedMemberInjectors`.
            ?: root.contributedMemberInjectors[target::class]
            ?: return false
        (injector as MembersInjector<Any>).injectMembers(target)
        return true
    }

    /**
     * The one `ViewModelProvider.Factory`. Built once, from every module graph that contributes view
     * models, so the whole app reads one map.
     */
    val viewModelFactory: MetroViewModelFactory by lazy { AapsViewModelFactory(root, openHumans) }

    /**
     * Plugins contributed by Metro graphs, keyed by order.
     *
     * A real `@IntoMap @IntKey(n)` multibinding built this map at compile time - the same annotation
     * shape, built at compile time.
     */
    fun plugins(): Map<Int, PluginBase> =
        root.contributedPlugins

    /**
     * The whole plugin list, in order - what `PluginStore.plugins` is set to.
     *
     * The qualified buckets are merged **only** under the condition that build should have them.
     * Keeping them apart is what stops a plugin appearing in a build that never had it - a follower
     * showing Objectives, say. `mergePlugins` names which bucket a clashing plugin came from, which is
     * why the sources are listed rather than merged directly.
     */
    fun allPlugins(aapsLogger: AAPSLogger): List<PluginBase> {
        val sources = buildList {
            add(PluginSource("Metro", plugins()))
            if (config.APS) add(PluginSource("Metro @APS", apsPlugins()))
            if (config.PUMPDRIVERS) add(PluginSource("Metro @PumpDriver", pumpDriverPlugins()))
            if (!config.AAPSCLIENT) add(PluginSource("Metro @NotNSClient", notNsClientPlugins()))
        }

        val (plugins, problems) = mergePlugins(sources)
        // Two buckets can still collide on one order key, which loses a plugin silently. Logged rather
        // than thrown: a wrong plugin list must not stop the app from starting, and this is loud enough
        // to find in a log.
        problems.forEach { aapsLogger.error(LTag.CORE, "PLUGIN LIST: $it") }

        return plugins
    }

    /**
     * Plugins that must NOT appear in an AAPSCLIENT build.
     *
     * Kept apart from [plugins] because these carry a `@NotNSClient`
     * qualifier, and [allPlugins] merges that bucket only when the build is not a
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
     * merge this only under `config.PUMPDRIVERS`.
     */
    fun pumpDriverPlugins(): Map<Int, PluginBase> = root.contributedPumpDriverPlugins

    /**
     * Constraint plugins that are also bound to an interface.
     *
     * wrong otherwise.
     */
    val xDripSource: XDripSource get() = root.xdripSourcePlugin
    val dexcomBoyda: DexcomBoyda get() = root.dexcomPlugin
    val bgQualityCheck: BgQualityCheck get() = root.bgQualityCheckPlugin
    val objectives: Objectives get() = root.objectivesPlugin

    val profileFunction: ProfileFunction get() = root.profileFunction
    val versionCheckerUtils: VersionCheckerUtils get() = root.versionCheckerUtils
    val nsIncomingDataProcessor: NsIncomingDataProcessor get() = root.nsIncomingDataProcessor
    val automation: Automation get() = root.automation
    val automationRuntime: AutomationRuntime get() = root.automationRuntime

    /** Same plugin as [objectives], by class. The instrumented tests ask for the concrete type. */
    val objectivesPlugin: ObjectivesPlugin get() = root.objectivesPlugin

    /** The live loop's calculator - not the history browser's. */
    val iobCobCalculator: IobCobCalculator get() = root.iobCobCalculator

    /** The loop. */
    val loop: Loop get() = root.loop
    val autotune: Autotune get() = root.autotune
    val runningModeReconciler: RunningModeReconciler get() = root.runningModeReconciler

    /** The openAPS pieces the instrumented APS tests read. */
    val determineBasalSMB: DetermineBasalSMB get() = root.determineBasalSMB
    val determineBasalAMA: DetermineBasalAMA get() = root.determineBasalAMA
    val determineBasalAutoISF: DetermineBasalAutoISF get() = root.determineBasalAutoISF
    val signatureVerifier: SignatureVerifierPlugin get() = root.signatureVerifierPlugin
    val resourceHelper: ResourceHelper get() = root.resourceHelper
    val fabricPrivacy: FabricPrivacy get() = root.fabricPrivacy
    val uiInteraction: UiInteraction get() = root.uiInteraction
    val preferences: Preferences get() = root.preferences
    val aapsLogger: AAPSLogger get() = root.aapsLogger
    val rxBus: RxBus get() = root.rxBus
    val dateUtil: DateUtil get() = root.dateUtil
    val l: L get() = root.l
    val sp: SP get() = root.sp
    val widgetUpdater: WidgetUpdater get() = root.widgetUpdater
    val commandQueue: CommandQueue get() = root.commandQueue
    val localAlertUtils: LocalAlertUtils get() = root.localAlertUtils
    val persistenceLayer: PersistenceLayer get() = root.persistenceLayer
    val appRepository: AppRepository get() = root.appRepository
    val constraintsChecker: ConstraintsChecker get() = root.constraintsChecker
    val activityMonitor: ActivityMonitor get() = root.activityMonitor
    val notificationManager: NotificationManager get() = root.notificationManager
    val profileSwitchExpiryScheduler: ProfileSwitchExpiryScheduler get() = root.profileSwitchExpiryScheduler
    val activePlugin: ActivePlugin get() = root.activePlugin
    val pluginStore: PluginStore get() = root.pluginStore
    val maintenance: Maintenance get() = root.maintenance
    val fileListProvider: FileListProvider get() = root.fileListProvider
    val sharedPreferences: SharedPreferences get() = root.sharedPreferences
    val exportPasswordDataStore: ExportPasswordDataStore get() = root.exportPasswordDataStore
    val cryptoUtil: CryptoUtil get() = root.cryptoUtil
    val concentrationHelper: ConcentrationHelper get() = root.concentrationHelper
    val processedTbrEbData: ProcessedTbrEbData get() = root.processedTbrEbData
    val configBuilder: ConfigBuilder get() = root.configBuilder
    val scenes: Scenes get() = root.scenes

    /** The one scene state holder, for classes that ask for the concrete type. */
    val decimalFormatter: DecimalFormatter get() = root.decimalFormatter
    val profileUtil: ProfileUtil get() = root.profileUtil
    val hardLimits: HardLimits get() = root.hardLimits
    val storage: Storage get() = root.storage

    val pumpSync: PumpSync get() = root.pumpSync
    val insulinManager: InsulinManager get() = root.insulinManager

    val config: Config get() = root.config
    val resourceHelperImpl: ResourceHelperImpl get() = root.resourceHelperImpl
    val fabricPrivacyImpl: FabricPrivacyImpl get() = root.fabricPrivacyImpl
    val processLifecycleListener: ProcessLifecycleListener get() = root.processLifecycleListener
    val profileRepository: ProfileRepository get() = root.profileRepository
}
