package app.aaps.implementation.queue

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSActivityUserInitiated
import platform.Foundation.beginActivityWithOptions
import platform.Foundation.endActivity

/**
 * [CommandExecutionPlatform] on iOS.
 *
 * Read [acquireWakeLock] before relying on it: the promise is weaker than Android's wake lock, and
 * the difference matters to a pump driver.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class IosCommandExecutionPlatform @Inject constructor() : CommandExecutionPlatform {

    /**
     * `NSProcessInfo.beginActivityWithOptions` is the closest iOS equivalent: it tells the system not
     * to idle-sleep while the activity is alive. It is the right tool for "do not nap while a bolus is
     * being delivered".
     *
     * It is NOT the same promise as Android's partial wake lock, and the difference is worth knowing
     * rather than hiding: this holds off *idle sleep*, it does not keep the app running once iOS
     * suspends it in the background. A pump driver that needs to keep working while backgrounded has
     * to hold a Core Bluetooth background session as well - that belongs to the driver, not here.
     * [durationMs] is therefore advisory; the activity lives until it is released.
     */
    override fun acquireWakeLock(tag: String, durationMs: Long): WakeLockHandle? {
        val token = NSProcessInfo.processInfo.beginActivityWithOptions(NSActivityUserInitiated, tag)
        return object : WakeLockHandle {

            private var released = false

            override fun release() {
                if (released) return
                released = true
                NSProcessInfo.processInfo.endActivity(token)
            }
        }
    }

    /**
     * True, and that is the truthful answer rather than a convenient one. iOS declares Bluetooth use
     * in `Info.plist` and prompts once on first use; there is no revocable runtime permission for the
     * drain loop to re-check on every pass. The same reasoning as `bluetoothPermissionGroup()`
     * returning null on this platform.
     */
    override fun hasBluetoothPermission(): Boolean = true

    /**
     * iOS has no API to turn the radio off and on - not a restricted one, none at all. Reporting false
     * makes the executor take exactly the path it takes when the user has the watchdog switched off,
     * instead of a `restartBluetooth()` that returns quietly having done nothing.
     */
    override val canRestartBluetooth: Boolean get() = false

    override suspend fun restartBluetooth() = error("Bluetooth cannot be restarted on iOS; guard with canRestartBluetooth")
}
