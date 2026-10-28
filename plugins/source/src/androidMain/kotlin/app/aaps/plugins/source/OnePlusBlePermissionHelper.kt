package app.aaps.plugins.source

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Runtime BLE permission helpers for the ONE+ start screen.
 * The manifest declarations live in the app and driver modules; this only requests at runtime.
 */
object OnePlusBlePermissionHelper {

    const val REQUEST_CODE: Int = 44601

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun missingPermissions(activity: Activity): Array<String> {
        return requiredPermissions()
            .filter {
                ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()
    }

    fun hasAll(activity: Activity): Boolean = missingPermissions(activity).isEmpty()

    fun requestMissing(activity: Activity) {
        val missing = missingPermissions(activity)
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missing, REQUEST_CODE)
        }
    }
}
