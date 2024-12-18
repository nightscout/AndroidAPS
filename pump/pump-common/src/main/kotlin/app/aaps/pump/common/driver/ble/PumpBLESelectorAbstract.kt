package app.aaps.pump.common.driver.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.widget.Toast
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.common.R

abstract class PumpBLESelectorAbstract(
    var resourceHelper: ResourceHelper,
    var aapsLogger: AAPSLogger,
    var sp: SP,
    var rxBus: RxBus,
    var context: Context
) : PumpBLESelector {

    protected val TAG = LTag.PUMPBTCOMM

    override fun getScanSettings(): ScanSettings? {
        return ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    }

    override fun getScanFilters(): MutableList<ScanFilter>? {
        return null
    }

    override fun filterDevice(device: BluetoothDevice): BluetoothDevice? {
        return device
    }

    override fun onResume() {
    }

    override fun onDestroy() {
    }

    override fun removeDevice(device: BluetoothDevice) {
    }

    override fun cleanupAfterDeviceRemoved() {
    }

    override fun onManualStopLeDeviceScan(context: Context) {
    }

    override fun onNonManualStopLeDeviceScan(context: Context) {
    }

    //fun onDeviceSelected(bluetoothDevice: BluetoothDevice, bleAddress: String, deviceName: String)

    override fun onScanFailed(context: Context, errorCode: Int) {
        Toast.makeText(
            context, resourceHelper.gs(R.string.ble_config_scan_error, errorCode),
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onStartLeDeviceScan(context: Context) {
        Toast.makeText(context, R.string.ble_config_scan_scanning, Toast.LENGTH_SHORT).show()
    }

    override fun onStopLeDeviceScan(context: Context) {
        Toast.makeText(context, R.string.ble_config_scan_finished, Toast.LENGTH_SHORT).show()
    }

    protected fun removeBond(bluetoothDevice: BluetoothDevice): Boolean {
        return try {
            val method = bluetoothDevice.javaClass.getMethod("removeBond")
            val resultObject = method.invoke(bluetoothDevice)
            if (resultObject == null) {
                aapsLogger.error(TAG, "ERROR: result object is null")
                false
            } else {
                val result = resultObject as Boolean
                if (result) {
                    aapsLogger.info(TAG, "Successfully removed bond")
                } else {
                    aapsLogger.warn(TAG, "Bond was not removed")
                }
                result
            }
        } catch (e: Exception) {
            aapsLogger.error(TAG, "ERROR: could not remove bond")
            e.printStackTrace()
            false
        }
    }

    protected fun getBondingStatusDescription(state: Int): String {
        return if (state == 10) {
            "BOND_NONE"
        } else if (state == 11) {
            "BOND_BONDING"
        } else if (state == 12) {
            "BOND_BONDED"
        } else {
            "UNKNOWN BOND STATUS ($state)"
        }
    }

}