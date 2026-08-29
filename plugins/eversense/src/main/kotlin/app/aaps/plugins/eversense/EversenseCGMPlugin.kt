package app.aaps.plugins.eversense

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.aaps.plugins.eversense.callbacks.EversenseScanCallback
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.models.EversenseTransmitterSettings
import app.aaps.plugins.eversense.packets.Eversense365Communicator
import app.aaps.plugins.eversense.packets.EversenseE3Communicator
import app.aaps.plugins.eversense.packets.e3.EnterDiagnosticModePacket
import app.aaps.plugins.eversense.packets.e3.ExitDiagnosticModePacket
import app.aaps.plugins.eversense.packets.e365.EnterDiagnosticMode365Packet
import app.aaps.plugins.eversense.packets.e365.ExitDiagnosticMode365Packet
import app.aaps.plugins.eversense.packets.e3.GetCalibrationReadinessPacket
import app.aaps.plugins.eversense.packets.e3.GetSignalStrengthRawPacket
import app.aaps.plugins.eversense.packets.e365.GetSignalStrengthPacket
import app.aaps.plugins.eversense.packets.e365.SetBloodGlucosePointPacket365
import app.aaps.plugins.eversense.enums.CalibrationReadiness
import app.aaps.plugins.eversense.util.EversenseLogger
import app.aaps.plugins.eversense.util.EversenseScanner
import app.aaps.plugins.eversense.util.StorageKeys
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Core Eversense CGM manager — constructor-injected, no manual singleton.
 *
 * Provided as @Singleton via SourceModule. Context, BluetoothManager, and SharedPreferences
 * are non-nullable vals initialized at construction time, eliminating ~40 null checks.
 */
class EversenseCGMPlugin(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    val preferences: SharedPreferences
) {
    init {
        // Must run before anything below (or in EversenseGattCallback's construction) does any
        // EversenseLogger.* call - EversenseLogger configures Logback on first use, and
        // configure() has no effect after that point. Points Eversense's own log file at the
        // app's real, permission-free scoped external-files directory (same one AndroidAPS.log
        // uses) instead of EversenseLogger's raw-/sdcard fallback, which scoped storage silently
        // blocks writes to on modern Android without MANAGE_EXTERNAL_STORAGE - see
        // EversenseLogger.configure() for the full story.
        val extFilesDir = context.getExternalFilesDir(null)
        val eversenseLogDir = if (extFilesDir != null) File(extFilesDir, "eversense").absolutePath else "/sdcard/AndroidAPS/eversense"
        EversenseLogger.configure(eversenseLogDir)
        EversenseLogger.enableLogging(true)
    }

    private val gattCallback = EversenseGattCallback(this, preferences)
    private val connectionLock = Any()
    private var scanner: EversenseScanner? = null

    // Thread-safe: watchers are added/removed from main thread but iterated from bleExecutor
    val watchers: MutableList<EversenseWatcher> = CopyOnWriteArrayList()

    // Flag to track positioning mode state for safe diagnostic mode toggling
    var isPositioningMode: Boolean = false

    // Credentials set by AAPS layer before any login attempt
    var username: String = ""
    var password: String = ""

    fun addWatcher(watcher: EversenseWatcher) {
        if (!watchers.contains(watcher)) watchers.add(watcher)
    }

    fun removeWatcher(watcher: EversenseWatcher) {
        watchers.remove(watcher)
    }

    fun isConnected(): Boolean = gattCallback.isConnected()
    fun is365(): Boolean = gattCallback.is365()

    fun getCurrentState(): EversenseState {
        val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
        return JSON.decodeFromString<EversenseState>(stateJson)
    }

    @SuppressLint("MissingPermission")
    fun startScan(callback: EversenseScanCallback) {
        val bluetoothScanner = bluetoothManager.adapter?.bluetoothLeScanner ?: run {
            EversenseLogger.error(TAG, "BLE scanner not available")
            return
        }
        scanner = EversenseScanner(callback)
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        bluetoothScanner.startScan(null, settings, scanner)
        EversenseLogger.info(TAG, "BLE scan started")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val bluetoothScanner = bluetoothManager.adapter?.bluetoothLeScanner ?: run {
            EversenseLogger.error(TAG, "No bluetooth scanner available when trying to stop scan")
            return
        }
        scanner?.let {
            bluetoothScanner.stopScan(it)
            scanner = null
            EversenseLogger.info(TAG, "Scan stopped")
        } ?: EversenseLogger.info(TAG, "stopScan called but no active scan found")
    }

    // Android doesn't always deliver onConnectionStateChange when the Bluetooth radio itself
    // is powered off (observed in the wild: no disconnect callback at all across a BT toggle),
    // so gattCallback's internal "connected" flag can be left stale true. A live BLE connection
    // cannot survive the whole adapter going off, so plain connect() would wrongly short-circuit
    // on isConnected() and do nothing. Call this instead of connect() when reacting to
    // BluetoothAdapter.ACTION_STATE_CHANGED -> STATE_ON, since that's exactly the situation where
    // "connected" may be lying.
    @SuppressLint("MissingPermission")
    fun forceReconnect(): Boolean {
        gattCallback.cleanUp()
        return connect(null)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice? = null): Boolean {
        stopScan()

        synchronized(connectionLock) {
            if (gattCallback.isConnected()) {
                EversenseLogger.info(TAG, "Already connected, skipping reconnect")
                return true
            }

            if (bluetoothManager.adapter?.isEnabled != true) {
                // connectGatt() made while the Bluetooth radio is off (or still mid-toggle)
                // never fires any callback - not even a failure one - so it must not be
                // attempted here. Retrying blindly on a timer against a dead radio just wastes
                // GATT client slots. EversensePlugin's BluetoothAdapter.ACTION_STATE_CHANGED
                // receiver calls connect() again the moment the adapter is confirmed back on.
                EversenseLogger.warning(TAG, "Bluetooth is disabled — skipping connect attempt, will retry once it's back on")
                return false
            }

            gattCallback.cleanUp()

            return if (device != null) {
                EversenseLogger.info(TAG, "Connecting to supplied device: ${device.name}")
                preferences.edit { putString(StorageKeys.REMOTE_DEVICE_KEY, device.address) }
                EversenseLogger.info(TAG, "Saved device address for auto-reconnect: ${device.address}")
                device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
                true
            } else {
                val address = preferences.getString(StorageKeys.REMOTE_DEVICE_KEY, null) ?: run {
                    EversenseLogger.error(TAG, "No device supplied and no stored device address found.")
                    return false
                }
                val remoteDevice = bluetoothManager.adapter.getRemoteDevice(address) ?: run {
                    EversenseLogger.error(TAG, "Could not retrieve remote device for address $address")
                    return false
                }
                EversenseLogger.info(TAG, "Reconnecting to stored device: $address")
                remoteDevice.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
                true
            }
        }
    }

    fun clearStoredDevice() {
        preferences.edit { remove(StorageKeys.REMOTE_DEVICE_KEY) }
        EversenseLogger.info(TAG, "Cleared stored device address")
    }

    fun disconnect() {
        if (!gattCallback.isConnected()) {
            EversenseLogger.info(TAG, "disconnect() called but not currently connected")
            return
        }
        gattCallback.disconnect()
        EversenseLogger.info(TAG, "Disconnected from transmitter")
    }

    fun setDiagnosticMode(isEnabled: Boolean) {
        if (!gattCallback.isConnected()) {
            EversenseLogger.warning(TAG, "Cannot set diagnostic mode — not connected")
            return
        }
        try {
            // Routed through bleExecutor (like every other write path) so this can't race a
            // concurrently in-flight write from the Keep Alive sync cycle.
            val future = gattCallback.submitToExecutor {
                if (gattCallback.is365()) {
                    if (isEnabled) {
                        gattCallback.writePacket<EnterDiagnosticMode365Packet.Response>(EnterDiagnosticMode365Packet())
                    } else {
                        gattCallback.writePacket<ExitDiagnosticMode365Packet.Response>(ExitDiagnosticMode365Packet())
                    }
                    EversenseLogger.info(TAG, "Diagnostic mode set to $isEnabled (365)")
                } else {
                    if (isEnabled) {
                        gattCallback.writePacket<EnterDiagnosticModePacket.Response>(EnterDiagnosticModePacket())
                    } else {
                        gattCallback.writePacket<ExitDiagnosticModePacket.Response>(ExitDiagnosticModePacket())
                    }
                    EversenseLogger.info(TAG, "Diagnostic mode set to $isEnabled (E3)")
                }
            }
            future.get(20000, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            EversenseLogger.warning(TAG, "setDiagnosticMode failed: $e")
        }
    }

    fun enterPositioningMode() {
        isPositioningMode = true
        setDiagnosticMode(true)
        EversenseLogger.info(TAG, "Diagnostic Mode ENABLED: Positioning active")
    }

    fun exitPositioningMode() {
        isPositioningMode = false
        setDiagnosticMode(false)
        EversenseLogger.info(TAG, "Diagnostic Mode DISABLED: Power saving active")
    }

    fun writeSettings(settings: EversenseTransmitterSettings): Boolean {
        if (!gattCallback.isConnected()) {
            EversenseLogger.error(TAG, "Transmitter is not connected")
            return false
        }
        return EversenseE3Communicator.writeSettings(gattCallback, preferences, settings)
    }

    fun sendCalibration(glucoseMgDl: Int, timestampMs: Long = System.currentTimeMillis()): Boolean {
        if (!gattCallback.isConnected()) {
            EversenseLogger.error(TAG, "Transmitter is not connected")
            return false
        }
        val state = getCurrentState()
        return try {
            val future = gattCallback.submitToExecutor {
                if (gattCallback.is365()) {
                    val packet = SetBloodGlucosePointPacket365(glucoseMgDl, timestampMs)
                    gattCallback.writePacket<SetBloodGlucosePointPacket365.Response>(packet)
                    EversenseLogger.info(TAG, "365 calibration sent: $glucoseMgDl mg/dL")
                } else {
                    EversenseE3Communicator.sendCalibration(gattCallback, glucoseMgDl)
                }
            }
            future.get(20000, java.util.concurrent.TimeUnit.MILLISECONDS)

            val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
            val updatedState = JSON.decodeFromString<EversenseState>(stateJson)
            updatedState.lastCalibrationDate = timestampMs
            updatedState.nextCalibrationDate = timestampMs + 24 * 60 * 60 * 1000L
            updatedState.calibrationReadiness = CalibrationReadiness.WAITING_POST_CALIBRATION
            preferences.edit(commit = true) {
                putString(StorageKeys.STATE, JSON.encodeToString(updatedState))
            }
            EversenseLogger.info(TAG, "Updated calibration state: lastCalibrationDate=$timestampMs, readiness=WAITING_POST_CALIBRATION")

            if (!gattCallback.is365()) {
                gattCallback.submitToExecutor {
                    try {
                        val readinessResponse = gattCallback.writePacket<GetCalibrationReadinessPacket.Response>(GetCalibrationReadinessPacket())
                        val currentStateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
                        val currentState = JSON.decodeFromString<EversenseState>(currentStateJson)
                        currentState.calibrationReadiness = readinessResponse.readiness
                        preferences.edit(commit = true) {
                            putString(StorageKeys.STATE, JSON.encodeToString(currentState))
                        }
                        EversenseLogger.info(TAG, "Post-calibration readiness re-read: ${readinessResponse.readiness}")
                        watchers.forEach { it.onStateChanged(currentState) }
                    } catch (e: Exception) {
                        EversenseLogger.warning(TAG, "Post-calibration readiness re-read failed (non-fatal): $e")
                    }
                }
            }

            true
        } catch (e: Exception) {
            EversenseLogger.error(TAG, "Failed to send calibration: $e")
            false
        }
    }

    fun submitToExecutorAndSync(force: Boolean = false) {
        if (!gattCallback.isConnected()) {
            EversenseLogger.error(TAG, "Cannot sync — not connected")
            return
        }
        if (gattCallback.is365()) {
            EversenseLogger.info(TAG, "365 transmitter — skipping submitToExecutorAndSync (authV2flow handles it)")
            return
        }
        gattCallback.submitToExecutor {
            if (isPositioningMode) {
                setDiagnosticMode(true)
                EversenseLogger.info(TAG, "Re-enabled Diagnostic Mode after reconnect")
            }
            EversenseLogger.info(TAG, "Running E3 fullSync on bleExecutor after connect")
            EversenseE3Communicator.fullSync(gattCallback, preferences, watchers.toList(), force)
        }
    }

    fun triggerFullSync(force: Boolean = false) {
        if (!gattCallback.isConnected()) {
            EversenseLogger.error(TAG, "Cannot sync — not connected")
            return
        }
        EversenseLogger.info(TAG, "Triggering full sync on user request")
        if (gattCallback.is365()) {
            Eversense365Communicator.fullSync(gattCallback, preferences, watchers.toList(), force)
            Eversense365Communicator.readGlucose(gattCallback, preferences, watchers.toList())
        } else {
            EversenseE3Communicator.fullSync(gattCallback, preferences, watchers.toList(), force)
            EversenseE3Communicator.readGlucose(gattCallback, preferences, watchers.toList())
        }
        gattCallback.readRssi()
    }

    fun onRssiRead(rssi: Int) {
        val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
        val state = JSON.decodeFromString<EversenseState>(stateJson)
        state.placementSignalRssi = rssi
        preferences.edit { putString(StorageKeys.STATE, JSON.encodeToString(state)) }
        EversenseLogger.debug(TAG, "RSSI updated: $rssi dBm")
        watchers.forEach { it.onStateChanged(state) }
    }

    fun readSignalStrength() {
        if (!gattCallback.isConnected()) { EversenseLogger.warning(TAG, "Cannot read signal strength — not connected"); return }
        try {
            // Routed through bleExecutor (like every other write path) so this can't race a
            // concurrently in-flight write from the Keep Alive sync cycle.
            val future = gattCallback.submitToExecutor {
                if (gattCallback.is365()) {
                    val response = gattCallback.writePacket<GetSignalStrengthPacket.Response>(GetSignalStrengthPacket())
                    response.signalStrength
                } else {
                    val response = gattCallback.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket())
                    EversenseLogger.info(TAG, "E3 signal raw: ${response.rawValue} -> ${response.signalStrength}%")
                    response.signalStrength
                }
            }
            val signalStrength = future.get(20000, java.util.concurrent.TimeUnit.MILLISECONDS)
            val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
            val state = JSON.decodeFromString<EversenseState>(stateJson)
            state.sensorSignalStrength = signalStrength
            preferences.edit { putString(StorageKeys.STATE, JSON.encodeToString(state)) }
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
        gattCallback.readRssi()
    }

    companion object {
        private const val TAG = "EversenseCGMManager"
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
