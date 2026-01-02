package info.nightscout.comboctl.android

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import info.nightscout.comboctl.base.BluetoothPermissionException

private const val bluetoothConnectPermission = "android.permission.BLUETOOTH_CONNECT"
private const val bluetoothScanPermission = "android.permission.BLUETOOTH_SCAN"

class AndroidBluetoothPermissionException(val missingPermissions: List<String>) :
    BluetoothPermissionException("Missing Bluetooth permissions: ${missingPermissions.joinToString(", ")}")

internal fun <T> checkForConnectPermission(androidContext: Context, block: () -> T) =
    checkForPermissions(androidContext, listOf(bluetoothConnectPermission), block)

internal fun <T> checkForPermissions(androidContext: Context, permissions: List<String>, block: () -> T): T {
    return run {
        val missingPermissions = permissions
            .filter {
                ContextCompat.checkSelfPermission(androidContext, it) != PackageManager.PERMISSION_GRANTED
            }
        if (missingPermissions.isEmpty())
            block.invoke()
        else
            throw AndroidBluetoothPermissionException(missingPermissions)
    }
}

internal fun runIfScanPermissionGranted(androidContext: Context, block: () -> Unit): Boolean {
    return if (ContextCompat.checkSelfPermission(androidContext, bluetoothScanPermission) == PackageManager.PERMISSION_GRANTED) {
        block.invoke()
        true
    } else
        false
}
