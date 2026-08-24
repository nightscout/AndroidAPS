package app.aaps.plugins.constraints.di

import android.content.Context
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.APS
import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.core.interfaces.di.NotNSClient
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.MetroViewModelCreator
import app.aaps.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import app.aaps.plugins.constraints.dstHelper.DstHelperPlugin
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.objectives.objectives.Objective
import app.aaps.plugins.constraints.objectives.objectives.Objective0
import app.aaps.plugins.constraints.objectives.objectives.Objective1
import app.aaps.plugins.constraints.objectives.objectives.Objective2
import app.aaps.plugins.constraints.objectives.objectives.Objective3
import app.aaps.plugins.constraints.objectives.objectives.Objective4
import app.aaps.plugins.constraints.objectives.objectives.Objective5
import app.aaps.plugins.constraints.objectives.objectives.Objective6
import app.aaps.plugins.constraints.objectives.objectives.Objective7
import app.aaps.plugins.constraints.objectives.objectives.Objective8
import app.aaps.plugins.constraints.objectives.objectives.Objective9
import app.aaps.plugins.constraints.objectives.SntpClient
import app.aaps.plugins.constraints.objectives.compose.ObjectivesViewModel
import app.aaps.plugins.constraints.safety.SafetyPlugin
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.plugins.constraints.storage.StorageConstraintPlugin
import app.aaps.plugins.constraints.versionChecker.VersionCheckerPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Metro wiring for this module's seven plugins, replacing `ConstraintsPluginsListModule`.
 *
 * The three maps are the point. Each plugin belongs to exactly one build bucket, and `:app` merges
 * each bucket under its own condition: `@AllConfigs` always, `@APS` only when the build runs a loop,
 * `@NotNSClient` only when the build is not a follower. Put a plugin in the wrong map and it either
 * disappears from the app or turns up in a build that has never shown it - and nothing would fail,
 * because a plugin list is just a list. `ConstraintsBucketsTest` checks the split.
 *
 * ## Who constructs what
 *
 * Five of the seven are **built here**. Metro reads their existing `javax.inject` constructors through
 * this module's Dagger interop, so the classes themselves are untouched. Nothing else injects them -
 * that was checked before the change - so there is exactly one instance and no window in which both
 * frameworks could build one.
 *
 * Two are still **handed over** from Dagger, on purpose:
 *
 *  - [ObjectivesPlugin] injects `List<Objective>`, a ten-entry Dagger multibinding over twelve classes.
 *    That list has to be ported before this plugin can move, and that is its own change.
 *  - [SignatureVerifierPlugin] is injected by `MainApp` for a single `shortHashes()` call. Building it
 *    here while Dagger still injects it there would give two instances, so it moves when that call
 *    does.
 *
 * Everything arrives as [DeferredRef], for the re-entrancy reason written up in `MetroGraphs`. It is
 * not theoretical here: [SafetyPlugin] takes a [ConstraintsChecker], which aggregates the constraint
 * plugins - a loop straight back into this graph, broken by the leaves arriving deferred.
 */
// Scoped with javax @Singleton, not Metro's @SingleIn: the plugins carry @Singleton on the class and
// interop reads it as their scope, so the graph has to declare the same one to hold them.
@Singleton
@DependencyGraph(AppScope::class)
internal interface ConstraintsMetroGraph {

    /** Present in every build. */
    @AllConfigs
    val allConfigsPlugins: Map<Int, PluginBase>

    /** Only in builds that run the loop. */
    @APS
    val apsPlugins: Map<Int, PluginBase>

    /** Only in builds that are not a follower. */
    @NotNSClient
    val notNsClientPlugins: Map<Int, PluginBase>

    /** Builds [ObjectivesViewModel] - the `@HiltViewModel` replacement for this module. */
    val viewModelCreators: Map<KClass<*>, MetroViewModelCreator>

    /**
     * The plugins that are also bound to an interface for other callers.
     *
     * Dagger must hand out these exact objects rather than build its own. A plugin bound to an
     * interface is easy to miss when checking who still consumes it - it does not look like an
     * injection site - and the result is two instances: the one in the plugin list, which is enabled
     * and started, and a second one behind the interface that nobody ever starts.
     */
    val bgQualityCheckPlugin: BgQualityCheckPlugin
    val dstHelperPlugin: DstHelperPlugin
    val objectivesPlugin: ObjectivesPlugin

    @DependencyGraph.Factory
    fun interface Factory {

        @Suppress("LongParameterList")
        fun create(
            // Still built by Dagger - see the note above.
            @Provides signatureVerifierRef: DeferredRef<SignatureVerifierPlugin>,
            // App-wide leaves, from which the other five plugins and the view model are built.
            @Provides aapsLoggerRef: DeferredRef<AAPSLogger>,
            @Provides rxBusRef: DeferredRef<RxBus>,
            @Provides rhRef: DeferredRef<ResourceHelper>,
            @Provides dateUtilRef: DeferredRef<DateUtil>,
            @Provides sntpClientRef: DeferredRef<SntpClient>,
            @Provides receiverStatusStoreRef: DeferredRef<ReceiverStatusStore>,
            @Provides uelRef: DeferredRef<UserEntryLogger>,
            @Provides preferencesRef: DeferredRef<Preferences>,
            @Provides constraintsCheckerRef: DeferredRef<ConstraintsChecker>,
            @Provides activePluginRef: DeferredRef<ActivePlugin>,
            @Provides hardLimitsRef: DeferredRef<HardLimits>,
            @Provides configRef: DeferredRef<Config>,
            @Provides persistenceLayerRef: DeferredRef<PersistenceLayer>,
            @Provides notificationManagerRef: DeferredRef<NotificationManager>,
            @Provides decimalFormatterRef: DeferredRef<DecimalFormatter>,
            @Provides versionCheckerUtilsRef: DeferredRef<VersionCheckerUtils>,
            @Provides loopRef: DeferredRef<Loop>,
            @Provides profileFunctionRef: DeferredRef<ProfileFunction>,
            @Provides iobCobCalculatorRef: DeferredRef<IobCobCalculator>,
            @Provides contextRef: DeferredRef<Context>,
            @Provides virtualPumpRef: DeferredRef<VirtualPump>,
            @Provides passwordCheckRef: DeferredRef<PasswordCheck>
        ): ConstraintsMetroGraph
    }

    @Provides fun signatureVerifier(r: DeferredRef<SignatureVerifierPlugin>): SignatureVerifierPlugin = r.get()
    @Provides fun aapsLogger(r: DeferredRef<AAPSLogger>): AAPSLogger = r.get()
    @Provides fun rxBus(r: DeferredRef<RxBus>): RxBus = r.get()
    @Provides fun rh(r: DeferredRef<ResourceHelper>): ResourceHelper = r.get()
    @Provides fun dateUtil(r: DeferredRef<DateUtil>): DateUtil = r.get()
    @Provides fun sntpClient(r: DeferredRef<SntpClient>): SntpClient = r.get()
    @Provides fun receiverStatusStore(r: DeferredRef<ReceiverStatusStore>): ReceiverStatusStore = r.get()
    @Provides fun uel(r: DeferredRef<UserEntryLogger>): UserEntryLogger = r.get()
    @Provides fun preferences(r: DeferredRef<Preferences>): Preferences = r.get()
    @Provides fun constraintsChecker(r: DeferredRef<ConstraintsChecker>): ConstraintsChecker = r.get()
    @Provides fun activePlugin(r: DeferredRef<ActivePlugin>): ActivePlugin = r.get()
    @Provides fun hardLimits(r: DeferredRef<HardLimits>): HardLimits = r.get()
    @Provides fun config(r: DeferredRef<Config>): Config = r.get()
    @Provides fun persistenceLayer(r: DeferredRef<PersistenceLayer>): PersistenceLayer = r.get()
    @Provides fun notificationManager(r: DeferredRef<NotificationManager>): NotificationManager = r.get()
    @Provides fun decimalFormatter(r: DeferredRef<DecimalFormatter>): DecimalFormatter = r.get()
    @Provides fun versionCheckerUtils(r: DeferredRef<VersionCheckerUtils>): VersionCheckerUtils = r.get()
    @Provides fun loop(r: DeferredRef<Loop>): Loop = r.get()
    @Provides fun profileFunction(r: DeferredRef<ProfileFunction>): ProfileFunction = r.get()
    @Provides fun iobCobCalculator(r: DeferredRef<IobCobCalculator>): IobCobCalculator = r.get()
    @Provides fun context(r: DeferredRef<Context>): Context = r.get()
    @Provides fun virtualPump(r: DeferredRef<VirtualPump>): VirtualPump = r.get()
    @Provides fun passwordCheck(r: DeferredRef<PasswordCheck>): PasswordCheck = r.get()

    // A plugin is one object for the app's lifetime: it holds its enabled state and, for several of
    // these, a notification it has raised. Two instances would mean one of them silently ignored.
    @Provides @AllConfigs @IntoMap @IntKey(800)
    fun bindSafety(plugin: SafetyPlugin): PluginBase = plugin

    @Provides @NotNSClient @IntoMap @IntKey(810)
    fun bindVersionChecker(plugin: VersionCheckerPlugin): PluginBase = plugin

    @Provides @APS @IntoMap @IntKey(820)
    fun bindStorageConstraint(plugin: StorageConstraintPlugin): PluginBase = plugin

    @Provides @APS @IntoMap @IntKey(830)
    fun bindSignatureVerifier(plugin: SignatureVerifierPlugin): PluginBase = plugin

    @Provides @APS @IntoMap @IntKey(840)
    fun bindObjectives(plugin: ObjectivesPlugin): PluginBase = plugin

    @Provides @AllConfigs @IntoMap @IntKey(850)
    fun bindDstHelper(plugin: DstHelperPlugin): PluginBase = plugin

    @Provides @AllConfigs @IntoMap @IntKey(860)
    fun bindBgQualityCheck(plugin: BgQualityCheckPlugin): PluginBase = plugin


    /**
     * The ten objectives, in order. This replaces `ObjectivesModule`, which did the same thing with
     * Dagger `@Binds @IntoMap` plus a `@Provides` that sorted the map into a list.
     *
     * The original map carried an `@ObjectiveClass` qualifier to tell it apart from other maps. There
     * is only one `Map<Int, Objective>` in this graph, so the qualifier would distinguish nothing and
     * is dropped - unlike the plugin buckets above, where the qualifier is the whole point.
     */
    @Provides
    fun objectivesList(objectives: Map<Int, Objective>): List<Objective> =
        objectives.toList().sortedBy { it.first }.map { it.second }

    @Provides @IntoMap @IntKey(0) fun objective0(o: Objective0): Objective = o

    @Provides @IntoMap @IntKey(1) fun objective1(o: Objective1): Objective = o

    @Provides @IntoMap @IntKey(2) fun objective2(o: Objective2): Objective = o

    @Provides @IntoMap @IntKey(3) fun objective3(o: Objective3): Objective = o

    @Provides @IntoMap @IntKey(4) fun objective4(o: Objective4): Objective = o

    @Provides @IntoMap @IntKey(5) fun objective5(o: Objective5): Objective = o

    @Provides @IntoMap @IntKey(6) fun objective6(o: Objective6): Objective = o

    @Provides @IntoMap @IntKey(7) fun objective7(o: Objective7): Objective = o

    @Provides @IntoMap @IntKey(8) fun objective8(o: Objective8): Objective = o

    @Provides @IntoMap @IntKey(9) fun objective9(o: Objective9): Objective = o

    @Provides
    @IntoMap
    @ClassKey(ObjectivesViewModel::class)
    fun bindObjectivesViewModel(provider: Provider<ObjectivesViewModel>): MetroViewModelCreator =
        MetroViewModelCreator { provider() }
}
