package app.aaps.desktop.shell.platform

import app.aaps.implementation.queue.CommandExecutionPlatform
import app.aaps.implementation.queue.WakeLockHandle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The desktop side of [CommandExecutionPlatform].
 *
 * Every answer here is real rather than a stand-in, and each is "no" for a concrete reason:
 *
 * - **No wake lock.** A desktop JVM process is not frozen the way a phone app is; while it runs, it
 *   runs. Returning null says "nothing was taken", which is honest - the alternative, a handle whose
 *   `release()` does nothing, would read like a lock was held. Stopping the *machine* from sleeping
 *   is a different promise, needs a per-OS call, and no desktop caller asks for it.
 * - **No Bluetooth.** A desktop client talks to a master over Nightscout, not to a pump, so there is
 *   no permission to hold and no adapter to restart. [canRestartBluetooth] is false, which is what
 *   stops a caller trying.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopCommandExecutionPlatform @Inject constructor() : CommandExecutionPlatform {

    override fun acquireWakeLock(tag: String, durationMs: Long): WakeLockHandle? = null

    override fun hasBluetoothPermission(): Boolean = false

    override val canRestartBluetooth: Boolean = false

    override suspend fun restartBluetooth() = Unit
}
