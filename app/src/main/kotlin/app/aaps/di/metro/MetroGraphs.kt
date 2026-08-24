package app.aaps.di.metro

import androidx.work.WorkManager
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.core.interfaces.dst.DstHelper
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.workflow.MetroWorkerCreator
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.ui.compose.MetroViewModelFactory
import app.aaps.ui.compose.overview.OverviewDataCacheFactory
import app.aaps.implementation.scenes.ActiveSceneManager
import app.aaps.implementation.scenes.SceneExecutor
import app.aaps.implementation.scenes.SceneRepository
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryJob
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryScheduler
import app.aaps.plugins.automation.di.AutomationMetroBridge
import app.aaps.plugins.calibration.di.CalibrationGraph
import app.aaps.plugins.sensitivity.di.SensitivityGraph
import app.aaps.plugins.smoothing.di.SmoothingGraph
import app.aaps.plugins.sync.di.OpenHumansMetroBridge
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The Metro half of the object graph, beside Dagger. Counterpart of `KoinGraph` on `koin-spike` and
 * `KotlinInjectGraph` on `kotlin-inject-spike`, written the same way so the three can be compared.
 *
 * The graphs are created lazily and each is created ONCE, because Metro graph factories take real
 * instances. That is the same shape as kotlin-inject and it carries the same hazard: creating a graph
 * resolves every Dagger provider immediately, and Dagger reaches back - `Loop` leads to the plugin
 * list, which asks these graphs. On the kotlin-inject branch that cycle across the framework boundary
 * showed up as a StackOverflowError on device, and neither framework can detect it because neither can
 * see the other's graph.
 *
 * It happened here too - the guess that nothing would touch [coreObjects] until well after startup was
 * simply wrong, and the app died on launch exactly as it did on the kotlin-inject branch. Three
 * frameworks, three identical StackOverflowErrors: this hazard belongs to Dagger-plus-anything
 * coexistence, not to any one framework, and compile-time checking does not help because the cycle is
 * invisible to both sides.
 *
 * [CoreObjectsGraph] therefore takes [DeferredRef] rather than instances. A plain `() -> T` would be
 * the natural way to defer and is what the kotlin-inject branch uses, but Metro treats a parameterless
 * function type as its own provider type and rejects it as a factory parameter ("may not be intrinsic
 * types"), so an ordinary wrapper class is needed instead.
 *
 * The other three graphs take instances because their dependencies are leaves that do not lead back
 * here. That is a judgement, not a guarantee - the same judgement that was wrong once already.
 */
@Singleton
class MetroGraphs @Inject constructor(
    private val aapsLogger: Provider<AAPSLogger>,
    private val rh: Provider<ResourceHelper>,
    private val preferences: Provider<Preferences>,
    private val persistenceLayer: Provider<PersistenceLayer>,
    private val rxBus: Provider<RxBus>,
    private val profileFunction: Provider<ProfileFunction>,
    private val profileUtil: Provider<ProfileUtil>,
    private val constraintsChecker: Provider<ConstraintsChecker>,
    private val loop: Provider<Loop>,
    private val iobCobCalculator: Provider<IobCobCalculator>,
    private val dateUtil: Provider<DateUtil>,
    private val config: Provider<Config>,
    private val uel: Provider<UserEntryLogger>,
    private val automation: Provider<Automation>,
    private val glucoseStatusProvider: Provider<GlucoseStatusProvider>,
    private val processedDeviceStatusData: Provider<ProcessedDeviceStatusData>,
    private val concentrationHelper: Provider<ConcentrationHelper>,
    private val wizardBolusExecutor: Provider<WizardBolusExecutor>,
    @ApplicationScope private val appScope: Provider<CoroutineScope>,
    private val notificationManager: Provider<NotificationManager>,
    private val activePlugin: Provider<ActivePlugin>,
    private val fabricPrivacy: Provider<FabricPrivacy>,
    private val runningModeExpiryJob: Provider<RunningModeExpiryJob>,
    private val localAlertUtils: Provider<LocalAlertUtils>,
    private val commandQueue: Provider<CommandQueue>,
    private val maintenance: Provider<Maintenance>,
    private val dstHelper: Provider<DstHelper>,
    private val workManager: Provider<WorkManager>,
    private val receiverStatusStore: Provider<ReceiverStatusStore>,
    private val openHumansMetroBridge: Provider<OpenHumansMetroBridge>,
    private val automationMetroBridge: Provider<AutomationMetroBridge>,
    private val calculationWorkflow: Provider<CalculationWorkflow>,
    private val decimalFormatter: Provider<DecimalFormatter>,
    private val processedTbrEbData: Provider<ProcessedTbrEbData>,
    private val overviewDataCacheFactory: Provider<OverviewDataCacheFactory>,
    private val activeSceneManager: Provider<ActiveSceneManager>,
    private val sceneExecutor: Provider<SceneExecutor>,
    private val sceneRepository: Provider<SceneRepository>
) {

    private val smoothing: SmoothingGraph by lazy {
        createGraphFactory<SmoothingGraph.Factory>().create(
            aapsLogger = aapsLogger.get(),
            // ResourceHelper is the Android implementation of the multiplatform TextResolver.
            rh = rh.get(),
            preferences = preferences.get(),
            persistenceLayer = persistenceLayer.get()
        )
    }

    private val coreObjects: CoreObjectsGraph by lazy {
        createGraphFactory<CoreObjectsGraph.Factory>().create(
            DeferredRef { aapsLogger.get() },
            DeferredRef { rh.get() },
            DeferredRef { rxBus.get() },
            DeferredRef { preferences.get() },
            DeferredRef { profileFunction.get() },
            DeferredRef { profileUtil.get() },
            DeferredRef { constraintsChecker.get() },
            DeferredRef { loop.get() },
            DeferredRef { iobCobCalculator.get() },
            DeferredRef { dateUtil.get() },
            DeferredRef { config.get() },
            DeferredRef { uel.get() },
            DeferredRef { automation.get() },
            DeferredRef { glucoseStatusProvider.get() },
            DeferredRef { persistenceLayer.get() },
            DeferredRef { processedDeviceStatusData.get() },
            DeferredRef { concentrationHelper.get() },
            DeferredRef { wizardBolusExecutor.get() },
            DeferredRef { appScope.get() }
        )
    }

    private val calibration: CalibrationGraph by lazy {
        createGraphFactory<CalibrationGraph.Factory>().create(
            aapsLogger = aapsLogger.get(),
            rh = rh.get(),
            dateUtil = dateUtil.get(),
            persistenceLayer = persistenceLayer.get(),
            notificationManager = notificationManager.get(),
            glucoseStatusProvider = glucoseStatusProvider.get(),
            rxBus = rxBus.get(),
            profileUtil = profileUtil.get()
        )
    }

    private val sensitivity: SensitivityGraph by lazy {
        createGraphFactory<SensitivityGraph.Factory>().create(
            aapsLogger = aapsLogger.get(),
            rh = rh.get(),
            preferences = preferences.get(),
            dateUtil = dateUtil.get(),
            activePlugin = activePlugin.get()
        )
    }

    private val workers: AppWorkersGraph by lazy {
        createGraphFactory<AppWorkersGraph.Factory>().create(
            DeferredRef { aapsLogger.get() },
            DeferredRef { fabricPrivacy.get() },
            DeferredRef { runningModeExpiryJob.get() },
            DeferredRef { localAlertUtils.get() },
            DeferredRef { persistenceLayer.get() },
            DeferredRef { config.get() },
            DeferredRef { iobCobCalculator.get() },
            DeferredRef { loop.get() },
            DeferredRef { dateUtil.get() },
            DeferredRef { activePlugin.get() },
            DeferredRef { profileFunction.get() },
            DeferredRef { rxBus.get() },
            DeferredRef { commandQueue.get() },
            DeferredRef { maintenance.get() },
            DeferredRef { rh.get() },
            DeferredRef { preferences.get() },
            DeferredRef { dstHelper.get() },
            DeferredRef { workManager.get() },
            DeferredRef { concentrationHelper.get() },
            DeferredRef { notificationManager.get() },
            DeferredRef { activeSceneManager.get() },
            DeferredRef { sceneExecutor.get() },
            DeferredRef { sceneRepository.get() },
            DeferredRef { appScope.get() }
        )
    }

    /**
     * Workers Metro can build, keyed by class name because that is all WorkManager gives us.
     *
     * Resolved on each call rather than cached, so nothing here runs until a worker is really built -
     * WorkManager can initialise during startup and this must not resolve Dagger providers then.
     */
    /** Handed to Dagger consumers - it is built by Metro, in the module that owns the worker. */
    val runningModeExpiryScheduler: RunningModeExpiryScheduler get() = workers.runningModeExpiryScheduler

    fun workerCreators(): Map<String, MetroWorkerCreator> =
        (workers.workerCreators + openHumans.workerCreators).mapKeys { (klass, _) -> klass.java.name }

    private val receivers: AppReceiversGraph by lazy {
        createGraphFactory<AppReceiversGraph.Factory>().create(
            DeferredRef { aapsLogger.get() },
            DeferredRef { receiverStatusStore.get() },
            DeferredRef { rxBus.get() }
        )
    }

    // The module owns its own bridge, because its DI qualifiers are internal to it.
    private val openHumans: OpenHumansMetroBridge get() = openHumansMetroBridge.get()

    /**
     * Builds one history browsing window, with its own calculation objects.
     *
     * A new graph each call, on purpose - the graph instance is what makes the window's objects its
     * own. Everything the window shares with the app arrives deferred, because [ActivePlugin] leads
     * back to the plugin list and so back into these graphs.
     */
    fun newHistoryWindow(): HistoryWindowGraph =
        createGraphFactory<HistoryWindowGraph.Factory>().create(
            DeferredRef { aapsLogger.get() },
            DeferredRef { rxBus.get() },
            DeferredRef { preferences.get() },
            DeferredRef { rh.get() },
            DeferredRef { profileFunction.get() },
            DeferredRef { activePlugin.get() },
            DeferredRef { dateUtil.get() },
            DeferredRef { persistenceLayer.get() },
            DeferredRef { calculationWorkflow.get() },
            DeferredRef { decimalFormatter.get() },
            DeferredRef { processedTbrEbData.get() },
            DeferredRef { overviewDataCacheFactory.get() }
        )

    /**
     * Fills the `@Inject` fields of an Android class Metro knows about - the `HasAndroidInjector`
     * replacement. Returns false when the class has not been converted yet, so the caller falls back
     * to dagger.android.
     */
    @Suppress("UNCHECKED_CAST")
    fun injectMembers(target: Any): Boolean {
        val injector = receivers.memberInjectors[target::class]
            ?: openHumans.memberInjectors[target::class]
            ?: automationMetroBridge.get().memberInjectors[target::class]
            ?: return false
        (injector as MembersInjector<Any>).injectMembers(target)
        return true
    }

    /**
     * The `@HiltViewModel` replacement. Built once, from every module graph that contributes view
     * models - one map, the same way Hilt presents one factory for the whole app.
     */
    val viewModelFactory: MetroViewModelFactory by lazy {
        MetroViewModelFactory(openHumans.viewModelCreators)
    }

    /** Handed back to Dagger consumers that have not moved - Dagger delegates, never constructs. */
    val runningModeGuard: RunningModeGuard get() = coreObjects.runningModeGuard
    val quickWizard: QuickWizard get() = coreObjects.quickWizard
    val bolusWizard: BolusWizard get() = coreObjects.bolusWizard

    /**
     * Plugins contributed by Metro graphs, keyed by order.
     *
     * A real `@IntoMap @IntKey(n)` multibinding built this map at compile time - the same annotation
     * shape the Dagger module used, unlike the Koin branch which had to invent a registration object.
     */
    fun plugins(): Map<Int, PluginBase> = smoothing.plugins + calibration.plugins + sensitivity.plugins

    /**
     * Plugins that must NOT appear in an AAPSCLIENT build.
     *
     * Kept apart from [plugins] because the Dagger bindings these replace carried a `@NotNSClient`
     * qualifier, and `AppModule.providesPlugins` merges that bucket only when the build is not a
     * follower. Merging it unconditionally would quietly add Open Humans to follower builds.
     */
    fun notNsClientPlugins(): Map<Int, PluginBase> = openHumans.notNsClientPlugins
}
