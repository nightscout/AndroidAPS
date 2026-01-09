package com.nightscout.eversense.util

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import com.nightscout.eversense.callbacks.EversenseScanCallback
import com.nightscout.eversense.models.EversenseScanResult

class EversenseScanner(private val callback: EversenseScanCallback): ScanCallback() {
    @SuppressLint("MissingPermission")
    override fun onScanResult(callbackType: Int, scanRecord: ScanResult) {
        if (scanRecord.device?.name == null) {
            return
        }

        EversenseLogger.info(TAG, "Found device: ${scanRecord.device.name}")
        callback.onResult(EversenseScanResult(scanRecord.device.name, scanRecord.rssi, scanRecord.device))
    }

    companion object {
        private val TAG = "EversenseScanner"
    }
}