package app.aaps.implementation.queue

/**
 * What draining the pump command queue needs from the operating system.
 *
 * `CommandExecutor` is otherwise plain Kotlin - a state machine over the queue - so it lives in
 * commonMain and takes one of these. Three things are genuinely per platform and nothing else is:
 * keeping the device awake while a command runs, knowing whether Bluetooth may be used at all, and
 * restarting the radio when a connection wedges.
 *
 * Injected rather than `expect`/`actual` for the same reason as `DateFormatPlatform`: every Android
 * answer needs a `Context`, and an `expect object` has no constructor to give one to. It also lets a
 * test supply a fake and assert what the executor asked for.
 */
interface CommandExecutionPlatform {

    /**
     * Keeps the device awake for up to [durationMs] while commands are executed, or null when the
     * platform has no such thing.
     *
     * Not a nicety: a bolus must finish with the screen off. Android answers with a partial wake
     * lock. A platform that cannot make this promise must return null rather than a handle that does
     * nothing, so the caller can see the difference.
     */
    fun acquireWakeLock(tag: String, durationMs: Long): WakeLockHandle?

    /**
     * Whether the app may talk to a pump over Bluetooth right now.
     *
     * Android gates this behind runtime permissions that the user can revoke at any time, so it is
     * asked on every pass of the drain loop. A platform that declares its Bluetooth use up front and
     * has nothing to revoke answers true.
     */
    fun hasBluetoothPermission(): Boolean

    /**
     * Whether this platform can turn the Bluetooth radio off and on again - the "BT watchdog" that
     * recovers a wedged adapter after a connection timeout.
     *
     * Reported rather than assumed, because it is not universal: iOS has no API for it at all. Where
     * this is false the caller must take the same path it takes when the user has the watchdog
     * preference switched off, not silently pretend the restart happened.
     */
    val canRestartBluetooth: Boolean

    /**
     * Turns the radio off and on again, with the settling waits the operation needs. Only called when
     * [canRestartBluetooth] is true.
     */
    suspend fun restartBluetooth()
}

/** Releases the wake taken by [CommandExecutionPlatform.acquireWakeLock]. Safe to call twice. */
interface WakeLockHandle {

    fun release()
}
