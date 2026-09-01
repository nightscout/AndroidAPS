package app.aaps.ios.shell.di

import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.shared.clientbindings.ClientGraphBindings
import app.aaps.workflow.CalculationExecutor
import app.aaps.workflow.CoroutineCalculationExecutor
import app.aaps.workflow.PostCalculationRunner
import app.aaps.workflow.PrepareGraphDataRunner
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.database.AppRepository
import app.aaps.database.di.IosAppDatabaseBuilder
import app.aaps.ios.shell.prefs.IosSp
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.ios.shell.config.IosClientConfig
import app.aaps.shared.impl.logging.LImpl
import app.aaps.shared.impl.rx.bus.RxBusImpl
import app.aaps.implementation.notifications.CommonNotificationManager
import app.aaps.implementation.notifications.IosSystemNotificationPlatform
import app.aaps.shared.impl.utils.IosDateFormatPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import app.aaps.implementation.logging.AAPSLoggerIos
import app.aaps.plugins.calibration.LinearCalibrationPlugin
import app.aaps.plugins.calibration.NoCalibrationPlugin
import app.aaps.plugins.sensitivity.SensitivityAAPSPlugin
import app.aaps.plugins.sensitivity.SensitivityOref1Plugin
import app.aaps.plugins.sensitivity.SensitivityWeightedAveragePlugin
import app.aaps.plugins.smoothing.AvgSmoothingPlugin
import app.aaps.plugins.smoothing.ExponentialSmoothingPlugin
import app.aaps.plugins.smoothing.NoSmoothingPlugin
import app.aaps.plugins.smoothing.UnscentedKalmanFilterPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * A Metro graph that builds real AAPS plugins on iOS.
 *
 * Linking the framework only shows that every symbol resolves. It says nothing about whether the
 * code Metro generates actually runs, because none of it is called. Dependency injection is the
 * part where that distinction matters most: a graph is generated code, and generated code that
 * links can still fail the first time something asks it for an object.
 *
 * So this graph asks. It is deliberately over `AppScope`, the same scope the Android app uses,
 * rather than a private scope invented for the test, so the `@SingleIn(AppScope::class)` on the
 * plugins is the real annotation being honoured.
 *
 * ## What is in here
 *
 * It started as leaves only - the four plugins whose constructors needed nothing but a logger and a
 * text resolver - because everything else was held back by an interface with no common
 * implementation. That list is now empty. `DateUtil`, `Preferences`, `PersistenceLayer`,
 * `ActivePlugin`, `NotificationManager`, `ProfileUtil` and `RxBus` all resolve here, so the five
 * plugins that were listed as blocked are built below, from real AAPS code with nothing faked under
 * them.
 *
 * One fake is left, `ProbeTextResolver`, because resources are not a Metro problem: strings live in
 * Android resource files and iOS has no reader for them yet.
 *
 * To find what is still missing, add an accessor for it and read the `[Metro/MissingBinding]` error.
 * `_docs/ios_blockers.md` holds the current list and the procedure.
 */
@DependencyGraph(AppScope::class)
interface IosProbeGraph {

    val noSmoothing: NoSmoothingPlugin
    val avgSmoothing: AvgSmoothingPlugin
    val exponentialSmoothing: ExponentialSmoothingPlugin
    val noCalibration: NoCalibrationPlugin

    /**
     * The five that used to be held back.
     *
     * Each was blocked by an interface with no common implementation - DateUtil, Preferences,
     * ActivePlugin, PersistenceLayer, NotificationManager. All of those now resolve, so these build
     * from real AAPS code with nothing faked underneath them.
     */
    val sensitivityOref1: SensitivityOref1Plugin
    val sensitivityAAPS: SensitivityAAPSPlugin
    val sensitivityWeightedAverage: SensitivityWeightedAveragePlugin
    val unscentedKalmanFilter: UnscentedKalmanFilterPlugin
    val linearCalibration: LinearCalibrationPlugin

    /** Real AAPS classes, built from the iOS implementations underneath them. */
    val dateUtil: DateUtil
    val fabricPrivacy: FabricPrivacy
    val repository: AppRepository
    val notificationManager: NotificationManager
    val logger: AAPSLogger
    val alarmSoundPlayer: AlarmSoundPlayer
    val preferences: Preferences





    /**
     * A real SQLite database, in a file of its own.
     *
     * Named apart from the app's so a probe run cannot disturb real data. Every other leaf this
     * graph needs comes from [IosPlatformBindings], which the app graph shares.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun repository(): AppRepository = IosAppDatabaseBuilder().provideAppRepository("aaps-ios-graph.db")

    @DependencyGraph.Factory
    fun interface Factory {

        /**
         * [CoreObjectsGraph] is a `@BindingContainer`, so its `@Provides` are only visible to a graph
         * that includes it - the same way `AppRootGraph` takes it on Android. Without this, four
         * shared classes it already builds (the bolus wizard, the quick wizard and its entry, and the
         * running mode guard) look like missing bindings rather than ones Metro cannot see.
         */
        fun create(
            @Includes coreObjects: CoreObjectsGraph,
            @Includes clientBindings: ClientGraphBindings
        ): IosProbeGraph
    }
}
