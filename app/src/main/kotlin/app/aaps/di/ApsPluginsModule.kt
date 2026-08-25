package app.aaps.di

import android.content.Context
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.AllConfigs
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LoggerUtils
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpStatusProvider
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.ui.CarbSuggestionActions
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.autotune.AutotuneCore
import app.aaps.plugins.aps.autotune.AutotuneFS
import app.aaps.plugins.aps.autotune.AutotuneIob
import app.aaps.plugins.aps.autotune.AutotunePlugin
import app.aaps.plugins.aps.autotune.AutotunePrep
import app.aaps.plugins.aps.autotune.data.ATProfile
import app.aaps.plugins.aps.loop.LoopPlugin
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryJob
import app.aaps.plugins.aps.loop.runningMode.RunningModeReconciler
import app.aaps.plugins.aps.openAPS.DeltaCalculator
import app.aaps.plugins.aps.openAPSAMA.DetermineBasalAMA
import app.aaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import app.aaps.plugins.aps.openAPSAutoISF.DetermineBasalAutoISF
import app.aaps.plugins.aps.openAPSAutoISF.GlucoseStatusCalculatorAutoIsf
import app.aaps.plugins.aps.openAPSAutoISF.OpenAPSAutoISFPlugin
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalSMB
import app.aaps.plugins.aps.openAPSSMB.GlucoseStatusCalculatorSMB
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import kotlinx.coroutines.CoroutineScope
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Dagger wiring for `:plugins:aps`, lifted out of the plugin module so it can be multiplatform.
 *
 * One such file per converted module, so that moving off this arrangement later is a per-module move.
 * Why no KMP module may carry a Dagger annotation - and why the mistake passes the build instead of
 * failing it - is in `_docs/KMP_IOS_FEASIBILITY.md`, under "Decisions taken".
 *
 * **Every constructor here is called with named arguments, deliberately.** These classes take up to
 * 24 parameters and many share a type; called positionally, swapping two of them still compiles, and
 * neither Dagger nor the build would notice. This is the code that decides insulin doses, so the
 * mistake is made into a compile error instead.
 *
 * Registration keeps the @IntKey block for this module unchanged. See PluginsListModule for the
 * overall ordering overview.
 */
@Module
@InstallIn(SingletonComponent::class)
class ApsPluginsModule {

    // The openAPS shared pieces are gone from here: DeltaCalculator, both GlucoseStatusCalculators and
    // the three DetermineBasal classes now carry Metro's @Inject and @SingleIn in :plugins:aps, which is
    // commonMain and so compiles for iOS too. `CoreObjectsModule` hands the four Dagger still asks for -
    // the instrumented APS tests inject them - back to that side.

    // ---- APS plugins ------------------------------------------------------------------------

    @Provides
    @Singleton
    fun provideOpenAPSSMBPlugin(
        aapsLogger: AAPSLogger,
        rxBus: RxBus,
        constraintsChecker: ConstraintsChecker,
        rh: ResourceHelper,
        profileFunction: ProfileFunction,
        profileUtil: ProfileUtil,
        config: Config,
        activePlugin: ActivePlugin,
        iobCobCalculator: IobCobCalculator,
        hardLimits: HardLimits,
        preferences: Preferences,
        dateUtil: DateUtil,
        processedTbrEbData: ProcessedTbrEbData,
        persistenceLayer: PersistenceLayer,
        glucoseStatusProvider: GlucoseStatusProvider,
        tddCalculator: TddCalculator,
        bgQualityCheck: BgQualityCheck,
        notificationManager: NotificationManager,
        determineBasalSMB: DetermineBasalSMB,
        profiler: Profiler,
        glucoseStatusCalculatorSMB: GlucoseStatusCalculatorSMB,
        apsResultProvider: Provider<APSResult>,
        ch: ConcentrationHelper,
        fabricPrivacy: FabricPrivacy
    ): OpenAPSSMBPlugin = OpenAPSSMBPlugin(
        aapsLogger = aapsLogger,
        rxBus = rxBus,
        constraintsChecker = constraintsChecker,
        rh = rh,
        profileFunction = profileFunction,
        profileUtil = profileUtil,
        config = config,
        activePlugin = activePlugin,
        iobCobCalculator = iobCobCalculator,
        hardLimits = hardLimits,
        preferences = preferences,
        dateUtil = dateUtil,
        processedTbrEbData = processedTbrEbData,
        persistenceLayer = persistenceLayer,
        glucoseStatusProvider = glucoseStatusProvider,
        tddCalculator = tddCalculator,
        bgQualityCheck = bgQualityCheck,
        notificationManager = notificationManager,
        determineBasalSMB = determineBasalSMB,
        profiler = profiler,
        glucoseStatusCalculatorSMB = glucoseStatusCalculatorSMB,
        apsResultProvider = { apsResultProvider.get() },
        ch = ch,
        fabricPrivacy = fabricPrivacy
    )

    @Provides
    @Singleton
    fun provideOpenAPSAutoISFPlugin(
        aapsLogger: AAPSLogger,
        rxBus: RxBus,
        constraintsChecker: ConstraintsChecker,
        rh: ResourceHelper,
        profileFunction: ProfileFunction,
        profileUtil: ProfileUtil,
        config: Config,
        activePlugin: ActivePlugin,
        iobCobCalculator: IobCobCalculator,
        hardLimits: HardLimits,
        preferences: Preferences,
        dateUtil: DateUtil,
        processedTbrEbData: ProcessedTbrEbData,
        persistenceLayer: PersistenceLayer,
        glucoseStatusProvider: GlucoseStatusProvider,
        bgQualityCheck: BgQualityCheck,
        notificationManager: NotificationManager,
        determineBasalAutoISF: DetermineBasalAutoISF,
        profiler: Profiler,
        glucoseStatusCalculatorAutoIsf: GlucoseStatusCalculatorAutoIsf,
        apsResultProvider: Provider<APSResult>,
        ch: ConcentrationHelper
    ): OpenAPSAutoISFPlugin = OpenAPSAutoISFPlugin(
        aapsLogger = aapsLogger,
        rxBus = rxBus,
        constraintsChecker = constraintsChecker,
        rh = rh,
        profileFunction = profileFunction,
        profileUtil = profileUtil,
        config = config,
        activePlugin = activePlugin,
        iobCobCalculator = iobCobCalculator,
        hardLimits = hardLimits,
        preferences = preferences,
        dateUtil = dateUtil,
        processedTbrEbData = processedTbrEbData,
        persistenceLayer = persistenceLayer,
        glucoseStatusProvider = glucoseStatusProvider,
        bgQualityCheck = bgQualityCheck,
        notificationManager = notificationManager,
        determineBasalAutoISF = determineBasalAutoISF,
        profiler = profiler,
        glucoseStatusCalculatorAutoIsf = glucoseStatusCalculatorAutoIsf,
        apsResultProvider = { apsResultProvider.get() },
        ch = ch
    )

    @Provides
    @Singleton
    fun provideOpenAPSAMAPlugin(
        aapsLogger: AAPSLogger,
        rxBus: RxBus,
        constraintsChecker: ConstraintsChecker,
        rh: ResourceHelper,
        config: Config,
        profileFunction: ProfileFunction,
        activePlugin: ActivePlugin,
        iobCobCalculator: IobCobCalculator,
        processedTbrEbData: ProcessedTbrEbData,
        hardLimits: HardLimits,
        dateUtil: DateUtil,
        persistenceLayer: PersistenceLayer,
        glucoseStatusProvider: GlucoseStatusProvider,
        preferences: Preferences,
        determineBasalAMA: DetermineBasalAMA,
        glucoseStatusCalculatorSMB: GlucoseStatusCalculatorSMB,
        apsResultProvider: Provider<APSResult>,
        ch: ConcentrationHelper,
        fabricPrivacy: FabricPrivacy
    ): OpenAPSAMAPlugin = OpenAPSAMAPlugin(
        aapsLogger = aapsLogger,
        rxBus = rxBus,
        constraintsChecker = constraintsChecker,
        rh = rh,
        config = config,
        profileFunction = profileFunction,
        activePlugin = activePlugin,
        iobCobCalculator = iobCobCalculator,
        processedTbrEbData = processedTbrEbData,
        hardLimits = hardLimits,
        dateUtil = dateUtil,
        persistenceLayer = persistenceLayer,
        glucoseStatusProvider = glucoseStatusProvider,
        preferences = preferences,
        determineBasalAMA = determineBasalAMA,
        glucoseStatusCalculatorSMB = glucoseStatusCalculatorSMB,
        apsResultProvider = { apsResultProvider.get() },
        ch = ch,
        fabricPrivacy = fabricPrivacy
    )

    // ---- Loop --------------------------------------------------------------------------------

    @Provides
    @Singleton
    fun provideRunningModeReconciler(
        persistenceLayer: PersistenceLayer,
        processedTbrEbData: ProcessedTbrEbData,
        activePlugin: ActivePlugin,
        commandQueue: CommandQueue,
        profileFunction: ProfileFunction,
        config: Config,
        dateUtil: DateUtil,
        aapsLogger: AAPSLogger,
        rxBus: RxBus,
        rh: ResourceHelper,
        @ApplicationScope appScope: CoroutineScope
    ): RunningModeReconciler = RunningModeReconciler(
        persistenceLayer = persistenceLayer,
        processedTbrEbData = processedTbrEbData,
        activePlugin = activePlugin,
        commandQueue = commandQueue,
        profileFunction = profileFunction,
        config = config,
        dateUtil = dateUtil,
        aapsLogger = aapsLogger,
        rxBus = rxBus,
        rh = rh,
        appScope = appScope
    )

    @Provides
    @Singleton
    fun provideRunningModeExpiryJob(
        aapsLogger: AAPSLogger,
        config: Config,
        dateUtil: DateUtil,
        processedTbrEbData: ProcessedTbrEbData,
        commandQueue: CommandQueue
    ): RunningModeExpiryJob = RunningModeExpiryJob(
        aapsLogger = aapsLogger,
        config = config,
        dateUtil = dateUtil,
        processedTbrEbData = processedTbrEbData,
        commandQueue = commandQueue
    )

    @Provides
    @Singleton
    fun provideLoopPlugin(
        aapsLogger: AAPSLogger,
        rxBus: RxBus,
        preferences: Preferences,
        config: Config,
        constraintChecker: ConstraintsChecker,
        rh: ResourceHelper,
        profileFunction: ProfileFunction,
        context: Context,
        commandQueue: CommandQueue,
        activePlugin: ActivePlugin,
        processedTbrEbData: ProcessedTbrEbData,
        receiverStatusStore: ReceiverStatusStore,
        fabricPrivacy: FabricPrivacy,
        dateUtil: DateUtil,
        uel: UserEntryLogger,
        persistenceLayer: PersistenceLayer,
        uiInteraction: UiInteraction,
        notificationManager: NotificationManager,
        pumpEnactResultProvider: Provider<PumpEnactResult>,
        processedDeviceStatusData: ProcessedDeviceStatusData,
        pumpStatusProvider: PumpStatusProvider,
        decimalFormatter: DecimalFormatter,
        ch: ConcentrationHelper,
        carbSuggestionActions: CarbSuggestionActions,
        @ApplicationScope appScope: CoroutineScope
    ): LoopPlugin = LoopPlugin(
        aapsLogger = aapsLogger,
        rxBus = rxBus,
        preferences = preferences,
        config = config,
        constraintChecker = constraintChecker,
        rh = rh,
        profileFunction = profileFunction,
        context = context,
        commandQueue = commandQueue,
        activePlugin = activePlugin,
        processedTbrEbData = processedTbrEbData,
        receiverStatusStore = receiverStatusStore,
        fabricPrivacy = fabricPrivacy,
        dateUtil = dateUtil,
        uel = uel,
        persistenceLayer = persistenceLayer,
        uiInteraction = uiInteraction,
        notificationManager = notificationManager,
        pumpEnactResultProvider = pumpEnactResultProvider,
        processedDeviceStatusData = processedDeviceStatusData,
        pumpStatusProvider = pumpStatusProvider,
        decimalFormatter = decimalFormatter,
        ch = ch,
        carbSuggestionActions = carbSuggestionActions,
        appScope = appScope
    )

    // ---- Autotune ----------------------------------------------------------------------------

    @Provides
    @Singleton
    fun provideAutotuneFS(rh: ResourceHelper, loggerUtils: LoggerUtils): AutotuneFS =
        AutotuneFS(rh = rh, loggerUtils = loggerUtils)

    @Provides
    @Singleton
    fun provideAutotuneCore(preferences: Preferences, autotuneFS: AutotuneFS): AutotuneCore =
        AutotuneCore(preferences = preferences, autotuneFS = autotuneFS)

    @Provides
    @Singleton
    fun provideAutotuneIob(
        aapsLogger: AAPSLogger,
        persistenceLayer: PersistenceLayer,
        profileFunction: ProfileFunction,
        preferences: Preferences,
        dateUtil: DateUtil,
        autotuneFS: AutotuneFS
    ): AutotuneIob = AutotuneIob(
        aapsLogger = aapsLogger,
        persistenceLayer = persistenceLayer,
        profileFunction = profileFunction,
        preferences = preferences,
        dateUtil = dateUtil,
        autotuneFS = autotuneFS
    )

    @Provides
    @Singleton
    fun provideAutotunePrep(
        preferences: Preferences,
        dateUtil: DateUtil,
        autotuneFS: AutotuneFS,
        autotuneIob: AutotuneIob
    ): AutotunePrep = AutotunePrep(
        preferences = preferences,
        dateUtil = dateUtil,
        autotuneFS = autotuneFS,
        autotuneIob = autotuneIob
    )

    @Provides
    fun provideATProfile(
        preferences: Preferences,
        profileUtil: ProfileUtil,
        dateUtil: DateUtil,
        rh: ResourceHelper,
        profileStoreProvider: Provider<ProfileStore>,
        aapsLogger: AAPSLogger
    ): ATProfile = ATProfile(
        preferences = preferences,
        profileUtil = profileUtil,
        dateUtil = dateUtil,
        rh = rh,
        profileStoreProvider = { profileStoreProvider.get() },
        aapsLogger = aapsLogger
    )

    @Provides
    @Singleton
    fun provideAutotunePlugin(
        aapsLogger: AAPSLogger,
        rh: ResourceHelper,
        preferences: Preferences,
        rxBus: RxBus,
        profileFunction: ProfileFunction,
        profileUtil: ProfileUtil,
        dateUtil: DateUtil,
        profileRepository: ProfileRepository,
        autotuneFS: AutotuneFS,
        autotuneIob: AutotuneIob,
        autotunePrep: AutotunePrep,
        autotuneCore: AutotuneCore,
        config: Config,
        uel: UserEntryLogger,
        loop: Loop,
        profileStoreProvider: Provider<ProfileStore>,
        atProfileProvider: Provider<ATProfile>
    ): AutotunePlugin = AutotunePlugin(
        aapsLogger = aapsLogger,
        rh = rh,
        preferences = preferences,
        rxBus = rxBus,
        profileFunction = profileFunction,
        profileUtil = profileUtil,
        dateUtil = dateUtil,
        profileRepository = profileRepository,
        autotuneFS = autotuneFS,
        autotuneIob = autotuneIob,
        autotunePrep = autotunePrep,
        autotuneCore = autotuneCore,
        config = config,
        uel = uel,
        loop = loop,
        profileStoreProvider = profileStoreProvider,
        atProfileProvider = atProfileProvider
    )

    @Module
    @InstallIn(SingletonComponent::class)
    @Suppress("unused")
    abstract class Bindings {

        @Binds abstract fun bindLoop(plugin: LoopPlugin): Loop

        @Binds abstract fun bindAutotune(plugin: AutotunePlugin): Autotune

        // @IntKey block 200-240, step 10 - taken from the deleted ApsPluginsListModule, NOT inferred.
        // These decide plugin order; a wrong value here reorders the list silently.
        @Binds
        @AllConfigs
        @IntoMap
        @IntKey(200)
        abstract fun bindLoopPlugin(plugin: LoopPlugin): PluginBase

        @Binds
        @AllConfigs
        @IntoMap
        @IntKey(210)
        abstract fun bindOpenAPSAMAPlugin(plugin: OpenAPSAMAPlugin): PluginBase

        @Binds
        @AllConfigs
        @IntoMap
        @IntKey(220)
        abstract fun bindOpenAPSSMBPlugin(plugin: OpenAPSSMBPlugin): PluginBase

        @Binds
        @AllConfigs
        @IntoMap
        @IntKey(230)
        abstract fun bindOpenAPSAutoISFPlugin(plugin: OpenAPSAutoISFPlugin): PluginBase

        @Binds
        @AllConfigs
        @IntoMap
        @IntKey(240)
        abstract fun bindAutotunePlugin(plugin: AutotunePlugin): PluginBase
    }
}
