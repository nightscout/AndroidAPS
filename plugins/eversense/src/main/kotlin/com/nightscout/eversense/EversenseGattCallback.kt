package com.nightscout.eversense

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
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.exceptions.EversenseWriteException
import com.nightscout.eversense.packets.Eversense365Communicator
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversenseE3Communicator
import com.nightscout.eversense.packets.e365.AuthIdentityPacket
import com.nightscout.eversense.packets.e365.AuthStartPacket
import com.nightscout.eversense.packets.e365.AuthWhoAmIPacket
import com.nightscout.eversense.packets.e365.Eversense365Packets
import com.nightscout.eversense.packets.e365.KeepAlivePacket
import com.nightscout.eversense.packets.e3.EversenseE3Packets
import com.nightscout.eversense.packets.e3.SaveBondingInformationPacket
import com.nightscout.eversense.util.EversenseCrypto365Util
import com.nightscout.eversense.util.EversenseHttp365Util
import com.nightscout.eversense.util.EversenseLogger
import com.nightscout.eversense.util.StorageKeys
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
    }

    // FIX 1: Dedicated BLE executor for callbacks; separate network executor for HTTP calls
    // so that network operations in authV2flow() cannot block BLE processing.
    private val bleExecutor = Executors.newSingleThreadExecutor()
    private val networkExecutor = Executors.newSingleThreadExecutor()

    private val handler = Handler(Looper.getMainLooper())
    private var bluetoothGatt: BluetoothGatt? = null
    private var eversenseBluetoothService: BluetoothGattService? = null
    private var requestCharacteristic: BluetoothGattCharacteristic? = null
    private var responseCharacteristic: BluetoothGattCharacteristic? = null

    private var payloadSize: Int = 20
    private var security: EversenseSecurityType = EversenseSecurityType.None
    private var cryptoUtil = EversenseCrypto365Util(preferences)

    // FIX 2: Use AtomicReference for currentPacket to avoid the race condition where a stale
    // BLE notification could be processed against the wrong packet between assignment and write.
    var currentPacket: AtomicReference<EversenseBasePacket?> = AtomicReference(null)

    // FIX 3: Track connection state with a dedicated flag rather than relying on bluetoothGatt
    // being non-null, which is not a reliable indicator of actual connection state.
    @Volatile
    private var connected: Boolean = false

    // Tracks consecutive failed connection attempts to detect transmitter placement issues
    @Volatile
    private var failedConnectionAttempts: Int = 0
    private val PLACEMENT_WARNING_THRESHOLD = 3

    // When true, AAPS disconnects after each sync to give the official Eversense app
    // a 2-minute window to connect and upload data to the cloud. Reconnects automatically.
    @Volatile
    var coexistenceMode: Boolean = false

    // True during a planned coexistence disconnect — suppresses normal auto-reconnect
    @Volatile
    private var plannedDisconnect: Boolean = false

    fun isConnected(): Boolean = connected
    fun is365(): Boolean = security == EversenseSecurityType.SecureV2

    // FIX 4: Added disconnect() which calls both disconnect() and close() on the GATT client.
    // Calling only disconnect() without close() leaks the underlying GATT client resource.
    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        connected = false
        EversenseLogger.info(TAG, "GATT disconnected and closed")
    }
    @SuppressLint("MissingPermission")
    fun cleanUp() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        connected = false
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

            preferences.edit(commit = true) {
                putString(StorageKeys.REMOTE_DEVICE_KEY, gatt.device.address)
            }

            // FIX 5: Both connect and disconnect watcher notifications are now dispatched via
            // handler.post() so they always arrive on the main thread, preventing UI thread crashes.
            handler.post {
                plugin.watchers.forEach { it.onConnectionChanged(true) }
            }

            if (!gatt.requestMtu(512)) {
                EversenseLogger.error(TAG, "Failed to request MTU")
            }
            return
        }

        if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
            EversenseLogger.warning(TAG, "Disconnected or failed - status: $status, newState: $newState")
            gatt.close()
            bluetoothGatt = null
            connected = false

            handler.post {
                plugin.watchers.forEach { it.onConnectionChanged(false) }
            }

            if (status == 19) {
                failedConnectionAttempts++
                EversenseLogger.warning(TAG, "Connection terminated by transmitter (status 19) — attempt $failedConnectionAttempts")
                if (failedConnectionAttempts >= PLACEMENT_WARNING_THRESHOLD) {
                    handler.post { plugin.watchers.forEach { it.onTransmitterNotPlaced() } }
                }
            } else {
                failedConnectionAttempts = 0
            }

            val storedAddress = preferences.getString(StorageKeys.REMOTE_DEVICE_KEY, null)
            if (storedAddress != null && !plannedDisconnect) {
                val delayMs = if (status == BluetoothGatt.GATT_SUCCESS) 5000L else 10000L
                EversenseLogger.info(TAG, "Scheduling auto-reconnect in ${delayMs/1000}s (status: $status)")
                handler.postDelayed({
                    EversenseLogger.info(TAG, "Attempting auto-reconnect...")
                    plugin.connect(null)
                }, delayMs)
            } else {
                if (!plannedDisconnect) { EversenseLogger.warning(TAG, "No stored device address — skipping auto-reconnect") } else { EversenseLogger.info(TAG, "Planned coexistence disconnect — suppressing auto-reconnect") }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        if (status != 0) {
            EversenseLogger.error(TAG, "Failed to set payload size - status: $status")
            return
        }

        payloadSize = mtu - 3
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

    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalStdlibApi::class)
    private fun handleCharacteristicChanged(gatt: BluetoothGatt, rawData: ByteArray) {
        EversenseLogger.debug(TAG, "Received data: ${rawData.toHexString()}")

        var data = rawData
        if (security == EversenseSecurityType.SecureV2) {
            data = data.drop(3).toByteArray()

            if (data[0] != Eversense365Packets.AuthenticateResponseId) {
                data = cryptoUtil.decrypt(data)
                EversenseLogger.debug(TAG, "Decrypted data -> ${data.toHexString()}")
                if (data.isEmpty()) {
                    EversenseLogger.error(TAG, "Failed to decrypt data — re-running security handshake on next connection")
                    cryptoUtil.disallowUseShortcut()
                    gatt.disconnect()
                    return
                }
            }
        }

        if (EversenseE3Packets.isPushPacket(data[0])) {
            EversenseLogger.debug(TAG, "Keep Alive packet received (E3)!")
            bleExecutor.submit {
                EversenseE3Communicator.readGlucose(this, preferences, plugin.watchers)
                EversenseE3Communicator.fullSync(this, preferences, plugin.watchers)
                    if (coexistenceMode) {
                        EversenseLogger.info(TAG, "Coexistence — disconnecting to give official app 2-minute window")
                        plannedDisconnect = true
                        disconnect()
                        handler.postDelayed({
                            EversenseLogger.info(TAG, "Coexistence reconnect — official app window ended")
                            plannedDisconnect = false
                            plugin.connect(null)
                        }, 120000L)
                    }
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
                if (coexistenceMode && !plannedDisconnect) {
                    EversenseLogger.info(TAG, "Coexistence — disconnecting to give official app 2-minute window")
                    plannedDisconnect = true
                    disconnect()
                    handler.postDelayed({
                        EversenseLogger.info(TAG, "Coexistence reconnect — official app window ended")
                        plannedDisconnect = false
                        plugin.connect(null)
                    }, 120000L)
                }
            }
            return
        } else if (data.size >= 4 && data[0] == Eversense365Packets.NotificationResponseId && data[1] == 0x03.toByte()) {
            // Push alarm notification
            val alarmCode = data[3].toInt() and 0xFF
            val alarm = com.nightscout.eversense.models.ActiveAlarm(
                code = com.nightscout.eversense.enums.EversenseAlarm.from(alarmCode),
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

            if (EversenseE3Packets.isErrorPacket(data[0])) {
                EversenseLogger.error(TAG, "Received error response - data: ${data.toHexString()}")
                packet.notifyAll()
                return
            }

            if (security == EversenseSecurityType.None) {
                if (packetAnnotation.responseId != data[0]) {
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
    fun <T : EversenseBasePacket.Response> writePacket(packet: EversenseBasePacket): T {
        val gatt = bluetoothGatt ?: throw EversenseWriteException("Gatt is null — not connected")

        val requestCharacteristic = requestCharacteristic
            ?: throw EversenseWriteException("requestCharacteristic is null")

        val requestData = packet.buildRequest(cryptoUtil, payloadSize)
            ?: throw EversenseWriteException("Failed to build request data")

        // FIX 2: Use AtomicReference.set() for thread-safe assignment of currentPacket.
        currentPacket.set(packet)

        EversenseLogger.debug(TAG, "Writing data: ${requestData.toHexString()}")
        requestCharacteristic.setValue(requestData)
        gatt.writeCharacteristic(requestCharacteristic)

        synchronized(packet) {
            try {
                // FIX 8: Explicitly detect timeout by comparing elapsed time after wait() returns.
                // Previously, a timeout would fall through to parseResponse() silently, likely
                // producing a confusing cast exception rather than a clear timeout error.
                val start = System.currentTimeMillis()
                packet.wait(WRITE_TIMEOUT_MS)
                val elapsed = System.currentTimeMillis() - start
                if (elapsed >= WRITE_TIMEOUT_MS) {
                    currentPacket.set(null)
                    throw EversenseWriteException("Timed out waiting for response after ${WRITE_TIMEOUT_MS}ms — packet: ${packet.getAnnotation()?.responseId}")
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

        EversenseLogger.info(TAG, "E3 auth complete — ready for full sync")
        EversenseE3Communicator.fullSync(this, preferences, plugin.watchers)
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

            EversenseLogger.info(TAG, "365 auth complete — ready for full sync")
            Eversense365Communicator.fullSync(this, preferences, plugin.watchers)

        } catch (exception: Exception) {
            EversenseLogger.error(TAG, "[365] authV2 failed: $exception")
            exception.printStackTrace()
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