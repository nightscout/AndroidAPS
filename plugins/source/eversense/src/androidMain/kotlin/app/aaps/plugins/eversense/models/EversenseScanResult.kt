package app.aaps.plugins.eversense.models

import android.bluetooth.BluetoothDevice

data class EversenseScanResult(val name: String, val rssi: Int, val device: BluetoothDevice)
