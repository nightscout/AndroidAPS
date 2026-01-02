package app.aaps.implementation.pump

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.utils.extensions.safeEnable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlePreCheckImpl @Inject constructor(
    private val context: Context,
    private val rh: ResourceHelper
) : BlePreCheck {

    companion object {

        private const val PERMISSION_REQUEST_BLUETOOTH = 30242 // arbitrary.
    }

    override fun prerequisitesCheck(activity: AppCompatActivity): Boolean {
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            OKDialog.show(activity, rh.gs(app.aaps.core.ui.R.string.message), rh.gs(app.aaps.core.ui.R.string.ble_not_supported))
            return false
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_REQUEST_BLUETOOTH)
                return false
            }

            val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
            // Ensures Bluetooth is available on the device and it is enabled.
            bluetoothAdapter?.safeEnable(3000)
            if (bluetoothAdapter?.isEnabled != true) {
                OKDialog.show(activity, rh.gs(app.aaps.core.ui.R.string.message), rh.gs(app.aaps.core.ui.R.string.ble_not_enabled))
                return false
            }
        }
        return true
    }
}