package app.aaps.plugins.source

import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.logging.LoggerUtils
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.keys.interfaces.PreferenceItem
import app.aaps.core.keys.interfaces.withClick
import app.aaps.plugins.dexcomoneplus.OnePlusAndroidPlatform
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDriver
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDrivers
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseWatcher
import app.aaps.plugins.dexcomoneplus.OnePlusInstalledApps
import app.aaps.plugins.dexcomoneplus.OnePlusInstalledAppsAndroid
import app.aaps.plugins.dexcomoneplus.OnePlusLogMarkers
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorStore
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScanner
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScannerAndroid
import app.aaps.plugins.source.activities.DexcomOnePlusStartActivity
import app.aaps.plugins.source.activities.DexcomOnePlusWarmupActivity
import app.aaps.plugins.source.keys.DexcomOnePlusIntentKey
import app.aaps.plugins.source.logs.DriverLogReader
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [DexcomOnePlusHost] on Android: the driver registry, the start and warm-up screens, the driver
 * lines of `AndroidAPS.log`, the `connectedDevice` foreground service and the status-bar warm-up
 * notification.
 */
@ContributesBinding(AppScope::class, binding = binding<DexcomOnePlusHost>())
@SingleIn(AppScope::class)
@Inject
class DexcomOnePlusAndroidHost(
    private val context: Context,
    // A function, not the type: the plugin tests build this host around a bare Context, and they
    // never open the log screen.
    private val loggerUtils: () -> LoggerUtils,
) : DexcomOnePlusHost {

    /**
     * Resolved on first use, not in the constructor.
     *
     * Nothing here needs it until the plugin actually runs, and building the host must not depend
     * on a `Context` that can answer `getApplicationContext`. The visibility tests build the host
     * around a bare mock to read `showInList`, and they never reach a screen or a store.
     */
    private val appContext: Context by lazy { context.applicationContext }

    private val platform by lazy { OnePlusAndroidPlatform(appContext) }

    override fun attachPlatform(driver: OnePlusCgmDriver) = driver.attach(platform)

    override fun slotStore(namespace: String?): OnePlusSensorStore =
        OnePlusSensorStore(appContext, namespace)

    override val stagingNamespace: String = OnePlusCgmDrivers.STAGING_NAMESPACE

    private val warmupNotification by lazy { DexcomOnePlusWarmupNotification(appContext) }

    override fun productionDriver(): OnePlusCgmDriver = OnePlusCgmDrivers.default()

    override fun stagingDriver(): OnePlusCgmDriver = OnePlusCgmDrivers.staging()

    override fun existingProductionDriver(): OnePlusCgmDriver? = OnePlusCgmDrivers.existingProduction()

    override fun existingStagingDriver(): OnePlusCgmDriver? = OnePlusCgmDrivers.existingStaging()

    override fun attachProductionDriver(watcher: OnePlusGlucoseWatcher): OnePlusCgmDriver =
        OnePlusCgmDrivers.production(watcher)

    override fun promoteStagingInstance(): OnePlusCgmDriver? = OnePlusCgmDrivers.promoteStagingInstance()

    override fun setSessionServiceWanted(wanted: Boolean) {
        if (wanted) DexcomOnePlusSessionService.start(appContext)
        else DexcomOnePlusSessionService.stop(appContext)
    }

    override fun showWarmup(state: OnePlusWarmupState) = warmupNotification.update(state)

    override fun cancelWarmupNotification() = warmupNotification.cancel()

    override fun alert(message: String) = warmupNotification.alert(message)

    /** The start screen's own scanner instance, with no hint until the screen sets one. */
    override fun screenScanner(): OnePlusBleScanner =
        OnePlusBleScannerAndroid(appContext, sessionHint = null)

    /**
     * The Wear OS data layer. `EventMobileToWear` is picked up by the wear plugin, which sends it to
     * the connected watch; with no watch or the wear plugin off, the message simply goes nowhere,
     * and the Status screen says the watch has not answered.
     */
    override fun watchTransport(rxBus: RxBus): DexcomOnePlusWatchTransport =
        DexcomOnePlusWatchTransport { message -> rxBus.send(EventMobileToWear(message)) }

    /** Android can list the installed apps it declares in its manifest `<queries>`. */
    override val installedApps: OnePlusInstalledApps by lazy { OnePlusInstalledAppsAndroid(appContext) }

    /**
     * Start and Warm-up stay in the settings, as before. Status is not here any more: it is the
     * sensor button of the plugin tab, and the status Activity is still what the notification and
     * the start and warm-up screens open.
     */
    override fun screenItems(plugin: DexcomOnePlusPlugin): List<PreferenceItem> = listOf(
        DexcomOnePlusIntentKey.Start.withClick { open(DexcomOnePlusStartActivity::class.java) },
        DexcomOnePlusIntentKey.Warmup.withClick { open(DexcomOnePlusWarmupActivity::class.java) },
    )

    override fun openScreen(screen: DexcomOnePlusHost.Screen) = when (screen) {
        DexcomOnePlusHost.Screen.START  -> open(DexcomOnePlusStartActivity::class.java)
        DexcomOnePlusHost.Screen.WARMUP -> open(DexcomOnePlusWarmupActivity::class.java)
    }

    /** The ONE+ lines of `AndroidAPS.log`, as the driver log Activity reads them. */
    override suspend fun driverLog(): DexcomOnePlusHost.DriverLog {
        val lines = withContext(Dispatchers.IO) {
            DriverLogReader.read(loggerUtils().logDirectory, setOf(OnePlusLogMarkers.TAG))
        }
        return DexcomOnePlusHost.DriverLog(lines = lines, note = SourceStrings.dexcom_oneplus_log_note_file)
    }

    override fun shareLog(text: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        appContext.startActivity(
            Intent.createChooser(send, appContext.getString(R.string.cgm_driver_log_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun open(screen: Class<*>) {
        context.startActivity(
            Intent(context, screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
