package app.aaps.ios.shell.di

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.plugins.calibration.NoCalibrationPlugin
import app.aaps.plugins.smoothing.AvgSmoothingPlugin
import app.aaps.plugins.smoothing.ExponentialSmoothingPlugin
import app.aaps.plugins.smoothing.NoSmoothingPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides

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

    @Provides fun logger(): AAPSLogger = ProbeLogger
    @Provides fun textResolver(): TextResolver = ProbeTextResolver

    @DependencyGraph.Factory
    fun interface Factory {

        fun create(): IosProbeGraph
    }
}
