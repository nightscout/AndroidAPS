package com.nightscout.eversense

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.UUID

class EversenseGattCallback : BluetoothGattCallback() {

    companion object {
        private val TAG = "EversenseGattCallback"

        public val serviceUUID = UUID.fromString("c3230001-9308-47ae-ac12-3d030892a211")
        private val requestSecureUUID = "6eb0f025-bd60-7aaa-25a7-0029573f4f23"
        private val requestSecureV2UUID = "c3230002-9308-47ae-ac12-3d030892a211"
        private val responseSecureUUID = "6eb0f027-a7ba-7e7d-66c9-6d813f01d273"
        private val responseSecureV2UUID = "c3230003-9308-47ae-ac12-3d030892a211"
        private val magicDescriptorUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var bluetoothGatt: BluetoothGatt? = null

    private var payloadSize: Int = 20

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        Log.i(TAG, "Connection state changed - state: $status, newState: $newState")

        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            bluetoothGatt = gatt

            val success = gatt.requestMtu(512)
            Log.i(TAG, "Requested MTU: $success")
            return
        }

        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.w(TAG, "Disconnected...")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        if (status == 0) {
            payloadSize = mtu - 3
            Log.i(TAG, "New payload size: $payloadSize")
        } else {
            payloadSize = 20
            Log.e(TAG, "Failed to set payload size - status: $status")
        }

        val success = gatt?.discoverServices()
        Log.i(TAG, "Trigger discover services - success: $success")
    }
}
