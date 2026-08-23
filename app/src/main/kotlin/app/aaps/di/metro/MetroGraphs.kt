package app.aaps.di.metro

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.plugins.calibration.di.CalibrationGraph
import app.aaps.plugins.sensitivity.di.SensitivityGraph
import app.aaps.plugins.smoothing.di.SmoothingGraph
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
    private val activePlugin: Provider<ActivePlugin>
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
}
