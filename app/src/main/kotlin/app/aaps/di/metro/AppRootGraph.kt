package app.aaps.di.metro

import app.aaps.core.interfaces.ui.CarbSuggestionActions
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.interfaces.maintenance.CloudDirectoryManager
import app.aaps.core.interfaces.overview.graph.GraphConfigRepository
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.interfaces.scenes.ActiveSceneSync
import app.aaps.implementation.scenes.ActiveSceneManager
import app.aaps.core.interfaces.scenes.SceneChainResolver
import app.aaps.core.interfaces.scenes.SceneStore
import app.aaps.core.interfaces.scenes.Scenes
import app.aaps.core.interfaces.scenes.SceneActions
import app.aaps.core.interfaces.scenes.SceneAutomationApi
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
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.plugins.automation.di.AutomationMetroGraph
import app.aaps.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import app.aaps.plugins.constraints.dstHelper.DstHelperPlugin
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.objectives.objectives.Objective
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.NSClientSourcePlugin
import app.aaps.plugins.source.XdripSourcePlugin
import app.aaps.plugins.source.di.SourceMetroGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes
import dev.zacsweers.metro.Provides
import javax.inject.Singleton
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
// Two scopes on purpose. AppScope is Metro's own; javax @Singleton is declared as well because, with
// Dagger interop on, Metro READS the javax scope on classes from other modules. Without this the graph
// refuses any @Singleton class with "may not reference bindings from different scopes" - and with it,
// an existing class can be contributed without retagging its scope annotation at all.
@Singleton
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
    val carbSuggestionActions: CarbSuggestionActions
    val temporaryBasalStorage: TemporaryBasalStorage
    val detailedBolusInfoStorage: DetailedBolusInfoStorage
    val blePreCheck: BlePreCheck
    val visibilityContext: VisibilityContext
    val cloudDirectoryManager: CloudDirectoryManager
    val graphConfigRepository: GraphConfigRepository
    val batchExecutor: BatchExecutor
    val wizardExecutor: WizardExecutor
    val configBuilder: ConfigBuilder
    val dataSyncSelectorXdrip: DataSyncSelectorXdrip
    val activeSceneSync: ActiveSceneSync

    /**
     * The same object as [activeSceneSync], by class.
     *
     * `SceneExecutor`, `SceneAutomationApiImpl` and `SceneExpiryWorker` all ask for the concrete type and
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

    /**
     * Interfaces backed by a plugin this graph already builds. A `@Provides` rather than a delegate,
     * because the instance is the plugin - binding it any other way would make a second one.
     */
    @Provides fun dexcomBoyda(plugin: DexcomPlugin): DexcomBoyda = plugin

    @Provides fun xDripSource(plugin: XdripSourcePlugin): XDripSource = plugin

    @DependencyGraph.Factory
    fun interface Factory {

        /**
         * One parameter, not thirty-five. [AapsLeaves] carries everything Dagger still owns, and its
         * @Provides functions are called only when something needs that type - so the deferral that
         * DeferredRef used to do by hand is now just the shape of a binding container.
         */
        fun create(
            @Includes leaves: AapsLeaves,
            @Includes coreObjects: CoreObjectsGraph,
            @Includes pumpLeaves: PumpLeaves
        ): AppRootGraph
    }
}
