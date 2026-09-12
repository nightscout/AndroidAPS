package app.aaps.implementation.queue

/**
 * A [CommandExecutionPlatform] that records what the executor asked for.
 *
 * The tests used to hand `CommandExecutor` an Android `Context` and it reached for the power manager
 * and the Bluetooth adapter itself, so none of that was observable. With the platform behind an
 * interface a test can both keep it inert and assert on it - `wakeLocksTaken` is what lets a test
 * check that a drain took a wake and released it, which nothing verified before.
 */
class TestCommandExecutionPlatform(
    private val bluetoothPermission: Boolean = true,
    override val canRestartBluetooth: Boolean = true
) : CommandExecutionPlatform {

    var wakeLocksTaken: Int = 0
        private set
    var wakeLocksReleased: Int = 0
        private set
    var bluetoothRestarts: Int = 0
        private set

    override fun acquireWakeLock(tag: String, durationMs: Long): WakeLockHandle {
        wakeLocksTaken++
        return object : WakeLockHandle {
            override fun release() {
                wakeLocksReleased++
            }
        }
    }

    override fun hasBluetoothPermission(): Boolean = bluetoothPermission

    override suspend fun restartBluetooth() {
        bluetoothRestarts++
    }
}
