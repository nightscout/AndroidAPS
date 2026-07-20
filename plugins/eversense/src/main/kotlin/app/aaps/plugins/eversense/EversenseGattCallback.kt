package app.aaps.plugins.eversense

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.exceptions.EversenseWriteException
import app.aaps.plugins.eversense.packets.Eversense365Communicator
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversenseE3Communicator
import app.aaps.plugins.eversense.packets.e365.AuthIdentityPacket
import app.aaps.plugins.eversense.packets.e365.AuthStartPacket
import app.aaps.plugins.eversense.packets.e365.AuthWhoAmIPacket
import app.aaps.plugins.eversense.packets.e365.Eversense365Packets
import app.aaps.plugins.eversense.packets.e365.KeepAlivePacket
import app.aaps.plugins.eversense.packets.e3.EversenseE3Packets
import app.aaps.plugins.eversense.packets.e3.SaveBondingInformationPacket
import app.aaps.plugins.eversense.util.EversenseCrypto365Util
import app.aaps.plugins.eversense.util.EversenseHttp365Util
import app.aaps.plugins.eversense.util.EversenseLogger
import app.aaps.plugins.eversense.util.StorageKeys
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.Throws

class EversenseGattCallback(
    private val plugin: EversenseCGMPlugin,
    private val preferences: SharedPreferences
) : BluetoothGattCallback() {

    companion object {
        private const val TAG = "EversenseGattCallback"

        const val serviceUUID = "c3230001-9308-47ae-ac12-3d030892a211"

        private const val requestUUID = "6eb0f021-a7ba-7e7d-66c9-6d813f01d273"
        private const val requestSecureV2UUID = "c3230002-9308-47ae-ac12-3d030892a211"

        private const val responseUUID = "6eb0f024-bd60-7aaa-25a7-0029573f4f23"
        private const val responseSecureV2UUID = "c3230003-9308-47ae-ac12-3d030892a211"
        private const val magicDescriptorUUID = "00002902-0000-1000-8000-00805f9b34fb"

        private const val WRITE_TIMEOUT_MS = 5000L
        private const val CALIBRATION_TIMEOUT_MS = 15000L

        // Number of consecutive authV2flow failures before abandoning the shortcut path
        // and forcing a full re-auth (WhoAmI + fleet certificate). This handles the case
        // where the BLE stack resets (e.g. charger plug-in) and the session key is lost.
        // A value of 3 allows transient glitches to recover without internet, while still
        // falling back to full auth after sustained failures.
        private const val SHORTCUT_FAIL_THRESHOLD = 3
    }

    // FIX 1: Dedicated BLE executor for callbacks; separate network executor for HTTP calls
    // so that network operations in authV2flow() cannot block BLE processing.
    private var bleExecutor = Executors.newSingleThreadExecutor()
    private var networkExecutor = Executors.newSingleThreadExecutor()

    private val handler = Handler(Looper.getMainLooper())
    private var bluetoothGatt: BluetoothGatt? = null
    private var eversenseBluetoothService: BluetoothGattService? = null
    private var requestCharacteristic: BluetoothGattCharacteristic? = null
    private var responseCharacteristic: BluetoothGattCharacteristic? = null

    private var payloadSize: Int = 20
    private var security: EversenseSecurityType = EversenseSecurityType.None
    private var cryptoUtil = EversenseCrypto365Util(preferences)

    // Multi-notification response reassembly (SecureV2/365 only). Each notification is framed
    // with a chunk header: chunk 1 = [chunkIndex=1, totalChunks, 0x01] (3 bytes), chunk 2+ =
    // [chunkIndex, totalChunks] (2 bytes), followed by that chunk's slice of the [prefix+ciphertext]
    // blob. Most responses fit in one chunk, but bulk historical log reads can span several —
    // decrypting a lone chunk 1 of N>1 always fails the CCM MAC check since the auth tag covers
    // the full ciphertext, so chunks must be buffered until the full message has arrived.
    private var chunkAccumulator: ByteArray = ByteArray(0)
    private var chunkTotalExpected: Int = 1
    private var chunkNextIndex: Int = 1

    // FIX 2: Use AtomicReference for currentPacket to avoid the race condition where a stale
    // BLE notification could be processed against the wrong packet between assignment and write.
    var currentPacket: AtomicReference<EversenseBasePacket?> = AtomicReference(null)

    // FIX 3: Track connection state with a dedicated flag rather than relying on bluetoothGatt
    // being non-null, which is not a reliable indicator of actual connection state.
    @Volatile
    private var connected: Boolean = false
    private var transmitterReady: Boolean = false

    // Tracks consecutive status-19 failures to detect transmitter placement issues
    @Volatile
    private var failedConnectionAttempts: Int = 0
    private val PLACEMENT_WARNING_THRESHOLD = 3

    // Tracks consecutive general reconnect attempts (reset on successful connection).
    // Used to compute exponential backoff so AAPS retries quickly after boot (when the
    // official Eversense app temporarily holds the BLE connection) and backs off for
    // sustained failures to avoid draining the battery.
    @Volatile
    private var reconnectAttempts: Int = 0

    // Persistent reconnect: retries every 60s indefinitely while disconnected.
    // Android autoConnect gives up silently after ~30 min on Samsung devices.
    private val persistentReconnectRunnable = object : Runnable {
        override fun run() {
            if (!connected) {
                EversenseLogger.info(TAG, "Persistent reconnect tick — still disconnected, retrying...")
                plugin.connect(null)
                handler.postDelayed(this, 60_000L)
            }
        }
    }

    // FIX 12: Tracks consecutive authV2flow failures while using the shortcut path.
    // After SHORTCUT_FAIL_THRESHOLD failures, disallowUseShortcut() is called to force
    // a full re-auth on the next connection. This handles BLE stack resets (e.g. charger
    // plug-in) that invalidate the session key without needing internet on every reconnect.
    @Volatile
    private var shortcutFailCount: Int = 0

    fun isConnected(): Boolean = connected && transmitterReady
    fun isBleConnected(): Boolean = connected
    fun is365(): Boolean = security == EversenseSecurityType.SecureV2

    // Submit a task to the bleExecutor and return a Future so callers can block until complete.
    // This ensures calibration and other ad-hoc BLE operations are serialised with Keep Alive
    // cycles and do not race with currentPacket assignment.
    fun submitToExecutor(task: () -> Unit): java.util.concurrent.Future<*> =
        bleExecutor.submit(task)

    // FIX 4: Added disconnect() which calls both disconnect() and close() on the GATT client.
    // Calling only disconnect() without close() leaks the underlying GATT client resource.
    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        connected = false
        transmitterReady = false
        EversenseLogger.info(TAG, "GATT disconnected and closed")
    }

    // disconnect() alone does NOT reliably re-trigger onConnectionStateChange(): calling
    // bluetoothGatt.close() while its own async disconnect() is still in flight can suppress
    // that callback from ever arriving, and all reconnect scheduling (backoff, persistent
    // retry loop) lives inside that callback's disconnect branch. Left unrecovered, this
    // silently strands the connection until the user notices and manually reconnects -
    // observed in the wild via fullSync()'s own failure handler (see Eversense365Communicator).
    // Use this instead of plain disconnect() whenever OUR code (not the Android BLE stack) is
    // the one initiating the disconnect, so a reconnect is scheduled regardless of whether the
    // callback fires. Treated as a clean, self-initiated disconnect (GATT_SUCCESS) for a fast
    // 5s retry rather than backoff, since it isn't a real BLE-level failure.
    @SuppressLint("MissingPermission")
    fun disconnectAndScheduleReconnect() {
        disconnect()
        handler.post {
            plugin.watchers.forEach { it.onConnectionChanged(false) }
        }
        scheduleReconnect(BluetoothGatt.GATT_SUCCESS)
    }

    @SuppressLint("MissingPermission")
    fun cleanUp() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        connected = false
        transmitterReady = false
        bleExecutor.shutdownNow()
        bleExecutor = Executors.newSingleThreadExecutor()
        EversenseLogger.info(TAG, "GATT cleaned up before reconnect")
    }
    @SuppressLint("MissingPermission")
    fun readRssi() {
        bluetoothGatt?.readRemoteRssi() ?: EversenseLogger.warning(TAG, "Cannot read RSSI — not connected")
    }

    @SuppressLint("MissingPermission")
    override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            EversenseLogger.debug(TAG, "RSSI: $rssi dBm")
            plugin.onRssiRead(rssi)
        } else {
            EversenseLogger.warning(TAG, "Failed to read RSSI - status: $status")
        }
    }

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        EversenseLogger.info(TAG, "Connection state changed - status: $status, newState: $newState, device: ${gatt.device.name}")

        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            bluetoothGatt = gatt
            // FIX 3: Set connected flag on confirmed STATE_CONNECTED.
            connected = true
            // Reset backoff counters on successful connection
            reconnectAttempts = 0
            failedConnectionAttempts = 0
            handler.removeCallbacks(persistentReconnectRunnable)

            preferences.edit(commit = true) {
                putString(StorageKeys.REMOTE_DEVICE_KEY, gatt.device.address)
            }

            // FIX 5: Both connect and disconnect watcher notifications are now dispatched via
            // handler.post() so they always arrive on the main thread, preventing UI thread crashes.
            handler.post {
                plugin.watchers.forEach { it.onConnectionChanged(true) }
            }

            if (!gatt.requestMtu(512)) {
                EversenseLogger.warning(TAG, "requestMtu returned false — skipping to discoverServices with default payload size")
                payloadSize = 20
                gatt.discoverServices()
            }
            return
        }

        if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
            EversenseLogger.warning(TAG, "Disconnected or failed - status: $status, newState: $newState")

            // FIX 11: For E365 normal post-sync disconnect (status 19), reuse the existing
            // GATT object and call gatt.connect() directly — exactly as the official app does.
            // This preserves the BLE bond and session key so the shortcut auth path works
            // without needing internet. For all other disconnects, close and reconnect fresh.
            if (status == 19 && is365()) {
                connected = false
                transmitterReady = false
                handler.post {
                    plugin.watchers.forEach { it.onConnectionChanged(false) }
                }
                EversenseLogger.debug(TAG, "365 post-sync disconnect (status 19) — reusing GATT for reconnect")
                gatt.connect()
                return
            }

            gatt.close()
            bluetoothGatt = null
            connected = false
            transmitterReady = false

            handler.post {
                plugin.watchers.forEach { it.onConnectionChanged(false) }
            }

            if (status == 19) {
                // E3 only — E365 status 19 is handled above
                failedConnectionAttempts++
                EversenseLogger.warning(TAG, "Connection terminated by transmitter (status 19) — attempt $failedConnectionAttempts")
                if (failedConnectionAttempts >= PLACEMENT_WARNING_THRESHOLD) {
                    handler.post { plugin.watchers.forEach { it.onTransmitterNotPlaced() } }
                }
            } else {
                failedConnectionAttempts = 0
            }

            scheduleReconnect(status)
        }
    }

    // Extracted from onConnectionStateChange's disconnect branch so disconnectAndScheduleReconnect()
    // can trigger the identical reconnect scheduling without depending on onConnectionStateChange()
    // firing - see that method's doc comment for why that firing isn't reliable after our own close().
    private fun scheduleReconnect(status: Int) {
        val storedAddress = preferences.getString(StorageKeys.REMOTE_DEVICE_KEY, null)
        if (storedAddress == null) {
            EversenseLogger.warning(TAG, "No stored device address — skipping auto-reconnect")
            return
        }
        // Exponential backoff so AAPS reclaims the transmitter quickly after boot
        // (when the official Eversense app temporarily holds the BLE connection)
        // and avoids battery drain during sustained unavailability.
        //
        // Status 19 = transmitter actively rejected us (placement issue, not competition) —
        // use a fixed 30 s interval so we don't spam it.
        // Status GATT_SUCCESS = clean disconnect (we or the transmitter closed cleanly) —
        // reconnect quickly in 5 s.
        // All other status codes (e.g. 133 = GATT_ERROR, device busy) = backoff:
        //   attempt 0 → 5 s, attempt 1 → 10 s, attempt 2 → 20 s, attempt 3 → 40 s,
        //   attempt 4+ → 60 s cap.
        val delayMs: Long = when {
            status == 19 -> 30_000L
            status == BluetoothGatt.GATT_SUCCESS -> 5_000L
            else -> {
                val attempt = reconnectAttempts++
                minOf(5_000L * (1L shl minOf(attempt, 4)), 60_000L)
            }
        }
        EversenseLogger.info(TAG, "Scheduling auto-reconnect in ${delayMs / 1000}s (status: $status, attempt: $reconnectAttempts)")
        handler.postDelayed({
            EversenseLogger.info(TAG, "Attempting auto-reconnect (attempt $reconnectAttempts)...")
            plugin.connect(null)
        }, delayMs)
        // Also start persistent 60s retry loop in case autoConnect gives up
        handler.removeCallbacks(persistentReconnectRunnable)
        handler.postDelayed(persistentReconnectRunnable, 60_000L)
    }

    @SuppressLint("MissingPermission")
    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        if (status != 0) {
            EversenseLogger.warning(TAG, "MTU negotiation failed (status: $status) — using default payload size of 20")
            payloadSize = 20
        } else {
            payloadSize = mtu - 3
        }
        EversenseLogger.debug(TAG, "New payload size: $payloadSize")

        val success = gatt?.discoverServices()
        EversenseLogger.info(TAG, "Trigger discover services - success: $success")
    }

    @SuppressLint("MissingPermission")
    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        EversenseLogger.info(TAG, "Discovered services - status: $status")

        if (gatt == null) {
            EversenseLogger.error(TAG, "Gatt is null")
            return
        }

        // FIX 6: Use firstOrNull instead of first. The original code used .first {} which throws
        // NoSuchElementException if the service is missing. The null check below it was dead code
        // that could never be reached. firstOrNull correctly returns null on no match.
        val service = gatt.services.firstOrNull { it.uuid.toString() == serviceUUID }
        if (service == null) {
            EversenseLogger.error(TAG, "Required service not found -> disconnecting")
            gatt.disconnect()
            return
        }

        eversenseBluetoothService = service
        if (service.characteristics.isEmpty()) {
            EversenseLogger.error(TAG, "Service has no characteristics -> disconnecting")
            gatt.disconnect()
            return
        }

        var requestChar = service.characteristics.find { it.uuid.toString() == requestUUID }
        var responseChar = service.characteristics.find { it.uuid.toString() == responseUUID }
        if (requestChar != null && responseChar != null) {
            EversenseLogger.info(TAG, "Connected to Eversense E3!")
            security = EversenseSecurityType.None
            requestCharacteristic = requestChar
            responseCharacteristic = responseChar

            gatt.setCharacteristicNotification(requestChar, true)
            gatt.setCharacteristicNotification(responseChar, true)
            enableNotify(gatt, responseChar)
            return
        }

        requestChar = service.characteristics.find { it.uuid.toString() == requestSecureV2UUID }
        responseChar = service.characteristics.find { it.uuid.toString() == responseSecureV2UUID }
        if (requestChar == null || responseChar == null) {
            EversenseLogger.error(TAG, "No Eversense request/response characteristic found -> disconnecting")
            gatt.disconnect()
            return
        }

        EversenseLogger.info(TAG, "Connected to Eversense 365!")
        security = EversenseSecurityType.SecureV2
        requestCharacteristic = requestChar
        responseCharacteristic = responseChar

        gatt.setCharacteristicNotification(requestChar, true)
        gatt.setCharacteristicNotification(responseChar, true)
        enableNotify(gatt, responseChar)
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        EversenseLogger.debug(TAG, "onDescriptorWrite (${descriptor.uuid}) for characteristic (${descriptor.characteristic.uuid}) - status $status")

        if (status == BluetoothGatt.GATT_SUCCESS && descriptor.uuid.toString() == magicDescriptorUUID) {
            if (descriptor.characteristic.uuid.toString() == responseUUID) {
                bleExecutor.submit { authE3flow() }
            } else if (descriptor.characteristic.uuid.toString() == responseSecureV2UUID) {
                bleExecutor.submit { authV2flow() }
            }
        }
    }

    // FIX 7: Override both the deprecated and current API 33+ signature of onCharacteristicChanged.
    // On Android 13+ (API 33+) the old single-argument override is never called by the system —
    // only the new three-argument version is. Without this override, glucose data would be silently
    // dropped on API 33+ devices. Both delegates to a shared handler to avoid code duplication.
    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalStdlibApi::class)
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        handleCharacteristicChanged(gatt, value)
    }

    @Deprecated("Deprecated in API 33 — overridden for compatibility with Android < 13")
    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalStdlibApi::class)
    @Suppress("DEPRECATION")
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        handleCharacteristicChanged(gatt, characteristic.value)
    }

    // Returns the reassembled [prefix+ciphertext] blob once all chunks of a message have
    // arrived, or null while still waiting on more chunks. A malformed/out-of-sequence chunk
    // discards whatever was in progress rather than risk splicing mismatched chunks together.
    private fun accumulateChunk(rawData: ByteArray): ByteArray? {
        if (rawData.size < 2) {
            EversenseLogger.warning(TAG, "Chunk too short to contain a header - size: ${rawData.size}")
            return null
        }

        val chunkIndex = rawData[0].toInt() and 0xFF
        val totalChunks = rawData[1].toInt() and 0xFF

        if (chunkIndex == 1) {
            if (chunkNextIndex != 1) {
                EversenseLogger.warning(TAG, "New chunk sequence started before previous one (chunk $chunkNextIndex/$chunkTotalExpected) completed - discarding partial data")
            }
            chunkAccumulator = rawData.copyOfRange(3, rawData.size)
            chunkTotalExpected = totalChunks
            chunkNextIndex = 2
        } else {
            if (chunkIndex != chunkNextIndex || totalChunks != chunkTotalExpected) {
                EversenseLogger.warning(TAG, "Out-of-sequence chunk (got $chunkIndex/$totalChunks, expected $chunkNextIndex/$chunkTotalExpected) - discarding in-progress message")
                chunkAccumulator = ByteArray(0)
                chunkTotalExpected = 1
                chunkNextIndex = 1
                return null
            }
            chunkAccumulator += rawData.copyOfRange(2, rawData.size)
            chunkNextIndex++
        }

        if (chunkNextIndex <= chunkTotalExpected) return null

        val complete = chunkAccumulator
        chunkAccumulator = ByteArray(0)
        chunkTotalExpected = 1
        chunkNextIndex = 1
        return complete
    }

    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalStdlibApi::class)
    private fun handleCharacteristicChanged(gatt: BluetoothGatt, rawData: ByteArray) {
        EversenseLogger.debug(TAG, "Received data: ${rawData.toHexString()}")

        var data = rawData
        if (security == EversenseSecurityType.SecureV2) {
            data = accumulateChunk(rawData) ?: return

            if (data[0] != Eversense365Packets.AuthenticateResponseId) {
                data = cryptoUtil.decrypt(data)
                EversenseLogger.debug(TAG, "Decrypted data -> ${data.toHexString()}")
                if (data.isEmpty()) {
                    EversenseLogger.error(TAG, "Failed to decrypt data — disconnecting, will retry shortcut on next connection")
                    gatt.disconnect()
                    return
                }
            }
        }

        if (!is365() && EversenseE3Packets.isPushPacket(data[0])) {
            EversenseLogger.debug(TAG, "Keep Alive packet received (E3)!")
            bleExecutor.submit {
                // Sync transmitter clock before reading glucose so the glucose timestamp
                // reflects phone time, not the drifted transmitter clock. The official app
                // always calls postCurrentDateTimeRequest before postReadSensorGlucose.
                try {
                    val currentDatetime = writePacket<app.aaps.plugins.eversense.packets.e3.GetCurrentDatetimePacket.Response>(
                        app.aaps.plugins.eversense.packets.e3.GetCurrentDatetimePacket()
                    )
                    if (currentDatetime.needsTimeSync) {
                        EversenseLogger.info(TAG, "Clock drift detected before glucose read — syncing transmitter clock")
                        writePacket<app.aaps.plugins.eversense.packets.e3.SetCurrentDatetimePacket.Response>(
                            app.aaps.plugins.eversense.packets.e3.SetCurrentDatetimePacket()
                        )
                    }
                } catch (e: Exception) {
                    EversenseLogger.warning(TAG, "Pre-glucose clock sync failed (non-fatal): $e")
                }
                EversenseE3Communicator.readGlucose(this, preferences, plugin.watchers)
                EversenseE3Communicator.fullSync(this, preferences, plugin.watchers)
            }
            return
        }

        if (Eversense365Packets.isKeepAlivePacket(data[0], data[1])) {
            EversenseLogger.debug(TAG, "Keep Alive packet received (365)!")

            val packet = KeepAlivePacket()
            packet.appendData(data.toUByteArray())
            val response = packet.parseResponse() ?: return

            val fourHalfMinAgo = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(270)
            bleExecutor.submit {
                if (response.glucoseDatetime > fourHalfMinAgo) {
                    Eversense365Communicator.readGlucose(this, preferences, plugin.watchers)
                    Eversense365Communicator.fullSync(this, preferences, plugin.watchers)
                }
            }
            return
        } else if (data.size >= 4 && data[0] == Eversense365Packets.NotificationResponseId && data[1] == 0x03.toByte()) {
            // Push alarm notification
            val alarmCode = data[2].toInt() and 0xFF
            val alarm = app.aaps.plugins.eversense.models.ActiveAlarm(
                code = app.aaps.plugins.eversense.enums.EversenseAlarm.from(alarmCode),
                codeRaw = alarmCode,
                flag = 0,
                priority = 0
            )
            EversenseLogger.info(TAG, "Push alarm received: ${alarm.code.title}")
            handler.post {
                plugin.watchers.forEach { it.onAlarmReceived(alarm) }
            }
            return
        } else if (Eversense365Packets.isNotificationPacket(data[0])) {
            EversenseLogger.warning(TAG, "Unknown notification packet received")
            return
        }

        val packet = currentPacket.get() ?: run {
            EversenseLogger.warning(TAG, "currentPacket is null -> skipping packet")
            return
        }

        synchronized(packet) {
            val packetAnnotation = packet.getAnnotation() ?: run {
                EversenseLogger.warning(TAG, "annotation is null -> skipping packet")
                return
            }

            // Only treat 0x80 as an error if it is NOT the expected response ID for this packet.
            // ReadSingleByteSerialFlashRegister responses legitimately use 0xAA (170) as their
            // response ID — but earlier versions of this code checked 0x80 before checking the
            // expected response ID, causing every battery/readiness/calibration read to fail.
            if (EversenseE3Packets.isErrorPacket(data[0]) && packetAnnotation.responseId != data[0]) {
                EversenseLogger.error(TAG, "Received error response - data: ${data.toHexString()}")
                packet.isErrorResponse = true
                packet.notifyAll()
                return
            }

            if (security == EversenseSecurityType.None) {
                if (!packet.skipResponseIdValidation && packetAnnotation.responseId != data[0]) {
                    EversenseLogger.warning(TAG, "Incorrect responseId - expected: ${packetAnnotation.responseId}, got: ${data[0]}")
                    return
                }
                packet.appendData(data.toUByteArray())
                packet.notifyAll()
            } else {
                if (packetAnnotation.responseId != data[0]) {
                    EversenseLogger.warning(TAG, "Incorrect responseId - expected: ${packetAnnotation.responseId}, got: ${data[0]}")
                    return
                }
                if (packetAnnotation.typeId != data[1]) {
                    EversenseLogger.warning(TAG, "Incorrect responseType - expected: ${packetAnnotation.typeId}, got: ${data[1]}")
                    return
                }
                packet.appendData(data.toUByteArray())
                packet.notifyAll()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalStdlibApi::class)
    @Throws(EversenseWriteException::class)
    fun <T : EversenseBasePacket.Response> writePacket(packet: EversenseBasePacket, timeoutMs: Long = WRITE_TIMEOUT_MS): T {
        val gatt = bluetoothGatt ?: throw EversenseWriteException("Gatt is null — not connected")

        val requestCharacteristic = requestCharacteristic
            ?: throw EversenseWriteException("requestCharacteristic is null")

        val requestData = packet.buildRequest(cryptoUtil, payloadSize)
            ?: throw EversenseWriteException("Failed to build request data")

        // FIX 2: Use AtomicReference.set() for thread-safe assignment of currentPacket.
        currentPacket.set(packet)

        EversenseLogger.debug(TAG, "Writing data: ${requestData.toHexString()}")
        @Suppress("DEPRECATION")
        requestCharacteristic.setValue(requestData)
        @Suppress("DEPRECATION")
        gatt.writeCharacteristic(requestCharacteristic)

        synchronized(packet) {
            try {
                // FIX 8: Explicitly detect timeout by comparing elapsed time after wait() returns.
                // Previously, a timeout would fall through to parseResponse() silently, likely
                // producing a confusing cast exception rather than a clear timeout error.
                val start = System.currentTimeMillis()
                packet.wait(timeoutMs)
                val elapsed = System.currentTimeMillis() - start
                if (elapsed >= timeoutMs) {
                    currentPacket.set(null)
                    throw EversenseWriteException("Timed out waiting for response after ${timeoutMs}ms — packet: ${packet.getAnnotation()?.responseId}")
                } else if (packet.isErrorResponse) {
                    currentPacket.set(null)
                    throw EversenseWriteException("Transmitter returned error response — packet: ${packet.getAnnotation()?.responseId}")
                }
            } catch (e: EversenseWriteException) {
                throw e
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "Exception during packet wait: $e")
                e.printStackTrace()
            }
        }

        return try {
            val response = packet.parseResponse()
            currentPacket.set(null)
            response as? T
                ?: throw EversenseWriteException("Unable to cast response — packet: ${packet.getAnnotation()?.responseId}")
        } catch (e: EversenseWriteException) {
            throw e
        } catch (e: Exception) {
            throw EversenseWriteException("Failed to parse response: $e")
        }
    }

    private fun authE3flow() {
        EversenseLogger.info(TAG, "Starting auth flow E3...")
        try {
            writePacket<SaveBondingInformationPacket.SaveBondingInformationResponse>(SaveBondingInformationPacket())
        } catch (exception: Exception) {
            EversenseLogger.error(TAG, "Auth flow E3 failed: $exception")
            return
        }

        EversenseLogger.info(TAG, "E3 auth complete — notifying watchers")
        // fullSync is triggered by onConnectionChanged via triggerFullSync on the bleExecutor.
        // Do NOT call fullSync here — it would race with the triggerFullSync call.
        transmitterReady = true
        handler.post { plugin.watchers.forEach { it.onTransmitterReady() } }
    }

    @SuppressLint("MissingPermission")
    private fun authV2flow() {
        // FIX 9: Network calls (login, getFleetSecretV2) are dispatched to a separate networkExecutor
        // so they do not block the bleExecutor, which must remain available for BLE callbacks.
        try {
            if (!cryptoUtil.generateKeyPairIfNotExists()) {
                bluetoothGatt?.disconnect()
                return
            }

            if (!cryptoUtil.canUseShortcut()) {
                val clientId = cryptoUtil.getClientId()
                val whoAmI = writePacket<AuthWhoAmIPacket.Response>(AuthWhoAmIPacket(clientId))

                // Dispatch HTTP work to the network executor and block bleExecutor until complete.
                                // Sync credentials from AAPS layer into SECURE_STATE before login
                // Guaranteed same-thread write immediately before network call
                if (plugin.username.isNotEmpty() && plugin.password.isNotEmpty()) {
                    val stateJson = preferences.getString(app.aaps.plugins.eversense.util.StorageKeys.SECURE_STATE, null) ?: "{}"
                    val secState = kotlinx.serialization.json.Json.decodeFromString<app.aaps.plugins.eversense.models.EversenseSecureState>(stateJson)
                    secState.username = plugin.username
                    secState.password = plugin.password
                    preferences.edit().putString(app.aaps.plugins.eversense.util.StorageKeys.SECURE_STATE,
                        kotlinx.serialization.json.Json.encodeToString(app.aaps.plugins.eversense.models.EversenseSecureState.serializer(), secState)).apply()
                    EversenseLogger.info(TAG, "[365] Credentials synced to SECURE_STATE before login")
                }
val authSession = networkExecutor.submit<Any?> {
                    EversenseHttp365Util.login(preferences)
                }.get() ?: run {
                    bluetoothGatt?.disconnect()
                    return
                }

                authSession as? EversenseHttp365Util.LoginResponseModel ?: run {
                    bluetoothGatt?.disconnect()
                    return
                }

                // Cache access token so it can be used for cloud uploads without re-login
                val expiryMs = System.currentTimeMillis() + (authSession.expires_in * 1000L)
                preferences.edit().putString(StorageKeys.ACCESS_TOKEN, authSession.access_token)
                    .putLong(StorageKeys.ACCESS_TOKEN_EXPIRY, expiryMs).apply()

                val fleet = networkExecutor.submit<Any?> {
                    EversenseHttp365Util.getFleetSecretV2(
                        accessToken = authSession.access_token,
                        serialNumber = whoAmI.serialNumber,
                        nonce = whoAmI.nonce,
                        flags = whoAmI.flags,
                        publicKey = cryptoUtil.getClientPublicKey()
                    )
                }.get() ?: run {
                    bluetoothGatt?.disconnect()
                    return
                }

                val fleetResponse = fleet as? EversenseHttp365Util.FleetSecretV2ResponseModel ?: run {
                    bluetoothGatt?.disconnect()
                    return
                }

                @OptIn(ExperimentalStdlibApi::class)
                writePacket<AuthIdentityPacket.Response>(
                    AuthIdentityPacket(fleetResponse.Result.Certificate?.hexToByteArray() ?: byteArrayOf())
                )

                cryptoUtil.allowUseShortcut()
            }

            val signature = cryptoUtil.generateEphem() ?: run {
                bluetoothGatt?.disconnect()
                return
            }

            val session = writePacket<AuthStartPacket.Response>(AuthStartPacket(cryptoUtil.getStartSecret(signature)))
            cryptoUtil.generateSessionKey(session.sessionPublicKey)

            // Auth succeeded — reset the shortcut fail counter
            shortcutFailCount = 0

            EversenseLogger.info(TAG, "365 auth complete — ready for full sync")
            Eversense365Communicator.fullSync(this, preferences, plugin.watchers, force = true)
                        // Read glucose immediately after auth so readings don't wait for next Keep Alive
            // This prevents late readings after reconnect from fullSync disconnect fix
            try {
                Eversense365Communicator.readGlucose(this, preferences, plugin.watchers)
            } catch (e: Exception) {
                EversenseLogger.warning(TAG, "[365] readGlucose after auth failed (non-fatal): $e")
            }
EversenseLogger.info(TAG, "365 transmitter ready — notifying watchers")
            transmitterReady = true
            handler.post { plugin.watchers.forEach { it.onTransmitterReady() } }

        } catch (exception: Exception) {
            EversenseLogger.error(TAG, "[365] authV2 failed: $exception")
            exception.printStackTrace()

            // After first successful auth, never call DMS server again.
            // DMS login only happens on fresh install, app update, or phone reboot
            // (all of which clear SharedPreferences and reset canUseShortcut to false).
            // On shortcut failure just log and retry — do NOT fall back to DMS.
            if (cryptoUtil.canUseShortcut()) {
                shortcutFailCount++
                EversenseLogger.warning(TAG, "Shortcut auth failed () — will retry shortcut on next connect (no DMS re-auth)")
                // Reset counter after threshold but keep canUseShortcut=true
                // so DMS is never called again after first successful auth
                if (shortcutFailCount >= SHORTCUT_FAIL_THRESHOLD) {
                    EversenseLogger.warning(TAG, "Shortcut fail threshold reached — resetting counter, keeping shortcut enabled")
                    shortcutFailCount = 0
                }
            }
        bluetoothGatt?.disconnect()
        }
    }

    // FIX 10: enableNotify uses the API 33+ writeDescriptor(descriptor, value) overload when
    // available, falling back to the deprecated setValue approach on older API levels.
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun enableNotify(gatt: BluetoothGatt, responseCharacteristic: BluetoothGattCharacteristic) {
        val descriptor = responseCharacteristic.getDescriptor(UUID.fromString(magicDescriptorUUID)) ?: return

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }
}