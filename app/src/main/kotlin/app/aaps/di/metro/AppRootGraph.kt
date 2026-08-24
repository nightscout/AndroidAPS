package app.aaps.di.metro

import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.di.APS
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.storage.Storage
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.interfaces.di.NotNSClient
import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.plugins.automation.di.AutomationMetroGraph
import app.aaps.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import app.aaps.plugins.constraints.dstHelper.DstHelperPlugin
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.objectives.objectives.Objective
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.NSClientSourcePlugin
import app.aaps.plugins.source.XdripSourcePlugin
import app.aaps.plugins.source.di.SourceMetroGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.MetroViewModelMultibindings
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.stats.DexcomTirCalculator
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.insulin.InsulinManager

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
@DependencyGraph(AppScope::class)
interface AppRootGraph : MetroViewModelMultibindings {

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
    val coreObjectsGraph: CoreObjectsGraph

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
    val decimalFormatter: DecimalFormatter
    val profileUtil: ProfileUtil
    val hardLimits: HardLimits
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
    val objectivesPlugin: ObjectivesPlugin
    val signatureVerifierPlugin: SignatureVerifierPlugin

    /**
     * Source plugins that are also bound to an interface for other callers.
     *
     * Metro builds these, so Dagger must delegate rather than construct - see `CoreObjectsModule`.
     */
    val xdripSourcePlugin: XdripSourcePlugin
    val nsClientSourcePlugin: NSClientSourcePlugin
    val dexcomPlugin: DexcomPlugin

    val sourceGraph: SourceMetroGraph
    val automationGraph: AutomationMetroGraph

    @DependencyGraph.Factory
    fun interface Factory {

        /**
         * One parameter, not thirty-five. [AapsLeaves] carries everything Dagger still owns, and its
         * @Provides functions are called only when something needs that type - so the deferral that
         * DeferredRef used to do by hand is now just the shape of a binding container.
         */
        fun create(@Includes leaves: AapsLeaves): AppRootGraph
    }
}
