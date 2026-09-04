package app.aaps.ios.shell.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.implementation.logging.AAPSLoggerIos
import app.aaps.implementation.notifications.IosSystemNotificationPlatform
import app.aaps.ios.shell.config.IosClientConfig
import app.aaps.ios.shell.platform.IosHistoryScope
import app.aaps.ios.shell.prefs.IosSp
import app.aaps.ui.compose.history.HistoryScope
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.impl.utils.IosDateFormatPlatform
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

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

    /** NSUserDefaults, the store the preference layer sits on. */
    @Provides
    @SingleIn(AppScope::class)
    fun keyValueStore(): KeyValueStore = IosSp()

    /** Dates through the iOS formatter, so this is the production class rather than a stand-in. */
    @Provides
    @SingleIn(AppScope::class)
    fun dateUtil(): DateUtil = DateUtilImpl(IosDateFormatPlatform())

    /** What kind of build this is. An iOS build is a follower client, not a loop. */
    @Provides
    @SingleIn(AppScope::class)
    fun config(): Config = IosClientConfig()


    /** Notifications through UNUserNotificationCenter, with the shared registry above it. */
    @Provides
    @SingleIn(AppScope::class)
    fun systemNotificationPlatform(logger: AAPSLogger, alarmSoundPlayer: AlarmSoundPlayer): SystemNotificationPlatform =
        IosSystemNotificationPlatform(logger, alarmSoundPlayer)

    /**
     * One history browsing window, app-scoped so every injection point sees the same one.
     *
     * Unscoped would hand out a fresh window per injection point, which quietly undoes the isolation
     * the extension exists for: two windows calculating over different ranges, with the screen
     * reading one and writing the other.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun historyScope(windowFactory: IosHistoryWindowGraph.Factory): HistoryScope =
        IosHistoryScope(windowFactory.create())

}
