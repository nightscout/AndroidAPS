package app.aaps.plugins.source

import android.content.Context
import android.content.Intent
import app.aaps.core.keys.interfaces.PreferenceItem
import app.aaps.core.keys.interfaces.withClick
import app.aaps.plugins.dexcomoneplus.OnePlusAndroidPlatform
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDriver
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDrivers
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseWatcher
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorStore
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScanner
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScannerAndroid
import app.aaps.plugins.source.activities.DexcomOnePlusStartActivity
import app.aaps.plugins.source.activities.DexcomOnePlusStatusActivity
import app.aaps.plugins.source.activities.DexcomOnePlusWarmupActivity
import app.aaps.plugins.source.keys.DexcomOnePlusIntentKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * [DexcomOnePlusHost] on Android: the driver registry, the three setup screens, the
 * `connectedDevice` foreground service and the status-bar warm-up notification.
 */
@ContributesBinding(AppScope::class, binding = binding<DexcomOnePlusHost>())
@SingleIn(AppScope::class)
@Inject
class DexcomOnePlusAndroidHost(
    private val context: Context,
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

    override fun screenItems(plugin: DexcomOnePlusPlugin): List<PreferenceItem> = listOf(
        DexcomOnePlusIntentKey.Status.withClick { open(DexcomOnePlusStatusActivity::class.java) },
        DexcomOnePlusIntentKey.Start.withClick { open(DexcomOnePlusStartActivity::class.java) },
        DexcomOnePlusIntentKey.Warmup.withClick { open(DexcomOnePlusWarmupActivity::class.java) },
    )

    private fun open(screen: Class<*>) {
        context.startActivity(
            Intent(context, screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
