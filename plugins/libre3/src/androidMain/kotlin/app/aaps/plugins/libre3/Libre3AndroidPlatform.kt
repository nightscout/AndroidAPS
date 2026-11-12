package app.aaps.plugins.libre3

import android.content.Context
import app.aaps.plugins.libre3.gatt.Libre3GattClient
import app.aaps.plugins.libre3.gatt.Libre3GattClientAndroid
import app.aaps.plugins.libre3.identity.Libre3SensorPrefs
import app.aaps.plugins.libre3.identity.Libre3SensorPrefsAndroid

/**
 * [Libre3Platform] on Android: the same two pieces the driver used to build from a `Context`
 * itself, now built here so the driver no longer knows what a `Context` is.
 */
class Libre3AndroidPlatform(context: Context) : Libre3Platform {

    private val appContext: Context = context.applicationContext

    override fun sensorPrefs(fileName: String): Libre3SensorPrefs =
        Libre3SensorPrefsAndroid(appContext, fileName)

    override fun gattClient(): Libre3GattClient = Libre3GattClientAndroid(appContext)
}

/**
 * Attach an Android host to a driver.
 *
 * Kept so the screens and the plugin still read the way they always did. It is an extension, not a
 * method on [Libre3CgmDriver], because a `Context` has no place in a contract that is shared
 * code.
 */
fun Libre3CgmDriver.setContext(context: Context) {
    attach(Libre3AndroidPlatform(context))
}
