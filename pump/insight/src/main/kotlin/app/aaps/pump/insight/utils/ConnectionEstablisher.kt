package app.aaps.pump.insight.utils

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import androidx.annotation.RequiresPermission
import app.aaps.core.data.time.T
import app.aaps.core.utils.extensions.safeEnable
import java.io.IOException
import java.util.UUID

class ConnectionEstablisher(
    private val callback: Callback,
    private val forPairing: Boolean,
    private val bluetoothAdapter: BluetoothAdapter,
    private val bluetoothDevice: BluetoothDevice,
    private var socket: BluetoothSocket?
) : Thread() {

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun run() {
        try {
            if (!bluetoothAdapter.isEnabled) {
                bluetoothAdapter.safeEnable()
                sleep(2000)
            }
        } catch (_: InterruptedException) {
            return
        }
        if (forPairing && bluetoothDevice.bondState != BluetoothDevice.BOND_NONE) {
            try {
                val removeBond = bluetoothDevice.javaClass.getMethod("removeBond")
                removeBond.invoke(bluetoothDevice)
            } catch (e: ReflectiveOperationException) {
                if (!isInterrupted) callback.onConnectionFail(e, 0)
                return
            }
        }
        try {
            if (socket == null) {
                socket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"))
                callback.onSocketCreated(socket)
            }
        } catch (e: IOException) {
            if (!isInterrupted) callback.onConnectionFail(e, 0)
            return
        }
        val connectionStart = System.currentTimeMillis()
        try {
            socket?.connect()
            if (!isInterrupted) callback.onConnectionSucceed()
        } catch (e: IOException) {
            if (!isInterrupted) callback.onConnectionFail(e, System.currentTimeMillis() - connectionStart)
        }
    }

    fun close(closeSocket: Boolean) {
        try {
            interrupt()
            socket?.let { if (closeSocket && it.isConnected) it.close() }
        } catch (_: IOException) {
        }
    }

    interface Callback {

        fun onSocketCreated(bluetoothSocket: BluetoothSocket?)
        fun onConnectionSucceed()
        fun onConnectionFail(e: Exception?, duration: Long)
    }
}