package com.nightscout.eversense

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission

class EversenseCGMPlugin {
    private var context: Context? = null
    private var bluetoothManager: BluetoothManager? = null

    fun setContext(context: Context) {
        this.context = context.applicationContext
        this.bluetoothManager = this.context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        val bluetoothManager = this.bluetoothManager ?:run {
            Log.e(TAG, "No bluetooth manager available. Make sure setContext has been called")
            return
        }

        // bluetoothManager.adapter.startLeScan([EversenseGattCallback.serviceUUID], callback)
    }

    companion object {
        private val TAG = "EversenseCGMManager"
        val instance:EversenseCGMPlugin by lazy {
            EversenseCGMPlugin()
        }
    }
}
