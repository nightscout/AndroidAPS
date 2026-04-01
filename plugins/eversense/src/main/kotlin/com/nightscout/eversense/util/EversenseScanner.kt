package com.nightscout.eversense.util

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import com.nightscout.eversense.callbacks.EversenseScanCallback
import com.nightscout.eversense.models.EversenseScanResult

class EversenseScanner(private val callback: EversenseScanCallback): ScanCallback() {

    @SuppressLint("MissingPermission")
    override fun onScanResult(callbackType: Int, scanRecord: ScanResult) {
        // Allow devices with null names — use address as fallback label.
        // Some Eversense transmitters may advertise without a device name.
        val deviceName = scanRecord.device?.name ?: scanRecord.device?.address ?: return

        // Filter to only show Eversense transmitters — they start with "T3" (E3) or "365"
        if (!deviceName.startsWith("T3") && !deviceName.startsWith("365") && !deviceName.contains("versense", ignoreCase = true)) {
            return
        }

        EversenseLogger.info(TAG, "Found Eversense device: $deviceName (address: ${scanRecord.device?.address})")
        callback.onResult(EversenseScanResult(deviceName, scanRecord.rssi, scanRecord.device))
    }

    override fun onScanFailed(errorCode: Int) {
        EversenseLogger.error(TAG, "BLE scan failed with error code: $errorCode")
    }

    companion object {
        private const val TAG = "EversenseScanner"
    }
}