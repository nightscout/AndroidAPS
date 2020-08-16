package info.nightscout.androidaps.plugins.pump.common.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlePreCheck @Inject constructor(
    val resourceHelper: ResourceHelper
) {

    companion object {
        private const val PERMISSION_REQUEST_COARSE_LOCATION = 30241 // arbitrary.
    }

    fun prerequisitesCheck(activity: AppCompatActivity): Boolean {
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            OKDialog.show(activity, resourceHelper.gs(R.string.message), resourceHelper.gs(R.string.ble_not_supported))
            return false
        } else {
            // Use this check to determine whether BLE is supported on the device. Then
            // you can selectively disable BLE-related features.
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // your code that requires permission
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST_COARSE_LOCATION)
            }

            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            // Ensures Bluetooth is available on the device and it is enabled. If not,
            // displays a dialog requesting user permission to enable Bluetooth.
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                OKDialog.show(activity, resourceHelper.gs(R.string.message), resourceHelper.gs(R.string.ble_not_enabled))
                return false
            } else {
                // Will request that GPS be enabled for devices running Marshmallow or newer.
                if (!isLocationEnabled(activity)) {
                    requestLocation(activity)
                    return false
                }
            }
        }
        return true
    }

    /**
     * Determine if GPS is currently enabled.
     *
     *
     * On Android 6 (Marshmallow), location needs to be enabled for Bluetooth discovery to work.
     *
     * @param context The current app context.
     * @return true if location is enabled, false otherwise.
     */
    private fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Prompt the user to enable GPS location if it isn't already on.
     *
     * @param activity The currently visible activity.
     */
    private fun requestLocation(activity: AppCompatActivity) {
        if (isLocationEnabled(activity)) {
            return
        }

        // Shamelessly borrowed from http://stackoverflow.com/a/10311877/868533
        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.location_not_found_title), resourceHelper.gs(R.string.location_not_found_message), Runnable {
            activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        })
    }
}