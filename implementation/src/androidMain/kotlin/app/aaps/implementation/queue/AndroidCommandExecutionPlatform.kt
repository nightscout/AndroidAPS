package app.aaps.implementation.queue

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.aaps.core.utils.extensions.safeDisable
import app.aaps.core.utils.extensions.safeEnable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.delay

/**
 * [CommandExecutionPlatform] on Android: a partial wake lock, so the CPU keeps running while a pump
 * command is in flight even if the screen goes off.
 *
 * The lock is taken with a timeout, so a command that never releases it cannot hold the CPU awake for
 * the life of the process.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidCommandExecutionPlatform @Inject constructor(
    private val context: Context
) : CommandExecutionPlatform {

    override fun acquireWakeLock(tag: String, durationMs: Long): WakeLockHandle? {
        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager?)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
            ?: return null
        wakeLock.acquire(durationMs)
        return object : WakeLockHandle {
            override fun release() {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    override fun hasBluetoothPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    override val canRestartBluetooth: Boolean get() = true

    /**
     * The waits are part of the operation: the adapter reports its new state before it is usable, so
     * disabling and immediately enabling leaves it in a state a driver cannot connect through. These
     * are the same one second settles the executor used inline before.
     */
    override suspend fun restartBluetooth() {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter ?: return
        adapter.safeDisable(0)
        delay(1000)
        adapter.safeEnable(0)
        delay(1000)
    }
}
