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
    // The three openAPS plugins are built from their own @Inject constructors in :plugins:aps now, and
    // register themselves through `ApsPluginRegistrations` in that module's androidMain - where the @APS
    // qualifier is reachable. Loop and Autotune stay below: they also bind interfaces the app depends on.

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

    // LoopPlugin builds itself now: Metro reads its javax @Inject and @Singleton through this module's
    // Dagger interop, and the class contributes itself at key 200 and binds the Loop interface.
    // `CoreObjectsModule.provideLoop` carries it back to the Dagger half.


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

    // AutotunePlugin builds itself now too - same shape as LoopPlugin: javax annotations read through
    // this module's interop, Metro contribution annotations on the class, key 240.


    @Module
    @InstallIn(SingletonComponent::class)
    @Suppress("unused")
    abstract class Bindings {



        // @IntKey block 200-240, step 10 - taken from the deleted ApsPluginsListModule, NOT inferred.
        // These decide plugin order; a wrong value here reorders the list silently.


    }
}
