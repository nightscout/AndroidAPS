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
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.exceptions.EversenseWriteException
import com.nightscout.eversense.packets.EversenseBasePacket
import com.nightscout.eversense.packets.EversenseE3Communicator
import com.nightscout.eversense.packets.e3.EversenseE3Packets
import com.nightscout.eversense.packets.e3.SaveBondingInformationPacket
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.jvm.Throws
import androidx.core.content.edit
import com.nightscout.eversense.packets.Eversense365Communicator
import com.nightscout.eversense.packets.e365.AuthIdentityPacket
import com.nightscout.eversense.packets.e365.AuthStartPacket
import com.nightscout.eversense.packets.e365.AuthWhoAmIPacket
import com.nightscout.eversense.packets.e365.Eversense365Packets
import com.nightscout.eversense.packets.e365.KeepAlivePacket
import com.nightscout.eversense.util.EversenseCrypto365Util
import com.nightscout.eversense.util.EversenseHttp365Util
import com.nightscout.eversense.util.EversenseLogger
import com.nightscout.eversense.util.StorageKeys
import java.util.concurrent.TimeUnit

class EversenseGattCallback(
    private val plugin: EversenseCGMPlugin,
    private val preferences: SharedPreferences
) : BluetoothGattCallback() {

    companion object {
        private val TAG = "EversenseGattCallback"

        const val serviceUUID = "c3230001-9308-47ae-ac12-3d030892a211"

        private const val requestUUID = "6eb0f021-a7ba-7e7d-66c9-6d813f01d273"
        private const val requestSecureV2UUID = "c3230002-9308-47ae-ac12-3d030892a211"

        private const val responseUUID = "6eb0f024-bd60-7aaa-25a7-0029573f4f23"
        private const val responseSecureV2UUID = "c3230003-9308-47ae-ac12-3d030892a211"
        private const val magicDescriptorUUID = "00002902-0000-1000-8000-00805f9b34fb"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var bluetoothGatt: BluetoothGatt? = null
    private var eversenseBluetoothService: BluetoothGattService? = null
    private var requestCharacteristic: BluetoothGattCharacteristic? = null
    private var responseCharacteristic: BluetoothGattCharacteristic? = null

    private var payloadSize: Int = 20
    private var security: EversenseSecurityType = EversenseSecurityType.None
    private var cryptoUtil = EversenseCrypto365Util(preferences)
    var currentPacket: EversenseBasePacket? = null

    fun isConnected(): Boolean {
        return this.bluetoothGatt != null
    }

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        EversenseLogger.info(TAG, "Connection state changed - state: $status, newState: $newState, ${gatt.device.name}")

        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            bluetoothGatt = gatt

            preferences.edit(commit = true) {
                putString(StorageKeys.REMOTE_DEVICE_KEY, gatt.device.address)
            }

            handler.post {
                plugin.watchers.forEach {
                    it.onConnectionChanged(true)
                }
            }

            if (!gatt.requestMtu(512)) {
                EversenseLogger.error(TAG, "Failed to request MTU")
            }
            return
        }

        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            EversenseLogger.warning(TAG, "Disconnected...")
            bluetoothGatt = null

            for (watcher in plugin.watchers) {
                watcher.onConnectionChanged(false)
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
            EversenseLogger.error(TAG, "Gatt is empty")
            return
        }

        val service = gatt.services.first { it.uuid.toString() == serviceUUID }
        if (service == null) {
            EversenseLogger.error(TAG, "Service is empty -> disconnecting from device")
            gatt.disconnect()
            return
        }

        eversenseBluetoothService = service
        if (service.characteristics.isEmpty()) {
            EversenseLogger.error(TAG, "Service has no characteristics -> disconnecting from device")
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
            EversenseLogger.error(TAG, "No Eversense request/response characteristic found -> Disconnect from device...")
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
        EversenseLogger.debug(TAG, "onDescriptor (${descriptor.uuid}) write for characteristic (${descriptor.characteristic.uuid}) - status $status")

        if (status == BluetoothGatt.GATT_SUCCESS && descriptor.uuid.toString() == magicDescriptorUUID) {
            if (descriptor.characteristic.uuid.toString() == responseUUID) {
                executor.submit { authE3flow() }
            } else if (descriptor.characteristic.uuid.toString() == responseSecureV2UUID) {
                executor.submit { authV2flow() }
            }
        }
    }

    @Deprecated("")
    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalStdlibApi::class)
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        EversenseLogger.debug(TAG, "Received data: ${characteristic.value.toHexString()}")

        var data = characteristic.value
        if (security == EversenseSecurityType.SecureV2) {
            data = data.drop(3).toByteArray()

            if (data[0] != Eversense365Packets.AuthenticateResponseId) {
                data = cryptoUtil.decrypt(data)
                EversenseLogger.debug(TAG, "Decrypted data -> ${data.toHexString()}")
                if (data.isEmpty()) {
                    EversenseLogger.error(TAG, "Failed do decrypt data...")

                    // Delete keys to redo security handshake on next connection
                    cryptoUtil.disallowUseShortcut()
                    gatt.disconnect()
                    return
                }
            }
        }

        if (EversenseE3Packets.isPushPacket(data[0])) {
            EversenseLogger.debug(TAG, "Keep Alive packet received!")
            executor.submit {
                EversenseE3Communicator.readGlucose(this, preferences, plugin.watchers)
                EversenseE3Communicator.fullSync(this, preferences, plugin.watchers)
            }
            return
        }

        if (Eversense365Packets.isKeepAlivePacket(data[0], data[1])) {
            EversenseLogger.debug(TAG, "Keep Alive packet received!")

            val packet = KeepAlivePacket()
            packet.appendData(data.toUByteArray())
            val response = packet.parseResponse() ?: return

            val fourHalfMinAgo = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(270)
            if (response.glucoseDatetime > fourHalfMinAgo) {
                executor.submit {
                    Eversense365Communicator.readGlucose(this, preferences, plugin.watchers)
                    Eversense365Communicator.fullSync(this, preferences, plugin.watchers)
                }
            }

            return
        } else if (Eversense365Packets.isNotificationPacket(data[0])) {
            EversenseLogger.warning(TAG, "Unknown notification packet received...")
            return
        }

        val packet = currentPacket ?:run {
            EversenseLogger.warning(TAG, "currentPacket is empty -> Skip packet...")
            return
        }

        synchronized(packet) {
            val packetAnnotation = packet.getAnnotation() ?:run {
                EversenseLogger.warning(TAG, "annotation is empty -> Skip packet...")
                return
            }

            if (EversenseE3Packets.isErrorPacket(data[0])) {
                EversenseLogger.error(TAG, "Received error response - data: ${data.toHexString()}")
                packet.notifyAll()
                return
            }

            if (security == EversenseSecurityType.None) {
                if (packetAnnotation.responseId != data[0]) {
                    EversenseLogger.warning(TAG, "Incorrect responseId received - Expected: ${packetAnnotation.responseId}, got: ${data[0]}")
                    return
                }

                packet.appendData(data.toUByteArray())
                packet.notifyAll()
            } else {
                if (packetAnnotation.responseId != data[0]) {
                    EversenseLogger.warning(TAG, "Incorrect responseId received - Expected: ${packetAnnotation.responseId}, got: ${data[0]}")
                    return
                }

                if (packetAnnotation.typeId != data[1]) {
                    EversenseLogger.warning(TAG, "Incorrect responseType received - Expected: ${packetAnnotation.typeId}, got: ${data[1]}")
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
    fun<T: EversenseBasePacket.Response>writePacket(packet: EversenseBasePacket): T {
        val gatt = bluetoothGatt ?:run {
            throw EversenseWriteException("Gatt is empty")
        }

        val requestCharacteristic = requestCharacteristic ?:run {
            throw EversenseWriteException("requestCharacteristic is empty")
        }

        val requestData = packet.buildRequest(cryptoUtil, payloadSize) ?:run {
            throw EversenseWriteException("Failed to build request data...")
        }

        currentPacket = packet

        EversenseLogger.debug(TAG, "Writing data: ${requestData.toHexString()}")

        requestCharacteristic.setValue(requestData)
        gatt.writeCharacteristic(requestCharacteristic)

        synchronized(packet) {
            try {
                packet.wait(5000)
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "Exception during await - exception: $e")
                e.printStackTrace()
            }
        }

        return try {
            val response = packet.parseResponse()
            currentPacket = null
            response as? T ?: throw EversenseWriteException("Unable to cast response - packet: ${packet.getAnnotation()?.responseId}")
        } catch(e: Exception) {
            throw EversenseWriteException("Failed to parse response - exception: $e")
        }
    }

    private fun authE3flow() {
        EversenseLogger.info(TAG, "Starting auth flow E3...")

        try {
            writePacket<SaveBondingInformationPacket.SaveBondingInformationResponse>(SaveBondingInformationPacket())
        } catch (exception: Exception) {
            EversenseLogger.error(TAG, "Failed to do auth flow - exception: $exception")
            return
        }

        EversenseLogger.info(TAG, "Ready for full sync!!")
        EversenseE3Communicator.fullSync(this, preferences, plugin.watchers)
    }

    @SuppressLint("MissingPermission")
    private fun authV2flow() {
        try {
            if (!cryptoUtil.generateKeyPairIfNotExists()) {
                bluetoothGatt?.disconnect()
                return
            }

            if (!cryptoUtil.canUseShortcut()) {
                val clientId = cryptoUtil.getClientId()
                val whoAmI = writePacket<AuthWhoAmIPacket.Response>(AuthWhoAmIPacket(clientId))

                val authSession = EversenseHttp365Util.login(preferences) ?:run {
                    bluetoothGatt?.disconnect()
                    return
                }

                val fleet = EversenseHttp365Util.getFleetSecretV2(
                    accessToken = authSession.access_token,
                    serialNumber = whoAmI.serialNumber,
                    nonce = whoAmI.nonce,
                    flags = whoAmI.flags,
                    publicKey = cryptoUtil.getClientPublicKey()
                ) ?:run {
                    bluetoothGatt?.disconnect()
                    return
                }

                writePacket<AuthIdentityPacket.Response>(
                    AuthIdentityPacket(fleet.Result.Certificate?.hexToByteArray() ?: byteArrayOf())
                )

                cryptoUtil.allowUseShortcut()
            }

            val signature = cryptoUtil.generateEphem() ?:run {
                bluetoothGatt?.disconnect()
                return
            }

            val session = writePacket<AuthStartPacket.Response>(AuthStartPacket(cryptoUtil.getStartSecret(signature)))
            cryptoUtil.generateSessionKey(session.sessionPublicKey)

            EversenseLogger.info(TAG, "Ready for full sync!!")
            Eversense365Communicator.fullSync(this, preferences, plugin.watchers)
        } catch(exception: Exception) {
            EversenseLogger.error(TAG, "[365] Failed to do authV2 - $exception")
            exception.printStackTrace()
            bluetoothGatt?.disconnect()
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun enableNotify(gatt: BluetoothGatt, responseCharacteristic: BluetoothGattCharacteristic) {
        responseCharacteristic.getDescriptor(UUID.fromString(magicDescriptorUUID))?.let {
            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(it)
        }
    }
}