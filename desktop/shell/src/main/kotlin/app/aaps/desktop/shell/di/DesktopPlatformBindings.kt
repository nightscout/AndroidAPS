package app.aaps.desktop.shell.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import app.aaps.desktop.shell.platform.DesktopSystemNotificationPlatform
import app.aaps.implementation.notifications.CommonNotificationManager
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.desktop.shell.config.DesktopClientConfig
import app.aaps.desktop.shell.prefs.DesktopSp
import app.aaps.implementation.logging.AAPSLoggerDesktop
import app.aaps.shared.impl.logging.LImpl
import app.aaps.shared.impl.rx.bus.RxBusImpl
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.impl.utils.JvmDateFormatPlatform
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The bindings the desktop graph needs that only a desktop can supply.
 *
 * The counterpart of what `:app` states on Android and `IosPlatformBindings` on Apple. Everything
 * here is either a real desktop implementation or a shared one being handed its platform piece -
 * nothing in this file is a stand-in.
 *
 * `@ContributesTo(AppScope::class)`, so the graph does not have to include it by name.
 *
 * The database is deliberately **not** here: the graph names its own file, so a future probe graph
 * cannot write into the app's data.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object DesktopPlatformBindings {

    /** Console for now, and a rotating file beside the database for afterwards. */
    @Provides
    @SingleIn(AppScope::class)
    fun logger(): AAPSLogger = AAPSLoggerDesktop()

    @Provides
    @SingleIn(AppScope::class)
    fun textResolver(): TextResolver = DesktopTextResolver

    /** A properties file next to the database, which `PreferencesImpl` sits on unchanged. */
    @Provides
    @SingleIn(AppScope::class)
    fun keyValueStore(): KeyValueStore = DesktopSp()

    @Provides
    @SingleIn(AppScope::class)
    fun dateUtil(): DateUtil = DateUtilImpl(JvmDateFormatPlatform())

    @Provides
    @SingleIn(AppScope::class)
    fun config(): Config = DesktopClientConfig()

    @Provides
    @SingleIn(AppScope::class)
    fun rxBus(logger: AAPSLogger): RxBus = RxBusImpl(logger)

    /**
     * The scope everything app-lifetime long runs on. `Dispatchers.Default` rather than a UI one:
     * nothing here draws, and work that must touch the UI hops to it itself.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @SingleIn(AppScope::class)
    @ApplicationScope
    fun qualifiedAppScope(scope: CoroutineScope): CoroutineScope = scope

    @Provides
    @SingleIn(AppScope::class)
    fun l(preferences: Preferences): L = LImpl { preferences }

    @Provides
    @SingleIn(AppScope::class)
    fun systemNotificationPlatform(logger: AAPSLogger): SystemNotificationPlatform =
        DesktopSystemNotificationPlatform(logger)

    /** The shared manager decides what exists; the tray above only shows it. */
    @Provides
    @SingleIn(AppScope::class)
    fun notificationManager(
        logger: AAPSLogger,
        textResolver: TextResolver,
        platform: SystemNotificationPlatform,
        scope: CoroutineScope
    ): NotificationManager = CommonNotificationManager(logger, textResolver, platform, scope)
}
