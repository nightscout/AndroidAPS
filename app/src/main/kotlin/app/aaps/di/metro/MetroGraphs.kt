package app.aaps.di.metro

import androidx.work.WorkManager
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.dst.DstHelper
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.workflow.MetroWorkerCreator
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.ui.compose.MetroViewModelFactory
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryScheduler
import app.aaps.plugins.automation.di.AutomationMetroGraph
import app.aaps.plugins.calibration.di.CalibrationGraph
import app.aaps.plugins.constraints.di.ConstraintsMetroGraph
import app.aaps.plugins.sensitivity.di.SensitivityGraph
import app.aaps.plugins.source.di.SourceMetroGraph
import app.aaps.plugins.sync.di.OpenHumansMetroBridge
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.createGraphFactory
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

    private val openHumansMetroBridge: Provider<OpenHumansMetroBridge>,
    private val leaves: Provider<AapsLeaves>
) {

    private val coreObjects: CoreObjectsGraph get() = root.coreObjectsGraph

    private val calibration: CalibrationGraph get() = root.calibrationGraph

    private val sensitivity: SensitivityGraph get() = root.sensitivityGraph

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

    /** The one Metro root. Sub-graphs are extensions of it rather than roots of their own. */
    private val root: AppRootGraph by lazy {
        createGraphFactory<AppRootGraph.Factory>().create(leaves.get())
    }

    private val source: SourceMetroGraph get() = root.sourceGraph

    private val receivers: AppReceiversGraph get() = root.receiversGraph
    private val workers: AppWorkersGraph get() = root.workersGraph

    // The module owns its own bridge, because its DI qualifiers are internal to it.
    private val openHumans: OpenHumansMetroBridge get() = openHumansMetroBridge.get()
    private val automation: AutomationMetroGraph get() = root.automationGraph
    private val constraints: ConstraintsMetroGraph get() = root.constraintsGraph

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
            ?: automation.memberInjectors[target::class]
            ?: source.memberInjectors[target::class]
            ?: return false
        (injector as MembersInjector<Any>).injectMembers(target)
        return true
    }

    /**
     * The `@HiltViewModel` replacement. Built once, from every module graph that contributes view
     * models - one map, the same way Hilt presents one factory for the whole app.
     */
    val viewModelFactory: MetroViewModelFactory by lazy {
        MetroViewModelFactory(openHumans.viewModelCreators + constraints.viewModelCreators)
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
    fun plugins(): Map<Int, PluginBase> =
        root.contributedPlugins + calibration.plugins + sensitivity.plugins +
            constraints.allConfigsPlugins

    /**
     * Plugins that must NOT appear in an AAPSCLIENT build.
     *
     * Kept apart from [plugins] because the Dagger bindings these replace carried a `@NotNSClient`
     * qualifier, and `AppModule.providesPlugins` merges that bucket only when the build is not a
     * follower. Merging it unconditionally would quietly add Open Humans to follower builds.
     */
    fun notNsClientPlugins(): Map<Int, PluginBase> =
        openHumans.notNsClientPlugins + constraints.notNsClientPlugins

    /**
     * Plugins that only belong in a build that runs the loop.
     *
     * Same reasoning as [notNsClientPlugins], for the `@APS` qualifier. Objectives, the signature
     * verifier and the storage constraint have no meaning in a build that never makes a decision.
     */
    fun apsPlugins(): Map<Int, PluginBase> = constraints.apsPlugins

    /**
     * Constraint plugins that are also bound to an interface, handed to Dagger in `CoreObjectsModule`.
     *
     * Metro builds these, so Dagger must delegate rather than construct - see there for what goes
     * wrong otherwise.
     */
    val bgQualityCheck: BgQualityCheck get() = constraints.bgQualityCheckPlugin
    val dstHelper: DstHelper get() = constraints.dstHelperPlugin
    val objectives: Objectives get() = constraints.objectivesPlugin
}
