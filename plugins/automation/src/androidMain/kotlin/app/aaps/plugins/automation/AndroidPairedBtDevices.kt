package app.aaps.plugins.automation

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Reads the paired devices from the Android Bluetooth adapter.
 *
 * This used to live inside `TriggerBTDevice` as a private function. It lost its caller when the
 * trigger editor moved to Compose, which left the device dropdown empty - see
 * `TriggerBTDeviceEditor`.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidPairedBtDevices @Inject constructor(
    private val context: Context
) : PairedBtDevices {

    override fun names(): List<String>? {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            return null
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        // The adapter is null on a phone with no Bluetooth at all. That is "no devices", not
        // "not allowed", so it stays an empty list.
        return manager?.adapter?.bondedDevices.orEmpty().mapNotNull { it.name }
    }
}
