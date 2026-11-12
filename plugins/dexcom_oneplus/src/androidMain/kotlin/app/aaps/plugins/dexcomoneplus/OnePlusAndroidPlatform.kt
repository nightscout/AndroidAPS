package app.aaps.plugins.dexcomoneplus

import android.content.Context
import app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattClient
import app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattClientAndroid
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorPrefs
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorPrefsAndroid
import app.aaps.plugins.dexcomoneplus.identity.OnePlusStoredSession
import app.aaps.plugins.dexcomoneplus.oem.OemDeviceProfile
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScanner
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScannerAndroid
import app.aaps.plugins.dexcomoneplus.session.OnePlusAndroidCpuLease
import app.aaps.plugins.dexcomoneplus.session.OnePlusCpuLease

/**
 * [OnePlusPlatform] on Android: the same four pieces the driver used to build from a `Context`
 * itself, now built here so the driver no longer knows what a `Context` is.
 */
class OnePlusAndroidPlatform(context: Context) : OnePlusPlatform {

    private val appContext: Context = context.applicationContext

    override fun sensorPrefs(fileName: String): OnePlusSensorPrefs =
        OnePlusSensorPrefsAndroid(appContext, fileName)

    override fun scanner(hint: OnePlusStoredSession?, slot: String): OnePlusBleScanner =
        OnePlusBleScannerAndroid(appContext, sessionHint = hint, slot = slot)

    override fun gattClient(profile: OemDeviceProfile): OnePlusGattClient =
        OnePlusGattClientAndroid(appContext, profile)

    override fun cpuLease(slot: String): OnePlusCpuLease = OnePlusAndroidCpuLease(appContext, slot)
}

/**
 * Attach an Android host to a driver.
 *
 * Kept so the screens and the plugin still read the way they always did. It is an extension, not a
 * method on [OnePlusCgmDriver], because a `Context` has no place in a contract that two platforms
 * share.
 */
fun OnePlusCgmDriver.setContext(context: Context) {
    attach(OnePlusAndroidPlatform(context))
}
