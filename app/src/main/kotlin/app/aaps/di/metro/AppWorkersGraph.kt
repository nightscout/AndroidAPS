package app.aaps.di.metro

import androidx.work.WorkManager
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.core.interfaces.dst.DstHelper
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.MetroWorkerCreator
import app.aaps.implementation.receivers.KeepAliveWorker
import app.aaps.implementation.scenes.ActiveSceneManager
import app.aaps.implementation.scenes.SceneExecutor
import app.aaps.implementation.scenes.SceneExpiryWorker
import app.aaps.implementation.scenes.SceneRepository
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryJob
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryScheduler
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryWorker
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

/**
 * Metro wiring for workers, replacing what `@HiltWorker` does today.
 *
 * This graph exists to answer one question: can Metro build an Android entry point that the framework
 * constructs for us? A worker is the hardest of the four, because WorkManager holds the constructor
 * call and only hands over a class name at runtime. The answer is the map below - a real compile-time
 * `@IntoMap` multibinding keyed by worker class, which [MetroWorkerFactory] looks up.
 *
 * Two workers are wired here on purpose. [RunningModeExpiryWorker] is small and now lives in the
 * multiplatform module that owns its job, which Dagger could not have done.
 * [KeepAliveWorker] takes nineteen dependencies, lives in another module, and runs every fifteen
 * minutes - so it both stresses the wiring and reports on itself from the device log.
 *
 * Workers are Android-only and always will be, so this graph stays in `:app` rather than moving to
 * commonMain. It is here to prove Hilt can be removed, not to reach iOS.
 *
 * The long parameter list is the price of the bridge, not of Metro. Every dependency has to be handed
 * over from Dagger one by one because Dagger owns these objects today. When Dagger is gone the factory
 * goes with it and the bindings below stand on their own.
 *
 * Dependencies arrive as [DeferredRef] for the reason written up in [MetroGraphs]: WorkManager can
 * initialise during startup, and resolving Dagger providers at graph-creation time is what produced a
 * StackOverflowError on all three spike branches. [Loop] and [ActivePlugin] in this list are exactly
 * the two that lead back into the plugin list, so this is not a theoretical concern here.
 */
@DependencyGraph(AppScope::class)
interface AppWorkersGraph {

    /**
     * Schedules [RunningModeExpiryWorker]. It lives in the same multiplatform module as the worker
     * and the job now, so Metro builds it and Dagger consumers are handed this instance.
     */
    val runningModeExpiryScheduler: RunningModeExpiryScheduler

    /** Every Metro-wired worker, keyed by its class. [MetroWorkerFactory] matches on the class name. */
    val workerCreators: Map<KClass<*>, MetroWorkerCreator>

    @DependencyGraph.Factory
    fun interface Factory {

        @Suppress("LongParameterList")
        fun create(
            @Provides aapsLoggerRef: DeferredRef<AAPSLogger>,
            @Provides fabricPrivacyRef: DeferredRef<FabricPrivacy>,
            @Provides runningModeExpiryJobRef: DeferredRef<RunningModeExpiryJob>,
            @Provides localAlertUtilsRef: DeferredRef<LocalAlertUtils>,
            @Provides persistenceLayerRef: DeferredRef<PersistenceLayer>,
            @Provides configRef: DeferredRef<Config>,
            @Provides iobCobCalculatorRef: DeferredRef<IobCobCalculator>,
            @Provides loopRef: DeferredRef<Loop>,
            @Provides dateUtilRef: DeferredRef<DateUtil>,
            @Provides activePluginRef: DeferredRef<ActivePlugin>,
            @Provides profileFunctionRef: DeferredRef<ProfileFunction>,
            @Provides rxBusRef: DeferredRef<RxBus>,
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
            @Provides appScopeRef: DeferredRef<CoroutineScope>
        ): AppWorkersGraph
    }

    @Provides fun aapsLogger(r: DeferredRef<AAPSLogger>): AAPSLogger = r.get()
    @Provides fun fabricPrivacy(r: DeferredRef<FabricPrivacy>): FabricPrivacy = r.get()
    @Provides fun runningModeExpiryJob(r: DeferredRef<RunningModeExpiryJob>): RunningModeExpiryJob = r.get()
    @Provides fun localAlertUtils(r: DeferredRef<LocalAlertUtils>): LocalAlertUtils = r.get()
    @Provides fun persistenceLayer(r: DeferredRef<PersistenceLayer>): PersistenceLayer = r.get()
    @Provides fun config(r: DeferredRef<Config>): Config = r.get()
    @Provides fun iobCobCalculator(r: DeferredRef<IobCobCalculator>): IobCobCalculator = r.get()
    @Provides fun loop(r: DeferredRef<Loop>): Loop = r.get()
    @Provides fun dateUtil(r: DeferredRef<DateUtil>): DateUtil = r.get()
    @Provides fun activePlugin(r: DeferredRef<ActivePlugin>): ActivePlugin = r.get()
    @Provides fun profileFunction(r: DeferredRef<ProfileFunction>): ProfileFunction = r.get()
    @Provides fun rxBus(r: DeferredRef<RxBus>): RxBus = r.get()
    @Provides fun commandQueue(r: DeferredRef<CommandQueue>): CommandQueue = r.get()
    @Provides fun maintenance(r: DeferredRef<Maintenance>): Maintenance = r.get()
    @Provides fun rh(r: DeferredRef<ResourceHelper>): ResourceHelper = r.get()
    @Provides fun preferences(r: DeferredRef<Preferences>): Preferences = r.get()
    @Provides fun dstHelper(r: DeferredRef<DstHelper>): DstHelper = r.get()
    @Provides fun workManager(r: DeferredRef<WorkManager>): WorkManager = r.get()
    @Provides fun concentrationHelper(r: DeferredRef<ConcentrationHelper>): ConcentrationHelper = r.get()
    @Provides fun notificationManager(r: DeferredRef<NotificationManager>): NotificationManager = r.get()
    @Provides fun activeSceneManager(r: DeferredRef<ActiveSceneManager>): ActiveSceneManager = r.get()
    @Provides fun sceneExecutor(r: DeferredRef<SceneExecutor>): SceneExecutor = r.get()
    @Provides fun sceneRepository(r: DeferredRef<SceneRepository>): SceneRepository = r.get()
    @Provides fun appScope(r: DeferredRef<CoroutineScope>): CoroutineScope = r.get()

    /**
     * The whole `@HiltWorker` replacement, per worker: one line binding the generated assisted factory
     * into the map. Metro generates the `Factory` from `@AssistedFactory`, and it already extends
     * [MetroWorkerCreator], so no adapter is needed.
     */
    @Provides
    @IntoMap
    @ClassKey(RunningModeExpiryWorker::class)
    fun bindRunningModeExpiryWorker(factory: RunningModeExpiryWorker.Factory): MetroWorkerCreator = factory

    @Provides
    @IntoMap
    @ClassKey(KeepAliveWorker::class)
    fun bindKeepAliveWorker(factory: KeepAliveWorker.Factory): MetroWorkerCreator = factory

    @Provides
    @IntoMap
    @ClassKey(SceneExpiryWorker::class)
    fun bindSceneExpiryWorker(factory: SceneExpiryWorker.Factory): MetroWorkerCreator = factory
}
