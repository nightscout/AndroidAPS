package app.aaps.plugins.eversense

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.aaps.plugins.eversense.connection.PacketExchange
import app.aaps.plugins.eversense.connection.SessionStore
import app.aaps.plugins.eversense.enums.EversenseAlarm
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.exceptions.EversenseWriteException
import app.aaps.plugins.eversense.packets.Eversense365Communicator
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversenseE3Communicator
import app.aaps.plugins.eversense.packets.e3.EversenseE3Packets
import app.aaps.plugins.eversense.packets.e3.SaveBondingInformationPacket
import app.aaps.plugins.eversense.packets.e365.AuthIdentityPacket
import app.aaps.plugins.eversense.packets.e365.AuthStartPacket
import app.aaps.plugins.eversense.packets.e365.AuthWhoAmIPacket
import app.aaps.plugins.eversense.packets.e365.Eversense365Packets
import app.aaps.plugins.eversense.packets.e365.KeepAlivePacket
import app.aaps.plugins.eversense.packets.e365.PushAlarmWithDataPacket
import app.aaps.plugins.eversense.protocol.NotificationAssembler
import app.aaps.plugins.eversense.storage.EversenseStore
import app.aaps.plugins.eversense.storage.edit
import app.aaps.plugins.eversense.util.EversenseCrypto365Util
import app.aaps.plugins.eversense.util.EversenseHttp365Util
import app.aaps.plugins.eversense.util.EversenseLogger
import app.aaps.plugins.eversense.util.StorageKeys
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class EversenseGattCallback internal constructor(
    private val plugin: EversenseCGMPlugin,
    private val sessionStore: SessionStore,
    internal val session: Long
) : BluetoothGattCallback() {

    @Volatile private var closed = false
    private val writeLock = ReentrantLock()
    @Volatile private var linkGeneration = 0L
    private val workGeneration = ThreadLocal<Long>()
    internal val stateStore get() = sessionStore.restrict {
        workGeneration.get()?.let { it == linkGeneration } ?: true
    }
    private val preferences get() = stateStore
    internal fun operationStore(): EversenseStore {
        val generation = linkGeneration
        return sessionStore.restrict { generation == linkGeneration }
    }
    private fun active() = !closed && plugin.sessionActive(session) &&
        (workGeneration.get()?.let { it == linkGeneration } ?: true)
    private fun accepts(gatt: BluetoothGatt?) = active() && gatt != null && (bluetoothGatt == null || bluetoothGatt === gatt)

    companion object {
        private const val TAG = "EversenseGattCallback"

        const val serviceUUID = "c3230001-9308-47ae-ac12-3d030892a211"

        private const val requestUUID = "6eb0f021-a7ba-7e7d-66c9-6d813f01d273"
        private const val requestSecureV2UUID = "c3230002-9308-47ae-ac12-3d030892a211"

        private const val responseUUID = "6eb0f024-bd60-7aaa-25a7-0029573f4f23"
        private const val responseSecureV2UUID = "c3230003-9308-47ae-ac12-3d030892a211"
        private const val magicDescriptorUUID = "00002902-0000-1000-8000-00805f9b34fb"

        private const val WRITE_TIMEOUT_MS = 5000L

    }

    // FIX 1: Dedicated BLE executor for callbacks; separate network executor for HTTP calls
    // so that network operations in authV2flow() cannot block BLE processing.
    private var bleExecutor = Executors.newSingleThreadExecutor()
    private var networkExecutor = Executors.newSingleThreadExecutor()

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var bluetoothGatt: BluetoothGatt? = null
    private var eversenseBluetoothService: BluetoothGattService? = null
    private var requestCharacteristic: BluetoothGattCharacteristic? = null
    private var responseCharacteristic: BluetoothGattCharacteristic? = null

    private var payloadSize: Int = 20
    private var security: EversenseSecurityType = EversenseSecurityType.None
    private var cryptoUtil = EversenseCrypto365Util(preferences)

    private val notifications = NotificationAssembler(SystemClock::elapsedRealtime) { notice ->
        when (notice) {
            NotificationAssembler.Notice.DUPLICATE ->
                EversenseLogger.debug(TAG) { "Duplicate notification ignored" }
            else -> EversenseLogger.warning(TAG, "Notification assembly: $notice")
        }
    }

    private val currentExchange = AtomicReference<PacketExchange<EversenseBasePacket>?>(null)

    // FIX 3: Track connection state with a dedicated flag rather than relying on bluetoothGatt
    // being non-null, which is not a reliable indicator of actual connection state.
    @Volatile
    private var connected: Boolean = false
    @Volatile private var transmitterReady: Boolean = false

    fun isConnected(): Boolean = connected && transmitterReady
    fun isBleConnected(): Boolean = connected
    fun is365(): Boolean = security == EversenseSecurityType.SecureV2

    // Submit a task to the bleExecutor and return a Future so callers can block until complete.
    // This ensures calibration and other ad-hoc BLE operations are serialised with Keep Alive
    // cycles and do not race with currentPacket assignment.
    fun <R> submitToExecutor(readyOnly: Boolean = false, task: () -> R): Future<R> {
        val generation = linkGeneration
        return try { bleExecutor.submit<R> {
            workGeneration.set(generation)
            try {
                check(active() && (!readyOnly || isConnected())) { "Eversense session is not ready" }
                task()
            } finally { workGeneration.remove() }
        } } catch (e: java.util.concurrent.RejectedExecutionException) {
            java.util.concurrent.CompletableFuture.failedFuture(e)
        }
    }

    fun hasGatt() = bluetoothGatt != null

    @SuppressLint("MissingPermission")
    fun reuse(): Boolean {
        if (!active()) return false
        notifications.reset()
        return bluetoothGatt?.connect() == true
    }

    @SuppressLint("MissingPermission")
    fun closeSession() {
        closed = true
        connected = false
        transmitterReady = false
        handler.removeCallbacksAndMessages(null)
        currentExchange.getAndSet(null)?.fail(EversenseWriteException("Session closed"))
        val old = bluetoothGatt
        bluetoothGatt = null
        try {
            try { old?.disconnect() } catch (_: SecurityException) { }
            try { old?.close() } catch (_: SecurityException) { }
        } finally {
            bleExecutor.shutdownNow().forEach { (it as? Future<*>)?.cancel(false) }
            networkExecutor.shutdownNow().forEach { (it as? Future<*>)?.cancel(false) }
        }
    }

    fun disconnectAndScheduleReconnect() {
        if (active()) plugin.sessionDisconnected(session, BluetoothGatt.GATT_SUCCESS)
    }

    /** Owns even a GATT handle which never reaches STATE_CONNECTED. */
    @SuppressLint("MissingPermission")
    fun attachGatt(gatt: BluetoothGatt?) {
        if (gatt == null) return
        if (!active()) { gatt.close(); return }
        bluetoothGatt?.takeIf { it !== gatt }?.let {
            EversenseLogger.info(TAG, "Closing an earlier GATT handle before using the new one")
            it.disconnect()
            it.close()
        }
        bluetoothGatt = gatt
    }
    @SuppressLint("MissingPermission")
    fun readRssi() {
        bluetoothGatt?.readRemoteRssi() ?: EversenseLogger.warning(TAG, "Cannot read RSSI — not connected")
    }

    @SuppressLint("MissingPermission")
    override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) = plugin.deliver(session) { handleOnReadRemoteRssi(gatt, rssi, status) }

    private fun handleOnReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
        if (!accepts(gatt)) return
        if (status == BluetoothGatt.GATT_SUCCESS) {
            EversenseLogger.debug(TAG) { "RSSI: $rssi dBm" }
            plugin.onRssiRead(rssi, session)
        } else {
            EversenseLogger.warning(TAG, "Failed to read RSSI - status: $status")
        }
    }

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) = plugin.deliver(session) { handleOnConnectionStateChange(gatt, status, newState) }

    private fun handleOnConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (!accepts(gatt)) return
        EversenseLogger.info(TAG, "Connection state changed - status: $status, newState: $newState")
        EversenseLogger.debug(TAG) { "Connected device: ${gatt.device.name}" }

        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            bluetoothGatt = gatt
            // FIX 3: Set connected flag on confirmed STATE_CONNECTED.
            connected = true

            preferences.edit(commit = true) {
                putString(StorageKeys.REMOTE_DEVICE_KEY, gatt.device.address)
            }

            // FIX 5: Both connect and disconnect watcher notifications are now dispatched via
            // handler.post() so they always arrive on the main thread, preventing UI thread crashes.
            handler.post {
                plugin.watchersFor(this.session).forEach { it.onConnectionChanged(true) }
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

            connected = false
            transmitterReady = false
            linkGeneration++
            currentExchange.getAndSet(null)?.fail(EversenseWriteException("Link interrupted"))
            // The controller owns retries. A normal 365 cycle keeps its GATT/bond.
            val reuse = status == 19 && is365()
            plugin.sessionDisconnected(session, status, reuse)
            handler.post { plugin.watchersFor(this.session).forEach { it.onConnectionChanged(false) } }

            plugin.placementChanged(session, status, is365())

        }
    }

    @SuppressLint("MissingPermission")
    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) = plugin.deliver(session) { handleOnMtuChanged(gatt, mtu, status) }

    private fun handleOnMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        if (!accepts(gatt)) return
        if (status != 0) {
            EversenseLogger.warning(TAG, "MTU negotiation failed (status: $status) — using default payload size of 20")
            payloadSize = 20
        } else {
            payloadSize = mtu - 3
        }
        EversenseLogger.debug(TAG) { "New payload size: $payloadSize" }

        val success = gatt?.discoverServices()
        EversenseLogger.info(TAG, "Trigger discover services - success: $success")
    }

    @SuppressLint("MissingPermission")
    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) = plugin.deliver(session) { handleOnServicesDiscovered(gatt, status) }

    private fun handleOnServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        if (!accepts(gatt)) return
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

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = plugin.deliver(session) { handleOnDescriptorWrite(gatt, descriptor, status) }

    private fun handleOnDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        if (!accepts(gatt)) return
        EversenseLogger.debug(TAG) { "onDescriptorWrite (${descriptor.uuid}) for characteristic (${descriptor.characteristic.uuid}) - status $status" }

        if (status == BluetoothGatt.GATT_SUCCESS && descriptor.uuid.toString() == magicDescriptorUUID) {
            if (descriptor.characteristic.uuid.toString() == responseUUID) {
                submitToExecutor { authE3flow() }
            } else if (descriptor.characteristic.uuid.toString() == responseSecureV2UUID) {
                submitToExecutor { authV2flow() }
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
    ) = plugin.deliver(session) { handleOnCharacteristicChanged(gatt, characteristic, value) }

    private fun handleOnCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        if (!accepts(gatt)) return
        handleCharacteristicChanged(gatt, value)
    }

    @Deprecated("Deprecated in API 33 — overridden for compatibility with Android < 13")
    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalStdlibApi::class)
    @Suppress("DEPRECATION")
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) = plugin.deliver(session) { handleOnCharacteristicChanged(gatt, characteristic) }

    private fun handleOnCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (!accepts(gatt)) return
        handleCharacteristicChanged(gatt, characteristic.value)
    }

    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalStdlibApi::class)
    private fun handleCharacteristicChanged(gatt: BluetoothGatt, rawData: ByteArray) {
        if (!accepts(gatt)) return
        EversenseLogger.debug(TAG) { "Received data: ${rawData.toHexString()}" }

        if (rawData.isEmpty()) return
        var data = rawData
        if (security == EversenseSecurityType.SecureV2) {
            data = notifications.accept(rawData) ?: return

            if (data[0] != Eversense365Packets.AuthenticateResponseId) {
                data = cryptoUtil.decrypt(data)
                EversenseLogger.debug(TAG) { "Decrypted data -> ${data.toHexString()}" }
                if (data.isEmpty()) {
                    // A decrypt/MAC failure means the session key no longer matches the
                    // transmitter, so retrying the shortcut is guaranteed to fail the same way.
                    // Force a full WhoAmI + DMS re-auth on the next connect, so we recover on the
                    // first retry instead of waiting out SHORTCUT_FAIL_THRESHOLD auth timeouts.
                    if (plugin.hasInternet()) {
                        EversenseLogger.error(TAG, "Failed to decrypt data — requesting full re-auth on next connect")
                        cryptoUtil.disallowUseShortcut()
                    } else {
                        EversenseLogger.warning(TAG, "Failed to decrypt data offline — retaining registered identity for a new local handshake")
                    }
                    disconnectAndScheduleReconnect()
                    return
                }
            }
        }

        if (!is365() && EversenseE3Packets.isPushPacket(data[0])) {
            EversenseLogger.debug(TAG) { "Keep Alive packet received (E3)!" }
            submitToExecutor {
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
                EversenseE3Communicator.readGlucose(this, preferences, plugin.watchersFor(this.session))
                EversenseE3Communicator.fullSync(this, preferences, plugin.watchersFor(this.session))
            }
            return
        }

        if (data.size >= 2 && Eversense365Packets.isKeepAlivePacket(data[0], data[1])) {
            EversenseLogger.debug(TAG) { "Keep Alive packet received (365)!" }

            val packet = KeepAlivePacket()
            packet.appendData(data.toUByteArray())
            val response = packet.parseResponse() ?: return

            val fourHalfMinAgo = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(270)
            submitToExecutor {
                if (response.glucoseDatetime > fourHalfMinAgo) {
                    Eversense365Communicator.readGlucose(this, preferences, plugin.watchersFor(this.session))
                    Eversense365Communicator.fullSync(this, preferences, plugin.watchersFor(this.session))
                }
            }
            return
        } else if (data.size >= 2 && data[0] == Eversense365Packets.NotificationResponseId && data[1] == Eversense365Packets.NotificationAlarmWithData) {
            // Push alarm notification. Delegate to PushAlarmWithDataPacket (see its own doc comment
            // for the empirically-confirmed byte layout) instead of duplicating the parsing inline,
            // matching how KeepAlivePacket is already handled a few lines above.
            val packet = PushAlarmWithDataPacket()
            packet.appendData(data.toUByteArray())
            val response = packet.parseResponse() ?: run {
                EversenseLogger.warning(TAG, "Push alarm packet too short -> skipping")
                return
            }
            if (response.alarm.code == EversenseAlarm.UNKNOWN) {
                // Unrecognized/bogus codes (e.g. the removed TxDocked/TxUndocked 68/69) must not
                // surface as a real alarm - matches the upstream iOS EversenseKit fix.
                EversenseLogger.warning(TAG, "Received unknown push alarm code: ${response.alarm.codeRaw}")
                return
            }
            EversenseLogger.info(TAG, "Push alarm received: ${response.alarm.code.title}")
            handler.post {
                plugin.watchersFor(this.session).forEach { it.onAlarmReceived(response.alarm) }
            }
            return
        } else if (Eversense365Packets.isNotificationPacket(data[0])) {
            EversenseLogger.warning(TAG, "Unknown notification packet received")
            return
        }

        val exchange = currentExchange.get() ?: run {
            EversenseLogger.warning(TAG, "currentPacket is null -> skipping packet")
            return
        }

        val packet = exchange.packet
        synchronized(packet) {
            val spec = packet.spec

            // SecureV2 rejects a command with [0xff, command type, error code].
            // Complete the matching exchange as rejected, rather than timing out and
            // tearing down an authenticated link (observed: ff 0e 05 for SetAppVersion).
            if (security == EversenseSecurityType.SecureV2 &&
                data.size == 3 && data[0] == 0xff.toByte() &&
                data[1] == spec.typeId
            ) {
                EversenseLogger.warning(TAG, "365 command rejected - type: ${data[1].toInt() and 0xff}, error: ${data[2].toInt() and 0xff}")
                packet.isErrorResponse = true
                exchange.complete()
                return
            }

            // Only treat 0x80 as an error if it is NOT the expected response ID for this packet.
            // ReadSingleByteSerialFlashRegister responses legitimately use 0xAA (170) as their
            // response ID — but earlier versions of this code checked 0x80 before checking the
            // expected response ID, causing every battery/readiness/calibration read to fail.
            if (EversenseE3Packets.isErrorPacket(data[0]) && spec.responseId != data[0]) {
                EversenseLogger.error(TAG, "Transmitter returned an error response")
                EversenseLogger.debug(TAG) { "Error response data: ${data.toHexString()}" }
                packet.isErrorResponse = true
                exchange.complete()
                return
            }

            if (security == EversenseSecurityType.None) {
                if (!packet.skipResponseIdValidation && spec.responseId != data[0]) {
                    EversenseLogger.warning(TAG, "Incorrect responseId - expected: ${spec.responseId}, got: ${data[0]}")
                    return
                }
                packet.appendData(data.toUByteArray())
                exchange.complete()
            } else {
                if (spec.responseId != data[0]) {
                    EversenseLogger.warning(TAG, "Incorrect responseId - expected: ${spec.responseId}, got: ${data[0]}")
                    return
                }
                if (data.size < 2 || spec.typeId != data[1]) {
                    EversenseLogger.warning(TAG, "Incorrect responseType - expected: ${spec.typeId}, got: ${data.getOrNull(1)}")
                    return
                }
                packet.appendData(data.toUByteArray())
                exchange.complete()
            }
        }
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    @SuppressLint("MissingPermission")
    fun <T : EversenseBasePacket.Response> writePacket(packet: EversenseBasePacket, timeoutMs: Long = WRITE_TIMEOUT_MS): T = writeLock.withLock {
        if (!active() || Thread.currentThread().isInterrupted) throw EversenseWriteException("Session closed")
        val gatt = bluetoothGatt ?: throw EversenseWriteException("GATT is not connected")
        val characteristic = requestCharacteristic ?: throw EversenseWriteException("Request characteristic is missing")
        val request = packet.buildRequest(cryptoUtil, payloadSize) ?: throw EversenseWriteException("Cannot build packet")
        val exchange = PacketExchange(packet)
        try {
            var accepted = false
            // Register and send under the lifecycle lock: stop either rejects this send or
            // observes its exchange and wakes the waiter. Never register after teardown.
            plugin.deliver(session) {
                if (active()) {
                    currentExchange.set(exchange)
                    characteristic.value = request
                    accepted = gatt.writeCharacteristic(characteristic)
                }
            }
            if (!accepted) throw EversenseWriteException("BLE write was rejected or session closed")
            exchange.await(timeoutMs)
            if (!active()) throw EversenseWriteException("Session ended during write")
            if (packet.isErrorResponse) throw EversenseWriteException("Transmitter rejected the packet")
            packet.parseResponse() as? T ?: throw EversenseWriteException("Invalid packet response")
        } catch (e: TimeoutException) {
            // Do not let a late reply be consumed by the next command on the same link.
            if (active()) plugin.sessionDisconnected(session, -1)
            throw EversenseWriteException("Packet response timed out after ${timeoutMs}ms")
        } catch (e: InterruptedException) {
            if (active()) plugin.sessionDisconnected(session, -1)
            Thread.currentThread().interrupt()
            throw EversenseWriteException("Packet write was interrupted")
        } catch (e: ExecutionException) {
            throw EversenseWriteException("Packet exchange ended: ${e.cause}")
        } finally {
            currentExchange.compareAndSet(exchange, null)
        }
    }

    private fun authE3flow() {
        EversenseLogger.info(TAG, "Starting auth flow E3...")
        try {
            writePacket<SaveBondingInformationPacket.SaveBondingInformationResponse>(SaveBondingInformationPacket())
        } catch (exception: Exception) {
            if (!active()) return
            EversenseLogger.error(TAG, "Auth flow E3 failed: $exception")
            return
        }

        EversenseLogger.info(TAG, "E3 auth complete — notifying watchers")
        // fullSync is triggered by onConnectionChanged via triggerFullSync on the bleExecutor.
        // Do NOT call fullSync here — it would race with the triggerFullSync call.
        if (!active()) return
        transmitterReady = true
        plugin.sessionReady(this.session)
        handler.post { plugin.watchersFor(this.session).forEach { it.onTransmitterReady() } }
    }

    @SuppressLint("MissingPermission")
    private fun authV2flow() {
        var shortcutAttempt = false
        val generation = linkGeneration
        val networkStore = stateStore.restrict { generation == linkGeneration }
        // FIX 9: Network calls (login, getFleetSecretV2) are dispatched to a separate networkExecutor
        // so they do not block the bleExecutor, which must remain available for BLE callbacks.
        try {
            shortcutAttempt = cryptoUtil.canUseShortcut()
            if (!cryptoUtil.generateKeyPairIfNotExists()) {
                bluetoothGatt?.disconnect()
                return
            }

            if (!cryptoUtil.canUseShortcut()) {
                if (!plugin.hasInternet()) {
                    EversenseLogger.warning(TAG, "365 registration requires internet; retaining stored identity and retrying later")
                    disconnectAndScheduleReconnect()
                    return
                }
                val clientId = cryptoUtil.getClientId()
                val whoAmI = writePacket<AuthWhoAmIPacket.Response>(AuthWhoAmIPacket(clientId))

                // HTTP results carry the same link generation as the authentication attempt.
                val authSession = networkExecutor.submit<Any?> {
                    EversenseHttp365Util.login(networkStore)
                }.get(65, TimeUnit.SECONDS) ?: run {
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
                        preferences = networkStore,
                        accessToken = authSession.access_token,
                        serialNumber = whoAmI.serialNumber,
                        nonce = whoAmI.nonce,
                        flags = whoAmI.flags,
                        publicKey = cryptoUtil.getClientPublicKey()
                    )
                }.get(65, TimeUnit.SECONDS) ?: run {
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

            if (!active()) return
            // Auth succeeded — reset both fail counters
            plugin.authenticationSucceeded(this.session)

            EversenseLogger.info(TAG, "365 auth complete — ready for full sync")
            Eversense365Communicator.fullSync(this, preferences, plugin.watchersFor(this.session), force = true)
            // Read glucose immediately after auth so readings don't wait for next Keep Alive
            // This prevents late readings after reconnect from fullSync disconnect fix
            try {
                Eversense365Communicator.readGlucose(this, preferences, plugin.watchersFor(this.session))
            } catch (e: Exception) {
                EversenseLogger.warning(TAG, "[365] readGlucose after auth failed (non-fatal): $e")
            }
            EversenseLogger.info(TAG, "365 transmitter ready — notifying watchers")
            if (!active()) return
            transmitterReady = true
            plugin.sessionReady(this.session)
            // Attempt finished successfully; release the in-progress guard.
            handler.post { plugin.watchersFor(this.session).forEach { it.onTransmitterReady() } }

        } catch (exception: Exception) {
            if (generation != linkGeneration) return
            plugin.authenticationFailed(this.session, shortcutAttempt)
            if (!active()) return
            EversenseLogger.error(TAG, "[365] authV2 failed: $exception")
            EversenseLogger.debug(TAG) { exception.stackTraceToString() }

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