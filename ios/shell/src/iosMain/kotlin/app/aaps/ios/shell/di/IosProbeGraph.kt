package app.aaps.ios.shell.di

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.database.AppRepository
import app.aaps.database.di.IosAppDatabaseBuilder
import app.aaps.ios.shell.prefs.IosSp
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.impl.utils.IosDateFormatPlatform
import app.aaps.plugins.calibration.NoCalibrationPlugin
import app.aaps.plugins.smoothing.AvgSmoothingPlugin
import app.aaps.plugins.smoothing.ExponentialSmoothingPlugin
import app.aaps.plugins.smoothing.NoSmoothingPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
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
 * ## What is in here, and why not more
 *
 * Leaves only: a class goes in when Metro can build it from what this module already supplies,
 * which is a logger and a text resolver. Those four are not a sample, they are all of them - every
 * `@Inject` class in commonMain whose constructor needs nothing else.
 *
 * The rest are held back by an interface with no iOS implementation, not by anything about Metro:
 *
 * | class                             | still needs                                              |
 * |-----------------------------------|----------------------------------------------------------|
 * | `SensitivityOref1Plugin`          | DateUtil, Preferences                                     |
 * | `SensitivityAAPSPlugin`           | DateUtil, Preferences, ActivePlugin                       |
 * | `SensitivityWeightedAveragePlugin`| DateUtil, Preferences, ActivePlugin                       |
 * | `UnscentedKalmanFilterPlugin`     | Preferences, PersistenceLayer                             |
 * | `LinearCalibrationPlugin`         | DateUtil, PersistenceLayer, NotificationManager, RxBus, ProfileUtil, GlucoseStatusProvider |
 *
 * Add each one here as its dependencies gain common implementations. Faking them instead would
 * only test the fake: `PersistenceLayer` alone has 217 members, `Preferences` 76, `DateUtil` 66.
 * `RxBus` and `GlucoseStatusProvider` have two each, so the sensitivity plugins are the cheapest
 * next step, and they unlock three of the five at once.
 *
 * Metro only validates what a graph can reach, so leaving the others out costs nothing.
 */
@DependencyGraph(AppScope::class)
interface IosProbeGraph {

    val noSmoothing: NoSmoothingPlugin
    val avgSmoothing: AvgSmoothingPlugin
    val exponentialSmoothing: ExponentialSmoothingPlugin
    val noCalibration: NoCalibrationPlugin

    /** Real AAPS classes, built from the iOS implementations underneath them. */
    val dateUtil: DateUtil
    val fabricPrivacy: FabricPrivacy
    val repository: AppRepository

    @Provides fun logger(): AAPSLogger = ProbeLogger
    @Provides fun textResolver(): TextResolver = ProbeTextResolver

    /** NSUserDefaults, the same store the preference layer will sit on. */
    @Provides
    @SingleIn(AppScope::class)
    fun keyValueStore(): KeyValueStore = IosSp()

    /** Dates through the iOS formatter, so this is the production class rather than a stand-in. */
    @Provides
    @SingleIn(AppScope::class)
    fun dateUtil(): DateUtil = DateUtilImpl(IosDateFormatPlatform())

    /**
     * A real SQLite database, in a file of its own.
     *
     * Named apart from the app's so a probe run cannot disturb real data.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun repository(): AppRepository = IosAppDatabaseBuilder().provideAppRepository("aaps-ios-graph.db")

    @DependencyGraph.Factory
    fun interface Factory {

        fun create(): IosProbeGraph
    }
}
