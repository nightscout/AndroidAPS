package app.aaps.plugins.libre3

import app.aaps.plugins.libre3.gatt.Libre3GattClient
import app.aaps.plugins.libre3.identity.Libre3SensorPrefs
import app.aaps.plugins.libre3.identity.Libre3SensorStore

/**
 * Everything the driver needs from the phone it is running on.
 *
 * This used to be an Android `Context` handed to `Libre3CgmDriver.setContext`. A `Context` is two
 * different favours rolled into one here - a private key-value file and a GATT stack - so the
 * favours are named instead, and each platform answers the ones it can answer.
 *
 * ⚠️ Nothing here may return something that pretends to work. A GATT client that quietly reported
 * nothing would be read by the dosing engine as "no glucose", which is not the same as a reading.
 * A platform that cannot open a link says so.
 */
interface Libre3Platform {

    /**
     * The private key-value file named [fileName], for one sensor slot.
     *
     * @param fileName from [Libre3SensorStore.prefsFileName], which already carries the slot
     *   namespace. The platform only has to find or make a file of that name.
     */
    fun sensorPrefs(fileName: String): Libre3SensorPrefs

    /** A fresh GATT client for one connection attempt. */
    fun gattClient(): Libre3GattClient
}

/**
 * The store of one slot, built on this platform's own files.
 *
 * @param namespace null or blank is the production slot. A name gives the pre-soak slot its own
 *   file. The receiver id always comes from the production file, because it is the identity of
 *   this phone and not of a sensor.
 */
fun Libre3Platform.sensorStore(namespace: String? = null): Libre3SensorStore {
    val fileName = Libre3SensorStore.prefsFileName(namespace)
    val slotPrefs = sensorPrefs(fileName)
    val identityPrefs =
        if (namespace.isNullOrBlank()) slotPrefs else sensorPrefs(Libre3SensorStore.prefsFileName(null))
    return Libre3SensorStore(prefs = slotPrefs, identityPrefs = identityPrefs, prefsName = fileName)
}
