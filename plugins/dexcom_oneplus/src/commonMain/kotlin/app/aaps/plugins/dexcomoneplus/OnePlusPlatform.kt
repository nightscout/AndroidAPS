package app.aaps.plugins.dexcomoneplus

import app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattClient
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorPrefs
import app.aaps.plugins.dexcomoneplus.identity.OnePlusStoredSession
import app.aaps.plugins.dexcomoneplus.oem.OemDeviceProfile
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScanner
import app.aaps.plugins.dexcomoneplus.session.OnePlusCpuLease

/**
 * Everything the driver needs from the phone it is running on.
 *
 * This used to be an Android `Context` handed to `OnePlusCgmDriver.setContext`. A `Context` is four
 * different favours rolled into one - a private key-value file, an LE scanner, a GATT stack and a
 * wake lock - and only the first of those means anything on a second platform. So the favours are
 * named here instead, and each platform answers the four it can answer.
 *
 * The driver asks for these once, in [OnePlusCgmDriver.attach], and keeps the platform for as long
 * as it lives: a session is rebuilt on every reconnect and needs a fresh GATT client each time.
 *
 * ⚠️ Nothing here may return something that pretends to work. A platform that cannot honour one of
 * these says so - [cpuLease] returns a lease that hands back null, and a GATT client refuses the
 * calls it cannot make. A CGM that quietly reports nothing is read by the dosing engine as "no
 * glucose", which is not the same as a reading.
 */
interface OnePlusPlatform {

    /**
     * The private key-value file named [fileName], for one sensor slot.
     *
     * @param fileName from `OnePlusSensorStore.prefsFileName`, which already carries the slot
     *   namespace. The platform only has to find or make a file of that name.
     */
    fun sensorPrefs(fileName: String): OnePlusSensorPrefs

    /** The LE scanner for [slot], ranking what it hears against [hint]. */
    fun scanner(hint: OnePlusStoredSession?, slot: String): OnePlusBleScanner

    /** A fresh GATT client for one connection attempt, honouring the quirks in [profile]. */
    fun gattClient(profile: OemDeviceProfile): OnePlusGattClient

    /** Keeps the processor awake while [slot] connects and authenticates. */
    fun cpuLease(slot: String): OnePlusCpuLease
}
