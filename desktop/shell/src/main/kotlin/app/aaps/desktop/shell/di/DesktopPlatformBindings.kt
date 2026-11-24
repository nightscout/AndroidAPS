package app.aaps.desktop.shell.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.desktop.shell.appIconResource
import app.aaps.desktop.shell.config.DesktopClientConfig
import app.aaps.desktop.shell.loadAwtAppIcon
import app.aaps.desktop.shell.platform.DesktopSystemNotificationPlatform
import app.aaps.desktop.shell.secure.DesktopStorage
import app.aaps.desktop.shell.secure.KeyVault
import app.aaps.implementation.logging.AAPSLoggerDesktop
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.impl.utils.JvmDateFormatPlatform
import app.aaps.workflow.CalculationExecutor
import app.aaps.workflow.LazyCalculationExecutor
import app.aaps.workflow.PostCalculationRunner
import app.aaps.workflow.PrepareGraphDataRunner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

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
    // `L` is deferred: it reads Preferences, which needs a logger. See IosPlatformBindings.
    fun logger(logConfig: () -> L): AAPSLogger = AAPSLoggerDesktop(logConfig = logConfig)

    /**
     * The encrypted settings store next to the database, which `PreferencesImpl` sits on unchanged.
     * Opened by `main` before the graph (see [DesktopStorage]), so this hands out that same store.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun keyValueStore(): KeyValueStore = DesktopStorage.open().store

    /** Where the keys are: the OS key store, or files where there is none. */
    @Provides
    @SingleIn(AppScope::class)
    fun keyVault(): KeyVault = DesktopStorage.open().vault

    @Provides
    @SingleIn(AppScope::class)
    fun dateUtil(): DateUtil = DateUtilImpl(JvmDateFormatPlatform())

    @Provides
    @SingleIn(AppScope::class)
    fun config(): Config = DesktopClientConfig()

    /**
     * The calculation executor, deferred so the graph accepts the runner cycle - see
     * [LazyCalculationExecutor].
     *
     * No wake lock: a desktop does not freeze a running app the way a locked phone does.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun calculationExecutor(
        @ApplicationScope scope: CoroutineScope,
        aapsLogger: AAPSLogger,
        prepare: () -> PrepareGraphDataRunner,
        post: () -> PostCalculationRunner
    ): CalculationExecutor = LazyCalculationExecutor(scope, aapsLogger, { prepare() }, { post() }, wakeLock = null)

    /** The tray. The concrete class too, because the alert platform also shows a message and beeps through it. */
    @Provides
    @SingleIn(AppScope::class)
    fun desktopTray(logger: AAPSLogger, config: Config, rh: TextResolver): DesktopSystemNotificationPlatform =
        DesktopSystemNotificationPlatform(logger, rh.gs(config.appName), loadAwtAppIcon(appIconResource(config)))

    @Provides
    fun systemNotificationPlatform(tray: DesktopSystemNotificationPlatform): SystemNotificationPlatform = tray

}
