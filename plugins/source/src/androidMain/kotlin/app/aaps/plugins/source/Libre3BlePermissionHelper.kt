package app.aaps.plugins.source

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Asks for the Bluetooth permissions the Libre 3 start needs.
 *
 * NFC is not here on purpose: it is granted when the app is installed, so it never has to be
 * asked for while the user is standing there with a sensor on their arm.
 */
object Libre3BlePermissionHelper {

    const val REQUEST_CODE: Int = 44701

    /** Android 12 or newer, which is what the app needs, so location is never asked for. */
    fun requiredPermissions(): Array<String> =
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

    fun missingPermissions(activity: Activity): Array<String> =
        requiredPermissions()
            .filter { ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()

    fun hasAll(activity: Activity): Boolean = missingPermissions(activity).isEmpty()

    fun requestMissing(activity: Activity) {
        val missing = missingPermissions(activity)
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(activity, missing, REQUEST_CODE)
    }
}
