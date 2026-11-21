package app.aaps.implementation.queue

import app.aaps.implementation.lifecycle.IosBackgroundTime
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import platform.Foundation.NSActivityUserInitiated
import platform.Foundation.NSProcessInfo
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
@Inject
class IosCommandExecutionPlatform() : CommandExecutionPlatform {

    /**
     * Two things together, because iOS has no single equivalent of Android's partial wake lock.
     *
     * - `NSProcessInfo.beginActivityWithOptions` holds off idle sleep while the app is in front, for
     *   "do not nap while a bolus is being delivered".
     * - [IosBackgroundTime] keeps the app running after it went to the background. A locked phone
     *   froze the queue about 10 seconds after the wake that started it, in the middle of a patch
     *   connect. The background time is limited by iOS, to about 30 seconds; after that the queue
     *   freezes until the next Bluetooth event, as before.
     *
     * The background time also ends after [durationMs]. The activity lives until it is released.
     */
    override fun acquireWakeLock(tag: String, durationMs: Long): WakeLockHandle? {
        val token = NSProcessInfo.processInfo.beginActivityWithOptions(NSActivityUserInitiated, tag)
        val backgroundTime = IosBackgroundTime.hold(tag, durationMs)
        return object : WakeLockHandle {

            private var released = false

            override fun release() {
                if (released) return
                released = true
                backgroundTime.release()
                NSProcessInfo.processInfo.endActivity(token)
            }
        }
    }

    /**
     * True. iOS asks for Bluetooth itself on first use. The user can refuse it later in the iOS
     * Settings, and that refusal is shown on the permission sheet (`bluetoothPermissionGroup()`), not
     * checked here.
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
