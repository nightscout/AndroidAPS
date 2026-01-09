package com.nightscout.eversense

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.os.ParcelUuid
import com.nightscout.eversense.callbacks.EversenseScanCallback
import com.nightscout.eversense.callbacks.EversenseWatcher
import com.nightscout.eversense.models.EversenseState
import com.nightscout.eversense.models.EversenseTransmitterSettings
import com.nightscout.eversense.packets.EversenseE3Communicator
import com.nightscout.eversense.util.EversenseLogger
import com.nightscout.eversense.util.EversenseScanner
import com.nightscout.eversense.util.StorageKeys
import kotlinx.serialization.json.Json

class EversenseCGMPlugin {
    private var context: Context? = null

    private var bluetoothManager: BluetoothManager? = null
    private var preferences: SharedPreferences? = null
    private var gattCallback: EversenseGattCallback? = null

    private var scanner: EversenseScanner? = null
    var watchers: List<EversenseWatcher> = listOf()

    fun setContext(context: Context, loggingEnabled: Boolean) {
        this.context = context
        EversenseLogger.instance.enableLogging(loggingEnabled)

        val preference = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)

        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        preferences = preference
        gattCallback = EversenseGattCallback(this, preference)
    }

    fun addWatcher(watcher: EversenseWatcher) {
        this.watchers += watcher
    }

    fun isConnected(): Boolean {
        val gattCallback = this.gattCallback ?:run {
            return false
        }

        return gattCallback.isConnected()
    }

    fun getCurrentState(): EversenseState? {
        val preferences = preferences ?:run {
            EversenseLogger.error(TAG, "No preferences available. Make sure setContext has been called")
            return null
        }

        val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
        return Json.decodeFromString<EversenseState>(stateJson)
    }

    @SuppressLint("MissingPermission")
    fun startScan(callback: EversenseScanCallback) {
        val bluetoothScanner = this.bluetoothManager?.adapter?.bluetoothLeScanner ?:run {
            EversenseLogger.error(TAG, "No bluetooth manager available. Make sure setContext has been called")
            return
        }

        scanner = EversenseScanner(callback)
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(EversenseGattCallback.serviceUUID)).build()
        )
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        bluetoothScanner.startScan(filters, settings, scanner)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice?): Boolean {
        val bluetoothManager = this.bluetoothManager ?:run {
            EversenseLogger.error(TAG, "No bluetooth manager available. Make sure setContext has been called")
            return false
        }

        val gattCallback = this.gattCallback ?:run {
            EversenseLogger.error(TAG, "No gattCallback available. Make sure setContext has been called")
            return false
        }

        if (scanner != null) {
            bluetoothManager.adapter.bluetoothLeScanner.stopScan(scanner)
        }

        if (gattCallback.isConnected()) {
            EversenseLogger.info(TAG, "Already connected!")
            return true
        }

        if (device != null) {
            EversenseLogger.info(TAG, "Connecting to ${device.name}")
            device.connectGatt(context, true, gattCallback)
            return true
        }

        val address = preferences?.getString(StorageKeys.REMOTE_DEVICE_KEY, null) ?:run {
            EversenseLogger.error(TAG, "Remote device not stored. Make sure you've connected once and bonded to this device")
            return false
        }

        val remoteDevice = bluetoothManager.adapter.getRemoteDevice(address) ?:run {
            EversenseLogger.error(TAG, "Remote device not found. Make sure you've connected once and bonded to this device")
            return false
        }

        remoteDevice.connectGatt(context, true, gattCallback)
        return true
    }

    fun writeSettings(settings: EversenseTransmitterSettings): Boolean {
        val preferences = preferences ?:run {
            EversenseLogger.error(TAG, "No preferences available. Make sure setContext has been called")
            return false
        }

        val gattCallback = this.gattCallback ?:run {
            EversenseLogger.error(TAG, "No gattCallback available. Make sure transmitter is connected before writing settings")
            return false
        }

        if (!gattCallback.isConnected()) {
            EversenseLogger.error(TAG, "Transmitter is not connected...")
            return false
        }

        return EversenseE3Communicator.writeSettings(gattCallback, preferences, settings)
    }

    companion object {
        private const val TAG = "EversenseCGMManager"

        val instance:EversenseCGMPlugin by lazy {
            EversenseCGMPlugin()
        }
    }
}