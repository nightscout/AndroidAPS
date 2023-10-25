package info.nightscout.pump.common.driver.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context

interface PumpBLESelector {

    /**
     * Called on resume
     */
    fun onResume()

    /**
     * Called on destroy
     */
    fun onDestroy()

    /**
     * This method is called when device is being removed (it can be empty if you don't need to do any special action, but if you
     * have to unbound (for example), then this is method where to call it. For unbounding removeBond is available
     */
    fun removeDevice(device: BluetoothDevice)

    /**
     * Cleanup method after device was removed
     */
    fun cleanupAfterDeviceRemoved()

    /**
     * operations when scan failed
     */
    fun onScanFailed(context: Context, errorCode: Int)

    /**
     * operations when scan starts
     */
    fun onStartLeDeviceScan(context: Context)

    /**
     * operations when scan stops
     */
    fun onStopLeDeviceScan(context: Context)

    /**
     * operations when scan was stopped manually (press on button)
     */
    fun onManualStopLeDeviceScan(context: Context)

    /**
     * operations when on non manual stop of scan (on timeout)
     */
    fun onNonManualStopLeDeviceScan(context: Context)

    /**
     * get Scan Filters
     */
    fun getScanFilters(): List<ScanFilter>?

    /**
     * get Scan Settings
     */
    fun getScanSettings(): ScanSettings?

    /**
     * filter device on search (for cases where we can't do it with Scan Filters
     */
    fun filterDevice(device: BluetoothDevice): BluetoothDevice?

    /**
     * operations when device selected
     */
    fun onDeviceSelected(bluetoothDevice: BluetoothDevice, bleAddress: String, deviceName: String)

    /**
     * If pump has no name, this name will be used
     */
    fun getUnknownPumpName(): String

    /**
     * get Address of Currently selected pump, empty string if none
     */
    fun currentlySelectedPumpAddress(): String

    /**
     * get Name of Currently selected pump, getUnknownPumpName() string if none
     */
    fun currentlySelectedPumpName(): String

    /**
     * Get Translation Text
     */
    fun getText(key: PumpBLESelectorText): String

}

enum class PumpBLESelectorText {
    SCAN_TITLE,
    SELECTED_PUMP_TITLE,
    REMOVE_TITLE,
    REMOVE_TEXT,
    NO_SELECTED_PUMP,
    PUMP_CONFIGURATION
}
