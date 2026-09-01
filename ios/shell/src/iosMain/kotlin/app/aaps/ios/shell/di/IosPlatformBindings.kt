package app.aaps.ios.shell.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.logging.AAPSLoggerIos
import app.aaps.implementation.notifications.CommonNotificationManager
import app.aaps.implementation.notifications.IosSystemNotificationPlatform
import app.aaps.ios.shell.config.IosClientConfig
import app.aaps.ios.shell.prefs.IosSp
import app.aaps.shared.impl.logging.LImpl
import app.aaps.shared.impl.rx.bus.RxBusImpl
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.impl.utils.IosDateFormatPlatform
import app.aaps.workflow.CalculationExecutor
import app.aaps.workflow.PostCalculationRunner
import app.aaps.workflow.PrepareGraphDataRunner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    fun logger(): AAPSLogger = AAPSLoggerIos()

    /**
     * Strings, as far as iOS can do them today.
     *
     * [IosTextResolver] answers with the name of the string rather than its text, because iOS has
     * no reader for Android resource files yet. Screens therefore render with readable placeholders
     * instead of blanks, which is what makes a first pass worth looking at.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun textResolver(): TextResolver = IosTextResolver

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

    /** The production bus, which is already common code. */
    @Provides
    @SingleIn(AppScope::class)
    fun rxBus(logger: AAPSLogger): RxBus = RxBusImpl(logger)

    /** The scope long lived work runs in, the counterpart of the app's own. */
    @Provides
    @SingleIn(AppScope::class)
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The same scope again, under the qualifier shared code asks for.
     *
     * Derived from the unqualified one rather than built separately, so both names mean the same
     * scope - two scopes would look identical and cancel work the other half was still waiting on.
     */
    @Provides
    @ApplicationScope
    fun qualifiedAppScope(scope: CoroutineScope): CoroutineScope = scope

    /**
     * Which log tags are switched on, read back from preferences.
     *
     * `LImpl` takes a `Preferences` accessor rather than the object, because log elements are built
     * once on first use and the preference layer is not ready while the graph is being assembled.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun l(preferences: Preferences): L = LImpl { preferences }

    /**
     * Bolus progress, stated rather than annotated: the class carries no DI annotations on purpose,
     * because `javax.inject` does not resolve in commonMain.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun bolusProgressData(ch: ConcentrationHelper, @ApplicationScope scope: CoroutineScope): BolusProgressData =
        BolusProgressData(ch, scope)

    /** Notifications through UNUserNotificationCenter, with the shared registry above it. */
    @Provides
    @SingleIn(AppScope::class)
    fun systemNotificationPlatform(logger: AAPSLogger, alarmSoundPlayer: AlarmSoundPlayer): SystemNotificationPlatform =
        IosSystemNotificationPlatform(logger, alarmSoundPlayer)

    @Provides
    @SingleIn(AppScope::class)
    fun notificationManager(
        logger: AAPSLogger,
        textResolver: TextResolver,
        platform: SystemNotificationPlatform,
        scope: CoroutineScope
    ): NotificationManager = CommonNotificationManager(logger, textResolver, platform, scope)

    /**
     * The shared coroutine executor, stated rather than contributed.
     *
     * `CoroutineCalculationExecutor` lives in `commonMain` and is already the right one for iOS -
     * its own docs say a coroutine is enough anywhere that is not Android. It carries no
     * `@ContributesBinding` on purpose, so it cannot collide with the WorkManager one Android
     * contributes, which is why the binding has to be named here.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun calculationExecutor(
        scope: CoroutineScope,
        logger: AAPSLogger,
        prepare: Provider<PrepareGraphDataRunner>,
        post: Provider<PostCalculationRunner>
    ): CalculationExecutor = LazyCalculationExecutor(scope, logger, prepare, post)
}
