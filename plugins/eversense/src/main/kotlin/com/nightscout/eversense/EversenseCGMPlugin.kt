package com.nightscout.eversense

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.os.ParcelUuid
import androidx.core.content.edit
import com.nightscout.eversense.callbacks.EversenseScanCallback
import com.nightscout.eversense.callbacks.EversenseWatcher
import com.nightscout.eversense.models.EversenseState
import com.nightscout.eversense.models.EversenseTransmitterSettings
import com.nightscout.eversense.packets.EversenseE3Communicator
import com.nightscout.eversense.packets.e3.GetSignalStrengthRawPacket
import com.nightscout.eversense.util.EversenseLogger
import com.nightscout.eversense.util.EversenseScanner
import com.nightscout.eversense.util.StorageKeys
import kotlinx.serialization.json.Json

class EversenseCGMPlugin {

    // FIX 1: Use ApplicationContext to avoid leaking Activity context.
    private var context: Context? = null

    private var bluetoothManager: BluetoothManager? = null
    private var preferences: SharedPreferences? = null
    private var gattCallback: EversenseGattCallback? = null

    // FIX 2: Lock object for synchronized access to connection state.
    private val connectionLock = Any()

    private var scanner: EversenseScanner? = null
    var watchers: List<EversenseWatcher> = listOf()

    fun setContext(context: Context, loggingEnabled: Boolean) {
        // FIX 1: Always store applicationContext.
        this.context = context.applicationContext
        EversenseLogger.instance.enableLogging(loggingEnabled)

        val preference = context.applicationContext.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        bluetoothManager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        preferences = preference
        gattCallback = EversenseGattCallback(this, preference)
    }

    fun setSmoothing(value: Boolean): Boolean {
        val state = getCurrentState() ?: run {
            EversenseLogger.error(TAG, "Cannot set smoothing: current state is null. Has setContext been called?")
            return false
        }
        state.useSmoothing = value
        preferences?.edit(commit = true) {
            putString(StorageKeys.STATE, JSON.encodeToString(state))
        }
        return true
    }

    fun addWatcher(watcher: EversenseWatcher) {
        this.watchers += watcher
    }

    fun removeWatcher(watcher: EversenseWatcher) {
        this.watchers -= watcher
    }

    fun isConnected(): Boolean = gattCallback?.isConnected() ?: false
    fun is365(): Boolean = gattCallback?.is365() ?: false

    fun getCurrentState(): EversenseState? {
        val preferences = preferences ?: run {
            EversenseLogger.error(TAG, "No preferences available. Make sure setContext has been called")
            return null
        }
        val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
        return JSON.decodeFromString<EversenseState>(stateJson)
    }

    @SuppressLint("MissingPermission")
    fun startScan(callback: EversenseScanCallback) {
        val bluetoothScanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: run {
            EversenseLogger.error(TAG, "No bluetooth manager available. Make sure setContext has been called")
            return
        }
        scanner = EversenseScanner(callback)
        // Scan without service UUID filter — Eversense transmitters may not always advertise
        // the service UUID before pairing. Show all BLE devices so user can identify their transmitter.
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        bluetoothScanner.startScan(null, settings, scanner)
        EversenseLogger.info(TAG, "BLE scan started")
    }

    // FIX 4: Added public stopScan() so callers can cancel scanning independently.
    @SuppressLint("MissingPermission")
    fun stopScan() {
        val bluetoothScanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: run {
            EversenseLogger.error(TAG, "No bluetooth scanner available when trying to stop scan")
            return
        }
        scanner?.let {
            bluetoothScanner.stopScan(it)
            scanner = null
            EversenseLogger.info(TAG, "Scan stopped")
        } ?: EversenseLogger.info(TAG, "stopScan called but no active scan found")
    }

    // FIX 5: connect() now explicitly differentiates between supplied device vs stored device.
    // FIX 6: Synchronized block prevents race condition on connection state check.
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice? = null): Boolean {
        val bluetoothManager = this.bluetoothManager ?: run {
            EversenseLogger.error(TAG, "No bluetooth manager available. Make sure setContext has been called")
            return false
        }
        val gattCallback = this.gattCallback ?: run {
            EversenseLogger.error(TAG, "No gattCallback available. Make sure setContext has been called")
            return false
        }

        stopScan()

        synchronized(connectionLock) {
            if (gattCallback.isConnected()) {
                EversenseLogger.info(TAG, "Already connected, skipping reconnect")
                return true
            }

            // Clean up stale GATT connections before connecting
            gattCallback.cleanUp()

            return if (device != null) {
                EversenseLogger.info(TAG, "Connecting to supplied device: ${device.name}")
                // Save address so we can auto-reconnect after app restart or phone reboot
                preferences?.edit()?.putString(StorageKeys.REMOTE_DEVICE_KEY, device.address)?.apply()
                EversenseLogger.info(TAG, "Saved device address for auto-reconnect: ${device.address}")
                device.connectGatt(context, true, gattCallback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
                true
            } else {
                val address = preferences?.getString(StorageKeys.REMOTE_DEVICE_KEY, null) ?: run {
                    EversenseLogger.error(TAG, "No device supplied and no stored device address found.")
                    return false
                }
                val remoteDevice = bluetoothManager.adapter.getRemoteDevice(address) ?: run {
                    EversenseLogger.error(TAG, "Could not retrieve remote device for address $address")
                    return false
                }
                EversenseLogger.info(TAG, "Reconnecting to stored device: $address")
                remoteDevice.connectGatt(context, true, gattCallback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
                true
            }
        }
    }

    // FIX 7: Added disconnect() which calls both disconnect() and close() on the GATT client.
    fun clearStoredDevice() {
        preferences?.edit()?.remove(StorageKeys.REMOTE_DEVICE_KEY)?.apply()
        EversenseLogger.info(TAG, "Cleared stored device address")
    }

    fun disconnect() {
        val gattCallback = this.gattCallback ?: run {
            EversenseLogger.info(TAG, "disconnect() called but no gattCallback exists")
            return
        }
        if (!gattCallback.isConnected()) {
            EversenseLogger.info(TAG, "disconnect() called but not currently connected")
            return
        }
        gattCallback.disconnect()
        EversenseLogger.info(TAG, "Disconnected from transmitter")
    }

    // writeSettings delegates to EversenseE3Communicator. Transmitter type (E3 vs 365) is
    // determined at GATT connection time via EversenseSecurityType, not stored in EversenseState.
    fun writeSettings(settings: EversenseTransmitterSettings): Boolean {
        val preferences = preferences ?: run {
            EversenseLogger.error(TAG, "No preferences available. Make sure setContext has been called")
            return false
        }
        val gattCallback = this.gattCallback ?: run {
            EversenseLogger.error(TAG, "No gattCallback available. Make sure transmitter is connected before writing settings")
            return false
        }
        if (!gattCallback.isConnected()) {
            EversenseLogger.error(TAG, "Transmitter is not connected")
            return false
        }
        return EversenseE3Communicator.writeSettings(gattCallback, preferences, settings)
    }


    // Send a blood glucose calibration value to the transmitter.
    // Requires CalibrationReadiness.READY state and an active connection.
    // Returns true if the packet was sent successfully, false otherwise.
    fun sendCalibration(glucoseMgDl: Int, timestampMs: Long = System.currentTimeMillis()): Boolean {
        val gattCallback = this.gattCallback ?: run {
            EversenseLogger.error(TAG, "No gattCallback available. Make sure transmitter is connected before calibrating")
            return false
        }
        if (!gattCallback.isConnected()) {
            EversenseLogger.error(TAG, "Transmitter is not connected")
            return false
        }
        val state = getCurrentState() ?: run {
            EversenseLogger.error(TAG, "Cannot calibrate: state is null")
            return false
        }
        if (state.calibrationReadiness.name != "READY") {
            EversenseLogger.error(TAG, "Transmitter is not ready for calibration: ${state.calibrationReadiness}")
            return false
        }
        return try {
            if (gattCallback.is365()) {
                val packet = com.nightscout.eversense.packets.e365.SetBloodGlucosePointPacket365(glucoseMgDl, timestampMs)
                gattCallback.writePacket<com.nightscout.eversense.packets.e365.SetBloodGlucosePointPacket365.Response>(packet)
                EversenseLogger.info(TAG, "365 calibration sent: $glucoseMgDl mg/dL")
            } else {
                EversenseE3Communicator.sendCalibration(gattCallback, glucoseMgDl)
            }
            true
        } catch (e: Exception) {
            EversenseLogger.error(TAG, "Failed to send calibration: $e")
            false
        }
    }


    // Triggers both a full sync and a glucose read on the connected transmitter.
    // Should be called from a background thread (ioScope).
    fun triggerFullSync(force: Boolean = false) {
        val gattCallback = this.gattCallback ?: run {
            EversenseLogger.error(TAG, "Cannot sync — no gattCallback available")
            return
        }
        val preferences = preferences ?: run {
            EversenseLogger.error(TAG, "Cannot sync — no preferences available")
            return
        }
        if (!gattCallback.isConnected()) {
            EversenseLogger.error(TAG, "Cannot sync — not connected")
            return
        }
        EversenseLogger.info(TAG, "Triggering full sync on user request")
        EversenseE3Communicator.fullSync(gattCallback, preferences, watchers.toList(), force)
        EversenseE3Communicator.readGlucose(gattCallback, preferences, watchers.toList())
        // Update placement signal after sync
        gattCallback.readRssi()
    }

    // Called by EversenseGattCallback when RSSI is read
    fun onRssiRead(rssi: Int) {
        val preferences = preferences ?: return
        val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
        val state = JSON.decodeFromString<EversenseState>(stateJson)
        state.placementSignalRssi = rssi
        state.sensorSignalStrength = rssiToStrength(rssi)
        preferences.edit()?.putString(StorageKeys.STATE, JSON.encodeToString(state))?.apply()
        EversenseLogger.debug(TAG, "RSSI updated: $rssi dBm")
        watchers.forEach { it.onStateChanged(state) }
    }


    // Read transmitter-to-sensor signal strength.
    // Tries the Eversense 365 ReadSignalStrength packet first.
    // Falls back to BLE RSSI for E3 transmitters which don't support the packet.
    fun readSignalStrength() {
        val gattCallback = this.gattCallback ?: run { EversenseLogger.error(TAG, "Cannot read signal strength — no gattCallback"); return }
        val preferences = this.preferences ?: run { EversenseLogger.error(TAG, "Cannot read signal strength — no preferences"); return }
        if (!gattCallback.isConnected()) { EversenseLogger.warning(TAG, "Cannot read signal strength — not connected"); return }
        try {
            val signalStrength = if (gattCallback.is365()) {
                val response = gattCallback.writePacket<com.nightscout.eversense.packets.e365.GetSignalStrengthPacket.Response>(com.nightscout.eversense.packets.e365.GetSignalStrengthPacket())
                response.signalStrength
            } else {
                val response = gattCallback.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket())
                EversenseLogger.info(TAG, "E3 signal raw: $($response.rawValue) -> $($response.signalStrength)%")
                response.signalStrength
            }
            val stateJson = preferences.getString(com.nightscout.eversense.util.StorageKeys.STATE, null) ?: "{}"
            val state = JSON.decodeFromString<EversenseState>(stateJson)
            state.sensorSignalStrength = signalStrength
            preferences.edit()?.putString(com.nightscout.eversense.util.StorageKeys.STATE, JSON.encodeToString(state))?.apply()
            EversenseLogger.info(TAG, "Signal strength: $signalStrength%")
            watchers.forEach { it.onStateChanged(state) }
        } catch (e: Exception) {
            EversenseLogger.warning(TAG, "readSignalStrength failed: $e")
            gattCallback.readRssi()
        }
    }

        private fun rssiToStrength(rssi: Int): Int = when {
        rssi == 0   -> 0
        rssi >= -65 -> 100
        rssi >= -75 -> 80
        rssi >= -85 -> 60
        rssi >= -95 -> 40
        else        -> 20
    }

        fun readRssi() {
        gattCallback?.readRssi()
    }

    companion object {
        private const val TAG = "EversenseCGMManager"

        // ignoreUnknownKeys: tolerates firmware version differences between E3 and 365 transmitters.
        private val JSON = Json { ignoreUnknownKeys = true }

        val instance: EversenseCGMPlugin by lazy {
            EversenseCGMPlugin()
        }
    }
}
