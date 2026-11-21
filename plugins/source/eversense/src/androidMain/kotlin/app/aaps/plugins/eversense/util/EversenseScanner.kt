package app.aaps.plugins.eversense.util

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.eversense.callbacks.EversenseScanCallback
import app.aaps.plugins.eversense.models.EversenseScanResult

class EversenseScanner(private val callback: EversenseScanCallback, private val aapsLogger: AAPSLogger) : ScanCallback() {

    @SuppressLint("MissingPermission")
    override fun onScanResult(callbackType: Int, scanRecord: ScanResult) {
        // Allow devices with null names — use address as fallback label.
        // Some Eversense transmitters may advertise without a device name.
        val deviceName = scanRecord.device?.name ?: scanRecord.device?.address ?: return

        // Filter to only show Eversense transmitters.
        // E3 transmitters advertise as "T" followed by a serial number (e.g. "T0214389", "T3xxxxxx").
        // E365 transmitters advertise starting with "365".
        if (!deviceName.startsWith("T") && !deviceName.startsWith("365") && !deviceName.contains("versense", ignoreCase = true)) {
            return
        }

        aapsLogger.debug(LTag.BGSOURCE) { "Found Eversense device: $deviceName (address: ${scanRecord.device?.address})" }
        callback.onResult(EversenseScanResult(deviceName, scanRecord.rssi, scanRecord.device))
    }

    override fun onScanFailed(errorCode: Int) {
        aapsLogger.error(LTag.BGSOURCE, "BLE scan failed with error code: $errorCode")
    }
}