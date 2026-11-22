package app.aaps.ios.shell.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.lifecycle.IosCalculationWakeLock
import app.aaps.implementation.logging.AAPSLoggerIos
import app.aaps.implementation.notifications.IosSystemNotificationPlatform
import app.aaps.ios.shell.config.IosClientConfig
import app.aaps.ios.shell.prefs.IosStorage
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.impl.utils.IosDateFormatPlatform
import app.aaps.workflow.CalculationExecutor
import app.aaps.workflow.LazyCalculationExecutor
import app.aaps.workflow.PostCalculationRunner
import app.aaps.workflow.PrepareGraphDataRunner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope

/**
 * The bindings every iOS graph needs, in one place.
 *
 * This is the iOS counterpart of what `:app` states on Android, and it exists because there are now
 * two graphs that need the same leaves: [IosProbeGraph], which exercises plugins in isolation, and
 * [IosAppGraph], which builds the real app. Written out twice they would drift, and a graph is
 * exactly the wrong place for two nearly-identical lists.
 *
 * `@ContributesTo(AppScope::class)`, so neither graph has to include it by name. It is in `iosMain`,
 * so nothing on Android can see it.
 *
 * The database is deliberately **not** here. Each graph names its own file, so a probe run cannot
 * write into the app's data.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object IosPlatformBindings {

    /** The real iOS logger: NSLog for the console, a rotating file for afterwards. */
    @Provides
    @SingleIn(AppScope::class)
    // `L` is deferred: it reads Preferences, which needs a logger, and the lambda is only invoked
    // when a line is actually written - long after both exist. Without it the logger fell back to
    // the compile-time tag defaults and the log-settings sheet did nothing.
    fun logger(logConfig: () -> L): AAPSLogger = AAPSLoggerIos(logConfig = logConfig)

    /**
     * The encrypted settings store the preference layer sits on. One for the whole process, shared by
     * every graph: DataStore allows only one per file. The app opens it before it builds a graph.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun keyValueStore(): KeyValueStore = IosStorage.open()

    /** The wall clock, for timers that wait for a time of day (`delayUntil`). */
    @Provides
    fun clock(): Clock = Clock.System

    /**
     * The calculation executor, deferred so the graph accepts the runner cycle - see
     * [LazyCalculationExecutor].
     *
     * With background time from iOS ([IosCalculationWakeLock]): the loop runs at the end of a
     * calculation, and on a locked phone iOS froze the run about 10 seconds after the reading that
     * started it.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun calculationExecutor(
        @ApplicationScope scope: CoroutineScope,
        aapsLogger: AAPSLogger,
        prepare: () -> PrepareGraphDataRunner,
        post: () -> PostCalculationRunner
    ): CalculationExecutor = LazyCalculationExecutor(scope, aapsLogger, { prepare() }, { post() }, IosCalculationWakeLock())

    /** Dates through the iOS formatter, so this is the production class rather than a stand-in. */
    @Provides
    @SingleIn(AppScope::class)
    fun dateUtil(): DateUtil = DateUtilImpl(IosDateFormatPlatform())

    /** What kind of build this is - the full app or a client - read from the bundle identifier. */
    @Provides
    @SingleIn(AppScope::class)
    fun config(): Config = IosClientConfig()


    /** Notifications through UNUserNotificationCenter, with the shared registry above it. */
    @Provides
    @SingleIn(AppScope::class)
    fun systemNotificationPlatform(logger: AAPSLogger, alarmSoundPlayer: AlarmSoundPlayer, preferences: Preferences): SystemNotificationPlatform =
        IosSystemNotificationPlatform(logger, alarmSoundPlayer) { preferences.get(BooleanKey.AlertOverrideDoNotDisturb) }

}
