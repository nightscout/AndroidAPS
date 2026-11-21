package app.aaps.plugins.eversense

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanSettings
import android.content.Context
import app.aaps.plugins.eversense.storage.EversenseStore
import android.os.SystemClock
import app.aaps.plugins.eversense.storage.edit
import app.aaps.plugins.eversense.callbacks.EversenseScanCallback
import app.aaps.plugins.eversense.models.EversenseState
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
import app.aaps.plugins.eversense.pairing.EversenseModel
import app.aaps.plugins.eversense.util.EversenseCrypto365Util
import app.aaps.plugins.eversense.util.EversenseScanner
import app.aaps.plugins.eversense.util.StorageKeys
import kotlinx.serialization.json.Json
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import app.aaps.plugins.eversense.connection.EversenseConnectionController
import app.aaps.plugins.eversense.connection.HandlerConnectionScheduler
import app.aaps.plugins.eversense.connection.SessionStore
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseSecureState
import app.aaps.plugins.eversense.enums.EversenseType

/**
 * Core Eversense CGM manager — constructor-injected, no manual singleton.
 *
 * Provided by the AAPS source graph. Context, BluetoothManager, and EversenseStore
 * are non-nullable vals initialized at construction time, eliminating ~40 null checks.
 */
class EversenseCGMPlugin(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    val preferences: EversenseStore,
    val aapsLogger: AAPSLogger
) {

    @Volatile private var activeCallback: EversenseGattCallback? = null
    private val scheduler = HandlerConnectionScheduler()
    private val connection = EversenseConnectionController(scheduler, SystemClock::elapsedRealtime, object : EversenseConnectionController.Transport {
        override fun open(session: Long): Boolean = openConnection(session)
        override fun reuse(session: Long): Boolean = activeCallback?.takeIf { it.session == session }?.reuse() == true
        override fun close() {
            val old = activeCallback
            activeCallback = null
            old?.closeSession()
        }
    })
    private val watchers = CopyOnWriteArrayList<EversenseWatcher>()
    private var scanner: EversenseScanner? = null
    private var scanDeadline: EversenseConnectionController.Task? = null
    private var scanToken = 0L
    private val scanLock = Any()

    private var shortcutFailures = 0
    private var fullAuthFailures = 0
    private var placementFailures = 0

    internal fun deliver(session: Long, action: () -> Unit) = connection.deliver(session, action)
    internal fun authenticationSucceeded(session: Long) = connection.deliver(session) {
        shortcutFailures = 0
        fullAuthFailures = 0
    }
    internal fun authenticationFailed(session: Long, shortcut: Boolean) = connection.whileOwned(session) {
        if (shortcut) {
            if (!hasInternet()) return@whileOwned
            if (++shortcutFailures >= 3) {
                shortcutFailures = 0
                EversenseCrypto365Util(preferences, aapsLogger).disallowUseShortcut()
            }
        } else if (++fullAuthFailures >= 3) {
            fullAuthFailures = 0
            EversenseCrypto365Util(preferences, aapsLogger).allowUseShortcut()
        }
    }
    internal fun placementChanged(session: Long, status: Int, is365: Boolean) = connection.whileOwned(session) {
        if (status == 19 && !is365) {
            if (++placementFailures >= 3) watchers.forEach { it.onTransmitterNotPlaced() }
        } else placementFailures = 0
    }

    /**
     * Reads the stored login and connects when a transmitter is paired too. Unpaired, it waits: [pair] connects then. It
     * does not try every minute for a transmitter it does not have.
     */
    @Synchronized
    fun start() {
        val state = secureState()
        username = state.username
        password = state.password
        connection.start(mayLink(state) && preferences.getString(StorageKeys.REMOTE_DEVICE_KEY, null) != null)
    }

    private fun secureState(): EversenseSecureState =
        JSON.decodeFromString<EversenseSecureState>(preferences.getString(StorageKeys.SECURE_STATE, null) ?: "{}")

    /**
     * Whether the link may be opened: with a login, or for an E3 used without one. A 365 registers this phone with the
     * Eversense server, and that needs the login.
     */
    private fun mayLink(state: EversenseSecureState = secureState()): Boolean =
        (username.isNotEmpty() && password.isNotEmpty()) || (state.username.isEmpty() && state.model == EversenseModel.E3.name)

    /** Uses an E3 without a login: a stored login is forgotten (with its cloud queue), and the link opens without one. */
    @Synchronized
    fun useWithoutLogin() {
        clearCredentials()
        rememberModel(EversenseModel.E3.name)
        if (preferences.getString(StorageKeys.REMOTE_DEVICE_KEY, null) != null) connection.connect()
    }

    /**
     * Pairs the transmitter at [address]: stores it with its [name], makes the next connection a full registration (a
     * 365 registers this phone with the new transmitter), and connects.
     *
     * @return false when the link cannot be opened (not started, Bluetooth off, no login)
     */
    @Synchronized
    fun pair(address: String, name: String): Boolean {
        preferences.edit(commit = true) {
            putString(StorageKeys.REMOTE_DEVICE_KEY, address)
            putString(StorageKeys.REMOTE_DEVICE_NAME, name)
        }
        EversenseCrypto365Util(preferences, aapsLogger).disallowUseShortcut()
        placementFailures = 0
        return connection.connect(force = true)
    }

    /** Keeps the model chosen at sign-in with the login, to show it; the connection does not use it. */
    @Synchronized
    fun rememberModel(model: String) {
        val state = secureState()
        if (state.model == model) return
        state.model = model
        preferences.edit(commit = true) { putString(StorageKeys.SECURE_STATE, JSON.encodeToString(state)) }
    }

    /**
     * Closes the link and drops what this object holds in memory: the scan, the retries and timers (the connection's
     * stop), the failure counters and the login. The transmitter, its keys and the stored login stay in [preferences];
     * the next start reads them again.
     */
    @Synchronized
    fun stop() {
        isPositioningMode = false
        lastPositioningModeExitAt = 0L
        stopScan()
        connection.stop()
        shortcutFailures = 0
        fullAuthFailures = 0
        placementFailures = 0
        username = ""
        password = ""
    }
    fun onBluetoothAvailable() = connection.bluetoothAvailable()
    internal fun sessionActive(session: Long) = connection.active(session)
    internal fun sessionReady(session: Long) = connection.deliver(session) {
        placementFailures = 0
        connection.ready(session)
    }
    internal fun sessionDisconnected(session: Long, status: Int, reuse: Boolean = false) = connection.disconnected(session, status, reuse)

    internal fun watchersFor(session: Long): List<EversenseWatcher> = listOf(object : EversenseWatcher {
        override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) { connection.dataReceived(session) }
        override fun onConnectionChanged(connected: Boolean) {}
        override fun onStateChanged(state: EversenseState) {}
        override fun onTransmitterReady() {}
        override fun onTransmitterNotPlaced() {}
        override fun onAlarmReceived(alarm: ActiveAlarm) {}
    }) + watchers.map { watcher -> object : EversenseWatcher {
        override fun onConnectionChanged(connected: Boolean) { if (connection.owns(session)) watcher.onConnectionChanged(connected) }
        override fun onStateChanged(state: EversenseState) { if (connection.active(session)) watcher.onStateChanged(state) }
        override fun onTransmitterReady() { if (connection.active(session)) watcher.onTransmitterReady() }
        override fun onTransmitterNotPlaced() { if (connection.active(session)) watcher.onTransmitterNotPlaced() }
        override fun onAlarmReceived(alarm: ActiveAlarm) { if (connection.active(session)) watcher.onAlarmReceived(alarm) }
        override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) { if (connection.active(session)) watcher.onCGMRead(type, readings) }
    } }

    // Flag to track positioning mode state for safe diagnostic mode toggling
    var isPositioningMode: Boolean = false
        private set

    // Credentials set by AAPS layer before any login attempt
    internal var username: String = ""
        private set
    internal var password: String = ""
        private set

    fun addWatcher(watcher: EversenseWatcher) {
        watchers.addIfAbsent(watcher)
    }

    fun removeWatcher(watcher: EversenseWatcher) {
        watchers.remove(watcher)
    }

    fun cloudStore(): EversenseStore? = connection.cloudStore(preferences)

    fun hasInternet(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isConnected(): Boolean = activeCallback?.isConnected() == true
    fun is365(): Boolean = activeCallback?.is365() == true

    fun getCurrentState(): EversenseState {
        val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
        return JSON.decodeFromString<EversenseState>(stateJson)
    }

    @SuppressLint("MissingPermission")
    fun startScan(callback: EversenseScanCallback): AutoCloseable = synchronized(scanLock) {
        stopScan()
        val token = ++scanToken
        val bluetoothScanner = bluetoothManager.adapter?.bluetoothLeScanner
            ?: return@synchronized AutoCloseable { }
        val newScanner = EversenseScanner(callback, aapsLogger)
        scanner = newScanner
        try {
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            bluetoothScanner.startScan(null, settings, newScanner)
            scanDeadline = scheduler.after(10_000L) { synchronized(scanLock) { if (scanToken == token) stopScan() } }
        } catch (e: SecurityException) {
            scanner = null
            aapsLogger.warn(LTag.BGSOURCE, "Cannot scan without Bluetooth permission: $e")
        }
        AutoCloseable { synchronized(scanLock) { if (scanToken == token) stopScan() } }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() = synchronized(scanLock) {
        scanToken++
        scanDeadline?.cancel(); scanDeadline = null
        val old = scanner
        scanner = null
        if (old != null) try { bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(old) }
        catch (e: SecurityException) { aapsLogger.warn(LTag.BGSOURCE, "Scan permission was revoked: $e") }
    }

    /** Connects to the paired transmitter now, unless a connection is already up or on its way. A new one is [pair]. */
    fun connect(): Boolean = connection.connect()

    @SuppressLint("MissingPermission")
    private fun openConnection(session: Long): Boolean {
        stopScan()
        if (!mayLink() || bluetoothManager.adapter?.isEnabled != true) return false
        val address = preferences.getString(StorageKeys.REMOTE_DEVICE_KEY, null) ?: return false
        val device = try { bluetoothManager.adapter.getRemoteDevice(address) } catch (_: IllegalArgumentException) { return false }
        val callback = EversenseGattCallback(this, SessionStore(connection, session, preferences), session)
        activeCallback = callback
        callback.attachGatt(device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE))
        return callback.hasGatt()
    }

    /** Invalidates old work before publishing credentials or region and reconnects once. */
    @Synchronized
    fun setCredentials(username: String, password: String, europeanRegion: Boolean) {
        val state = JSON.decodeFromString<EversenseSecureState>(preferences.getString(StorageKeys.SECURE_STATE, null) ?: "{}")
        val changed = state.username != username || state.password != password || state.isEuropeanRegion != europeanRegion
        val reconnect = changed || this.username != username || this.password != password
        if (!reconnect) return
        connection.disconnect()
        this.username = username
        this.password = password
        shortcutFailures = 0
        fullAuthFailures = 0
        if (changed) {
            state.username = username
            state.password = password
            state.isEuropeanRegion = europeanRegion
            state.canUseShortcut = false
            preferences.edit(commit = true) {
                putString(StorageKeys.SECURE_STATE, JSON.encodeToString(state))
                remove(StorageKeys.ACCESS_TOKEN)
                remove(StorageKeys.ACCESS_TOKEN_EXPIRY)
                remove(StorageKeys.CLOUD_QUEUE)
                // A new login has not been refused yet
                remove(StorageKeys.LOGIN_REFUSED)
            }
        }
        if (username.isNotEmpty() && password.isNotEmpty() && preferences.getString(StorageKeys.REMOTE_DEVICE_KEY, null) != null) connection.connect()
    }

    @Synchronized
    fun clearCredentials() {
        connection.disconnect()
        username = ""
        password = ""
        val state = JSON.decodeFromString<EversenseSecureState>(preferences.getString(StorageKeys.SECURE_STATE, null) ?: "{}")
        state.username = ""
        state.password = ""
        state.model = ""
        state.canUseShortcut = false
        preferences.edit(commit = true) {
            putString(StorageKeys.SECURE_STATE, JSON.encodeToString(state))
            remove(StorageKeys.ACCESS_TOKEN)
            remove(StorageKeys.ACCESS_TOKEN_EXPIRY)
            remove(StorageKeys.CLOUD_QUEUE)
            remove(StorageKeys.LOGIN_REFUSED)
        }
    }

    /** Unpairs: closes the link and forgets the transmitter, so the next pairing is a full registration. The login stays. */
    @Synchronized
    fun clearStoredDevice() {
        disconnect()
        placementFailures = 0
        preferences.edit(commit = true) {
            remove(StorageKeys.REMOTE_DEVICE_KEY)
            remove(StorageKeys.REMOTE_DEVICE_NAME)
        }
        EversenseCrypto365Util(preferences, aapsLogger).disallowUseShortcut()
        aapsLogger.info(LTag.BGSOURCE, "Cleared stored device address")
    }

    fun disconnect() {
        stopScan()
        connection.disconnect()
    }

    fun setDiagnosticMode(isEnabled: Boolean) {
        val callback = activeCallback?.takeIf { it.isConnected() }
        if (callback == null) {
            aapsLogger.warn(LTag.BGSOURCE, "Cannot set diagnostic mode — not connected")
            return
        }
        try {
            // Routed through bleExecutor (like every other write path) so this can't race a
            // concurrently in-flight write from the Keep Alive sync cycle.
            val future = callback.submitToExecutor(readyOnly = true) { doSetDiagnosticMode(callback, isEnabled) }
            await(future, 20_000)
        } catch (e: Exception) {
            aapsLogger.warn(LTag.BGSOURCE, "setDiagnosticMode failed: $e")
        }
    }

    // The actual diagnostic-mode write. Callers MUST already be running on bleExecutor's single
    // worker thread - either via setDiagnosticMode() above (the normal entry point, which submits
    // this to bleExecutor and blocks), or from another task already running there, like
    // submitToExecutorAndSync()'s reconnect handler below. Calling this indirectly through
    // setDiagnosticMode() from inside an already-running bleExecutor task deadlocks: the submit
    // queues a second task behind the one currently blocked waiting for it, on an executor that
    // only has one worker thread - future.get() always times out after 20s, silently, since
    // setDiagnosticMode() swallows the resulting TimeoutException as a warning.
    private fun doSetDiagnosticMode(callback: EversenseGattCallback, isEnabled: Boolean) {
        if (callback.is365()) {
            if (isEnabled) {
                callback.writePacket<EnterDiagnosticMode365Packet.Response>(EnterDiagnosticMode365Packet())
            } else {
                callback.writePacket<ExitDiagnosticMode365Packet.Response>(ExitDiagnosticMode365Packet())
            }
            aapsLogger.info(LTag.BGSOURCE, "Diagnostic mode set to $isEnabled (365)")
        } else {
            if (isEnabled) {
                callback.writePacket<EnterDiagnosticModePacket.Response>(EnterDiagnosticModePacket())
            } else {
                callback.writePacket<ExitDiagnosticModePacket.Response>(ExitDiagnosticModePacket())
            }
            aapsLogger.info(LTag.BGSOURCE, "Diagnostic mode set to $isEnabled (E3)")
        }
    }

    fun enterPositioningMode() {
        isPositioningMode = true
        setDiagnosticMode(true)
        aapsLogger.info(LTag.BGSOURCE, "Diagnostic Mode ENABLED: Positioning active")
    }

    // Set whenever we intentionally exit diagnostic/positioning mode. The transmitter reliably
    // pushes an alarm code with no EversenseAlarm mapping (-> UNKNOWN) ~1-2s after this transition
    // (confirmed from device logs: signal/battery/calibration all healthy at the time - a
    // mode-transition status push, not a real fault). EversensePlugin.onAlarmReceived() uses this
    // timestamp to suppress just that transient push without masking a genuinely-unrecognized
    // alarm arriving at any other time.
    @Volatile var lastPositioningModeExitAt: Long = 0L
        private set

    fun exitPositioningMode() {
        isPositioningMode = false
        lastPositioningModeExitAt = System.currentTimeMillis()
        setDiagnosticMode(false)
        aapsLogger.info(LTag.BGSOURCE, "Diagnostic Mode DISABLED: Power saving active")
    }

    fun sendCalibration(glucoseMgDl: Int, timestampMs: Long = System.currentTimeMillis()): Boolean {
        val callback = activeCallback?.takeIf { it.isConnected() }
        if (callback == null) {
            aapsLogger.error(LTag.BGSOURCE, "Transmitter is not connected")
            return false
        }
        val store = callback.operationStore()
        return try {
            val future = callback.submitToExecutor(readyOnly = true) {
                store.contains(StorageKeys.STATE) // Reject an operation captured before a link change.
                if (callback.is365()) {
                    val packet = SetBloodGlucosePointPacket365(glucoseMgDl, timestampMs)
                    callback.writePacket<SetBloodGlucosePointPacket365.Response>(packet)
                    aapsLogger.info(LTag.BGSOURCE, "365 calibration sent: $glucoseMgDl mg/dL")
                } else {
                    EversenseE3Communicator.sendCalibration(callback, glucoseMgDl)
                }
            }
            await(future, 20_000)

            val stateJson = store.getString(StorageKeys.STATE, null) ?: "{}"
            val updatedState = JSON.decodeFromString<EversenseState>(stateJson)
            updatedState.lastCalibrationDate = timestampMs
            updatedState.nextCalibrationDate = timestampMs + 24 * 60 * 60 * 1000L
            updatedState.calibrationReadiness = CalibrationReadiness.WAITING_POST_CALIBRATION
            store.edit(commit = true) {
                putString(StorageKeys.STATE, JSON.encodeToString(updatedState))
            }
            aapsLogger.debug(LTag.BGSOURCE) { "Updated calibration state: lastCalibrationDate=$timestampMs, readiness=WAITING_POST_CALIBRATION" }

            if (!callback.is365()) {
                callback.submitToExecutor(readyOnly = true) {
                    try {
                        val readinessResponse = callback.writePacket<GetCalibrationReadinessPacket.Response>(GetCalibrationReadinessPacket())
                        val currentStateJson = store.getString(StorageKeys.STATE, null) ?: "{}"
                        val currentState = JSON.decodeFromString<EversenseState>(currentStateJson)
                        currentState.calibrationReadiness = readinessResponse.readiness
                        store.edit(commit = true) {
                            putString(StorageKeys.STATE, JSON.encodeToString(currentState))
                        }
                        aapsLogger.debug(LTag.BGSOURCE) { "Post-calibration readiness re-read: ${readinessResponse.readiness}" }
                        watchersFor(callback.session).forEach { it.onStateChanged(currentState) }
                    } catch (e: Exception) {
                        aapsLogger.warn(LTag.BGSOURCE, "Post-calibration readiness re-read failed (non-fatal): $e")
                    }
                }
            }

            true
        } catch (e: Exception) {
            aapsLogger.error(LTag.BGSOURCE, "Failed to send calibration: $e")
            false
        }
    }

    fun submitToExecutorAndSync(force: Boolean = false) {
        val callback = activeCallback?.takeIf { it.isConnected() }
        if (callback == null) {
            aapsLogger.error(LTag.BGSOURCE, "Cannot sync — not connected")
            return
        }
        if (callback.is365()) {
            aapsLogger.debug(LTag.BGSOURCE) { "365 transmitter — skipping submitToExecutorAndSync (authV2flow handles it)" }
            return
        }
        callback.submitToExecutor(readyOnly = true) {
            if (isPositioningMode) {
                // Already running on bleExecutor here - call the write directly, not through
                // setDiagnosticMode(), which would submit-and-block on this same single-threaded
                // executor and deadlock (see doSetDiagnosticMode's doc comment).
                try {
                    doSetDiagnosticMode(callback, true)
                    aapsLogger.info(LTag.BGSOURCE, "Re-enabled Diagnostic Mode after reconnect")
                } catch (e: Exception) {
                    aapsLogger.warn(LTag.BGSOURCE, "Failed to re-enable diagnostic mode after reconnect: $e")
                }
            }
            aapsLogger.debug(LTag.BGSOURCE) { "Running E3 fullSync on bleExecutor after connect" }
            EversenseE3Communicator.fullSync(callback, callback.stateStore, watchersFor(callback.session), force)
        }
    }

    fun triggerFullSync(force: Boolean = false) {
        val callback = activeCallback?.takeIf { it.isConnected() }
        if (callback == null) {
            aapsLogger.error(LTag.BGSOURCE, "Cannot sync — not connected")
            return
        }
        callback.submitToExecutor(readyOnly = true) {
            aapsLogger.info(LTag.BGSOURCE, "Triggering full sync on user request")
            if (callback.is365()) {
                Eversense365Communicator.fullSync(callback, callback.stateStore, watchersFor(callback.session), force)
                Eversense365Communicator.readGlucose(callback, callback.stateStore, watchersFor(callback.session))
            } else {
                EversenseE3Communicator.fullSync(callback, callback.stateStore, watchersFor(callback.session), force)
                EversenseE3Communicator.readGlucose(callback, callback.stateStore, watchersFor(callback.session))
            }
            callback.readRssi()
        }
    }

    internal fun onRssiRead(rssi: Int, session: Long) {
        val callback = activeCallback?.takeIf { it.session == session && connection.active(session) } ?: return
        val stateJson = callback.stateStore.getString(StorageKeys.STATE, null) ?: "{}"
        val state = JSON.decodeFromString<EversenseState>(stateJson)
        state.placementSignalRssi = rssi
        callback.stateStore.edit { putString(StorageKeys.STATE, JSON.encodeToString(state)) }
        aapsLogger.debug(LTag.BGSOURCE) { "RSSI updated: $rssi dBm" }
        watchersFor(session).forEach { it.onStateChanged(state) }
    }

    fun readSignalStrength() {
        val callback = activeCallback?.takeIf { it.isConnected() }
        if (callback == null) { aapsLogger.warn(LTag.BGSOURCE, "Cannot read signal strength — not connected"); return }
        val store = callback.operationStore()
        try {
            // Routed through bleExecutor (like every other write path) so this can't race a
            // concurrently in-flight write from the Keep Alive sync cycle.
            val future = callback.submitToExecutor(readyOnly = true) {
                store.contains(StorageKeys.STATE) // Reject an operation captured before a link change.
                if (callback.is365()) {
                    val response = callback.writePacket<GetSignalStrengthPacket.Response>(GetSignalStrengthPacket())
                    response.signalStrength
                } else {
                    val response = callback.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket())
                    aapsLogger.debug(LTag.BGSOURCE) { "E3 signal raw: ${response.rawValue} -> ${response.signalStrength}%" }
                    response.signalStrength
                }
            }
            val signalStrength = await(future, 20_000)
            val stateJson = store.getString(StorageKeys.STATE, null) ?: "{}"
            val state = JSON.decodeFromString<EversenseState>(stateJson)
            state.sensorSignalStrength = signalStrength
            store.edit { putString(StorageKeys.STATE, JSON.encodeToString(state)) }
            aapsLogger.debug(LTag.BGSOURCE) { "Signal strength: $signalStrength%" }
            watchersFor(callback.session).forEach { it.onStateChanged(state) }
        } catch (e: Exception) {
            aapsLogger.warn(LTag.BGSOURCE, "readSignalStrength failed: $e")
            callback.readRssi()
        }
    }

    private fun <T> await(future: Future<T>, timeoutMs: Long): T = try {
        future.get(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (e: Exception) {
        future.cancel(true)
        if (e is InterruptedException) Thread.currentThread().interrupt()
        throw e
    }

    fun readRssi() {
        activeCallback?.readRssi()
    }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
