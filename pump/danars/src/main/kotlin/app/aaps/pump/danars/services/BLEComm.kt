package app.aaps.pump.danars.services

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import androidx.core.app.ActivityCompat
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.PairingStep
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.extensions.scanForActivity
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.notifyAll
import app.aaps.core.utils.waitMillis
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.R
import app.aaps.pump.dana.keys.DanaLongKey
import app.aaps.pump.dana.keys.DanaStringComposedKey
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.comm.DanaRSMessageHashTable
import app.aaps.pump.danars.comm.DanaRSPacket
import app.aaps.pump.danars.comm.DanaRSPacketEtcKeepConnection
import app.aaps.pump.danars.encryption.BleEncryption
import app.aaps.pump.danars.encryption.EncryptionType
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ScheduledFuture
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BLEComm @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val context: Context,
    private val rxBus: RxBus,
    private val danaRSMessageHashTable: DanaRSMessageHashTable,
    private val danaPump: DanaPump,
    private val danaRSPlugin: DanaRSPlugin,
    private val bleEncryption: BleEncryption,
    private val pumpSync: PumpSync,
    private val dateUtil: DateUtil,
    private val preferences: Preferences,
    private val configBuilder: ConfigBuilder,
    private val notificationManager: NotificationManager,
    private val bleTransport: BleTransport
) : BleTransportListener {

    companion object {

        private const val PACKET_START_BYTE = 0xA5.toByte()
        private const val PACKET_END_BYTE = 0x5A.toByte()
        private const val BLE5_PACKET_START_BYTE = 0xAA.toByte()
        private const val BLE5_PACKET_END_BYTE = 0xEE.toByte()
    }

    private var scheduledDisconnection: ScheduledFuture<*>? = null
    private var processedMessage: DanaRSPacket? = null
    private val mSendQueue = ArrayList<ByteArray>()
    private var connectDeviceName: String? = null
    private var connectAddress: String? = null

    private var encryption: EncryptionType = EncryptionType.ENCRYPTION_DEFAULT
        set(newValue) {
            bleEncryption.setEnhancedEncryption(newValue)
            field = newValue
        }
    private var isEasyMode: Boolean = false
    private var isUnitUD: Boolean = false

    @Volatile var isConnected = false
    var isConnecting = false
    /** Timeout for waiting for pump reply in [sendMessage]. Default 5 s; tests may lower this. */
    var messageTimeoutMs: Long = 5000L
    private var encryptedDataRead = false
    private var encryptedCommandSent = false
    private var pumpCheckSent = false  // Guard against duplicate ENCRYPTION__PUMP_CHECK

    init {
        bleTransport.setListener(this)
    }

    @Synchronized
    fun connect(from: String, address: String?): Boolean {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ToastUtils.errorToast(context, context.getString(app.aaps.core.ui.R.string.need_connect_permission))
            aapsLogger.error(LTag.PUMPBTCOMM, "missing permission: $from")
            return false
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "Initializing BLEComm.")

        if (address == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "unspecified address.")
            return false
        }

        val deviceName = bleTransport.adapter.getDeviceName(address)
        if (deviceName == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Device not found.  Unable to connect from: $from")
            return false
        }

        if (!bleTransport.adapter.isDeviceBonded(address)) {
            bleTransport.adapter.createBond(address)
            aapsLogger.debug(LTag.PUMPBTCOMM, "Bond requested, will retry after bonding completes from: $from")
            return false
        }

        isConnected = false
        encryption = EncryptionType.ENCRYPTION_DEFAULT
        encryptedDataRead = false
        encryptedCommandSent = false
        pumpCheckSent = false  // Reset the guard flag for new connection
        isConnecting = true
        bufferLength = 0
        bleTransport.updatePairingState(PairingState(step = PairingStep.CONNECTING))
        aapsLogger.debug(LTag.PUMPBTCOMM, "Trying to create a new connection from: $from")
        connectDeviceName = deviceName
        connectAddress = address

        if (!bleTransport.gatt.connect(address)) {
            aapsLogger.error(LTag.PUMPBTCOMM, "connectGatt failed from: $from")
            isConnecting = false
            return false
        }
        // Register for notifications early (before connection completes) to avoid
        // race condition where fast/cached connections complete before notification
        // registration in onServicesDiscovered path.
        bleTransport.gatt.enableNotifications()
        return true
    }

    @Synchronized
    fun stopConnecting() {
        isConnecting = false
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun disconnect(from: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            aapsLogger.error(LTag.PUMPBTCOMM, "missing permission: $from")
            return
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "disconnect from: $from")

        if (!encryptedDataRead && encryptedCommandSent && encryption == EncryptionType.ENCRYPTION_BLE5) {
            // there was no response from pump after started encryption
            // assume pairing keys are invalid
            val lastClearRequest = preferences.get(DanaLongKey.LastClearKeyRequest)
            if (lastClearRequest != 0L && dateUtil.isOlderThan(lastClearRequest, 5)) {
                ToastUtils.errorToast(context, R.string.invalidpairing)
                danaRSPlugin.changePump()
                connectAddress?.let { bleTransport.adapter.removeBond(it) }
            } else if (lastClearRequest == 0L) {
                aapsLogger.error(LTag.PUMPBTCOMM, "Clearing pairing keys postponed")
                preferences.put(DanaLongKey.LastClearKeyRequest, dateUtil.now())
            }
        }
        if (!encryptedDataRead && encryptedCommandSent && encryption == EncryptionType.ENCRYPTION_RSv3) {
            // there was no response from pump after started encryption
            // assume pairing keys are invalid
            val lastClearRequest = preferences.get(DanaLongKey.LastClearKeyRequest)
            if (lastClearRequest != 0L && dateUtil.isOlderThan(lastClearRequest, 5)) {
                /*
                aapsLogger.error(LTag.PUMPBTCOMM, "Clearing pairing keys !!!")
                preferences.remove(DanaStringComposedKey.V3RandomParingKey, danaRSPlugin.mDeviceName)
                preferences.remove(DanaStringComposedKey.V3ParingKey, danaRSPlugin.mDeviceName)
                preferences.remove(DanaStringComposedKey.V3RandomSyncKey, danaRSPlugin.mDeviceName)
                ToastUtils.errorToast(context, R.string.invalidpairing)
                danaRSPlugin.changePump()
                */
                // Behavior change - Try to restart app
                context.scanForActivity()?.finish()
                configBuilder.exitApp("Dana BLE encryption failed", Sources.Maintenance, true)
            } else if (lastClearRequest == 0L) {
                aapsLogger.error(LTag.PUMPBTCOMM, "Clearing pairing keys postponed")
                preferences.put(DanaLongKey.LastClearKeyRequest, dateUtil.now())
            }
        }
        // cancel previous scheduled disconnection to prevent closing upcoming connection
        scheduledDisconnection?.cancel(false)
        scheduledDisconnection = null

        synchronized(mSendQueue) { mSendQueue.clear() }
        bleTransport.gatt.disconnect()
        isConnected = false
        encryptedDataRead = false
        encryptedCommandSent = false
        pumpCheckSent = false  // Reset for next connection attempt
    }

    @SuppressLint("MissingPermission")
    @Synchronized fun close() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "BluetoothAdapter close")
        bleTransport.gatt.close()
    }

    // BleTransportListener callbacks

    override fun onConnectionStateChanged(connected: Boolean) {
        onConnectionStateChangeSynchronized(connected)
    }

    override fun onServicesDiscovered(success: Boolean) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "onServicesDiscovered")
        if (success) {
            findCharacteristic()
        }
    }

    override fun onCharacteristicChanged(data: ByteArray) {
        readDataParsing(data)
    }

    override fun onCharacteristicWritten() {
        Thread {
            synchronized(mSendQueue) {
                // after message sent, check if there is the rest of the message waiting and send it
                if (mSendQueue.isNotEmpty()) {
                    val bytes = mSendQueue[0]
                    mSendQueue.removeAt(0)
                    bleTransport.gatt.writeCharacteristic(bytes)
                }
            }
        }.start()
    }

    override fun onDescriptorWritten() {
        if (isConnected) return // Already connected, ignore duplicate notification enable
        bleTransport.updatePairingState(PairingState(step = PairingStep.HANDSHAKE_IN_PROGRESS))
        sendConnect()
        // 1st message sent to pump after connect
    }

    @Synchronized
    private fun onConnectionStateChangeSynchronized(connected: Boolean) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "onConnectionStateChange")
        if (connected) {
            bleTransport.gatt.discoverServices()
        } else {
            close()
            isConnected = false
            isConnecting = false
            encryption = EncryptionType.ENCRYPTION_DEFAULT
            encryptedDataRead = false
            encryptedCommandSent = false
            pumpCheckSent = false  // Reset for next connection attempt
            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
            bleTransport.updatePairingState(PairingState(step = PairingStep.IDLE))
            aapsLogger.debug(LTag.PUMPBTCOMM, "Device was disconnected")
        }
    }

    private fun findCharacteristic() {
        if (bleTransport.gatt.findCharacteristics()) {
            bleTransport.gatt.enableNotifications()
        }
    }

    private val readBuffer = ByteArray(1024)
    @Volatile private var bufferLength = 0

    private fun addToReadBuffer(buffer: ByteArray) {
        //log.debug("addToReadBuffer " + DanaRS_Packet.toHexString(buffer));
        if (buffer.isEmpty()) return

        synchronized(readBuffer) {
            // Append incoming data to input buffer
            System.arraycopy(buffer, 0, readBuffer, bufferLength, buffer.size)
            bufferLength += buffer.size
        }
    }

    private fun readDataParsing(receivedData: ByteArray) {
        //aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< readDataParsing " + DanaRS_Packet.toHexString(receivedData))
        var packetIsValid = false
        var isProcessing: Boolean
        isProcessing = true
        var inputBuffer: ByteArray?

        // decrypt 2nd level after successful connection
        val incomingBuffer =
            if (isConnected && (encryption == EncryptionType.ENCRYPTION_RSv3 || encryption == EncryptionType.ENCRYPTION_BLE5))
                bleEncryption.decryptSecondLevelPacket(receivedData).also {
                    encryptedDataRead = true
                    preferences.put(DanaLongKey.LastClearKeyRequest, 0L)
                }
            else receivedData
        addToReadBuffer(incomingBuffer)
        //aapsLogger.debug(LTag.PUMPBTCOMM, "incomingBuffer " + DanaRS_Packet.toHexString(incomingBuffer))

        while (isProcessing) {
            var length = 0
            synchronized(readBuffer) {
                // Find packet start [A5 A5] or [AA AA]
                if (bufferLength >= 6) {
                    for (idxStartByte in 0 until bufferLength - 2) {
                        if (readBuffer[idxStartByte] == PACKET_START_BYTE && readBuffer[idxStartByte + 1] == PACKET_START_BYTE ||
                            readBuffer[idxStartByte] == BLE5_PACKET_START_BYTE && readBuffer[idxStartByte + 1] == BLE5_PACKET_START_BYTE
                        ) {
                            if (idxStartByte > 0) {
                                // if buffer doesn't start with signature remove the leading trash
                                aapsLogger.debug(LTag.PUMPBTCOMM, "Shifting the input buffer by $idxStartByte bytes")
                                System.arraycopy(readBuffer, idxStartByte, readBuffer, 0, bufferLength - idxStartByte)
                                bufferLength -= idxStartByte
                                if (bufferLength < 0) bufferLength = 0
                            }
                            // A5 A5 LEN TYPE CODE PARAMS CHECKSUM1 CHECKSUM2 5A 5A    or
                            // AA AA LEN TYPE CODE PARAMS CHECKSUM1 CHECKSUM2 EE EE
                            //           ^---- LEN -----^
                            // total packet length 2 + 1 + readBuffer[2] + 2 + 2
                            length = readBuffer[2].toInt()
                            // test if there is enough data loaded
                            if (length + 7 > bufferLength)
                                return
                            // Verify packed end [5A 5A]
                            if (readBuffer[length + 5] == PACKET_END_BYTE && readBuffer[length + 6] == PACKET_END_BYTE ||
                                readBuffer[length + 5] == BLE5_PACKET_END_BYTE && readBuffer[length + 6] == BLE5_PACKET_END_BYTE
                            ) {
                                packetIsValid = true
                            } else {
                                aapsLogger.error(LTag.PUMPBTCOMM, "Error in input data. Resetting buffer.")
                                bufferLength = 0
                            }
                            break
                        }
                        break
                    }
                }
            }
            if (packetIsValid) {
                inputBuffer = ByteArray(length + 7)
                // copy packet to input buffer
                System.arraycopy(readBuffer, 0, inputBuffer, 0, length + 7)
                // Cut off the message from readBuffer
                try {
                    System.arraycopy(readBuffer, length + 7, readBuffer, 0, bufferLength - (length + 7))
                } catch (e: Exception) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "length: " + length + "bufferLength: " + bufferLength)
                    throw e
                }
                bufferLength -= length + 7
                // now we have encrypted packet in inputBuffer

                // decrypt the packet
                val decrypted = bleEncryption.getDecryptedPacket(inputBuffer)
                decrypted?.let { decryptedBuffer ->
                    if (decryptedBuffer[0] == BleEncryption.DANAR_PACKET__TYPE_ENCRYPTION_RESPONSE.toByte()) {
                        when (decryptedBuffer[1]) {
                            // 1st packet exchange
                            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK.toByte()          ->
                                processConnectResponse(decryptedBuffer)

                            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION.toByte()    ->
                                processEncryptionResponse(decryptedBuffer)

                            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY.toByte()       ->
                                processPasskeyCheck(decryptedBuffer)

                            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST.toByte()     ->
                                processPairingRequest(decryptedBuffer)

                            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_RETURN.toByte()      ->
                                processPairingRequest2(decryptedBuffer)

                            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__GET_PUMP_CHECK.toByte()      -> {
                                // not easy mode, request time info
                                if (decryptedBuffer[2] == 0x05.toByte()) sendTimeInfo()
                                // easy mode
                                else sendEasyMenuCheck()
                            }

                            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__GET_EASY_MENU_CHECK.toByte() ->
                                processEasyMenuCheck(decryptedBuffer)
                        }

                    } else {
                        // Retrieve message code from received buffer and last message sent
                        processMessage(decryptedBuffer)
                    }
                }
                checkNotNull(decrypted) { "Null decryptedInputBuffer" }
                packetIsValid = false
                if (bufferLength < 6) {
                    // stop the loop
                    isProcessing = false
                }
            } else {
                // stop the loop
                isProcessing = false
            }
        }
    }

    // 1st packet v1 v3 message sent to pump after connect
    @Synchronized
    private fun sendConnect() {
        // Guard against duplicate ENCRYPTION__PUMP_CHECK packets during rapid reconnection
        if (pumpCheckSent) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "ENCRYPTION__PUMP_CHECK already sent for this connection, ignoring duplicate call")
            return
        }

        val deviceName = connectDeviceName
        if (deviceName == null || deviceName == "") {
            notificationManager.post(NotificationId.DEVICE_NOT_PAIRED, R.string.pairfirst)
            return
        }

        pumpCheckSent = true  // Mark that we've sent the PUMP_CHECK for this connection attempt
        val bytes = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK, null, deviceName)
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + "ENCRYPTION__PUMP_CHECK (0x00)" + " " + DanaRSPacket.toHexString(bytes))
        bleTransport.gatt.writeCharacteristic(bytes)
    }

    // 1st packet response
    private fun processConnectResponse(decryptedBuffer: ByteArray) {
        // response OK v1
        if (decryptedBuffer.size == 4 && decryptedBuffer[2] == 'O'.code.toByte() && decryptedBuffer[3] == 'K'.code.toByte()) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PUMP_CHECK (OK)" + " " + DanaRSPacket.toHexString(decryptedBuffer))
            encryption = EncryptionType.ENCRYPTION_DEFAULT
            danaPump.ignoreUserPassword = false
            // Grab pairing key from preferences if exists
            val pairingKey = preferences.get(DanaStringComposedKey.ParingKey, danaRSPlugin.mDeviceName)
            aapsLogger.debug(LTag.PUMPBTCOMM, "Using stored pairing key: $pairingKey")
            if (pairingKey.isNotEmpty()) {
                sendPasskeyCheck(pairingKey)
            } else {
                // Stored pairing key does not exist, request pairing
                sendPairingRequest()
            }
            // response OK v3
        } else if (decryptedBuffer.size == 9 && decryptedBuffer[2] == 'O'.code.toByte() && decryptedBuffer[3] == 'K'.code.toByte()) {
            // v3 2nd layer encryption
            encryption = EncryptionType.ENCRYPTION_RSv3
            danaPump.ignoreUserPassword = true
            danaPump.hwModel = decryptedBuffer[5].toInt()
            danaPump.protocol = decryptedBuffer[7].toInt()
            // grab randomSyncKey
            preferences.put(DanaStringComposedKey.V3RandomSyncKey, danaRSPlugin.mDeviceName, value = String.format("%02x", decryptedBuffer[decryptedBuffer.size - 1]))

            if (danaPump.hwModel == 0x05) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PUMP_CHECK V3 (OK)" + " " + DanaRSPacket.toHexString(decryptedBuffer))
                // Dana RS Pump
                sendV3PairingInformation()
            } else if (danaPump.hwModel == 0x06) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PUMP_CHECK V3 EASY (OK)" + " " + DanaRSPacket.toHexString(decryptedBuffer))
                // Dana RS Easy
                sendEasyMenuCheck()
            }
            // response OK BLE5
        } else if (decryptedBuffer.size == 14 && decryptedBuffer[2] == 'O'.code.toByte() && decryptedBuffer[3] == 'K'.code.toByte()) {
            // v3 2nd layer encryption
            encryption = EncryptionType.ENCRYPTION_BLE5
            danaPump.ignoreUserPassword = true
            danaPump.hwModel = decryptedBuffer[5].toInt()
            danaPump.protocol = decryptedBuffer[7].toInt()
            val pairingKey = DanaRSPacket.asciiStringFromBuff(decryptedBuffer, 8, 6) // used while bonding
            if (decryptedBuffer[8] != 0.toByte())
                preferences.put(DanaStringComposedKey.Ble5PairingKey, danaRSPlugin.mDeviceName, value = pairingKey)

            val storedPairingKey = preferences.get(DanaStringComposedKey.Ble5PairingKey, danaRSPlugin.mDeviceName)
            if (storedPairingKey.isBlank()) {
                connectAddress?.let { bleTransport.adapter.removeBond(it) }
                disconnect("Non existing pairing key")
                return
            }

            if (danaPump.hwModel == 0x09 || danaPump.hwModel == 0x0A) {
                bleEncryption.setBle5Key(storedPairingKey.encodeToByteArray())
                aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PUMP_CHECK BLE5 (OK)" + " " + DanaRSPacket.toHexString(decryptedBuffer))
                // Dana-i BLE5 Pump
                sendBLE5PairingInformation()
            }
            // response PUMP : error status
        } else if (decryptedBuffer.size == 6 && decryptedBuffer[2] == 'P'.code.toByte() && decryptedBuffer[3] == 'U'.code.toByte() && decryptedBuffer[4] == 'M'.code.toByte() && decryptedBuffer[5] == 'P'.code.toByte()) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PUMP_CHECK (PUMP)" + " " + DanaRSPacket.toHexString(decryptedBuffer))
            bleTransport.updatePairingState(PairingState(step = PairingStep.ERROR, errorMessage = rh.gs(R.string.pumperror)))
            mSendQueue.clear()
            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED, rh.gs(R.string.pumperror)))
            runBlocking { pumpSync.insertAnnouncement(rh.gs(R.string.pumperror), null, danaPump.pumpType(), danaPump.serialNumber) }
            notificationManager.post(NotificationId.PUMP_ERROR, R.string.pumperror)
            // response BUSY: error status
        } else if (decryptedBuffer.size == 6 && decryptedBuffer[2] == 'B'.code.toByte() && decryptedBuffer[3] == 'U'.code.toByte() && decryptedBuffer[4] == 'S'.code.toByte() && decryptedBuffer[5] == 'Y'.code.toByte()) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PUMP_CHECK (BUSY)" + " " + DanaRSPacket.toHexString(decryptedBuffer))
            bleTransport.updatePairingState(PairingState(step = PairingStep.ERROR, errorMessage = rh.gs(app.aaps.core.ui.R.string.pump_busy)))
            mSendQueue.clear()
            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED, rh.gs(app.aaps.core.ui.R.string.pump_busy)))
        } else {
            // ERROR in response, wrong serial number
            aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PUMP_CHECK (ERROR)" + " " + DanaRSPacket.toHexString(decryptedBuffer))
            bleTransport.updatePairingState(PairingState(step = PairingStep.ERROR, errorMessage = rh.gs(app.aaps.core.ui.R.string.connection_error)))
            mSendQueue.clear()
            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED, rh.gs(app.aaps.core.ui.R.string.connection_error)))
            danaRSPlugin.clearPairing()
            notificationManager.post(NotificationId.WRONG_SERIAL_NUMBER, app.aaps.core.ui.R.string.password_cleared)
        }
    }

    // 2nd packet v1 check passkey
    private fun sendPasskeyCheck(pairingKey: String) {
        val encodedPairingKey = DanaRSPacket.hexToBytes(pairingKey)
        val bytes = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY, encodedPairingKey, null)
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + "ENCRYPTION__CHECK_PASSKEY" + " " + DanaRSPacket.toHexString(bytes))
        bleTransport.gatt.writeCharacteristic(bytes)
    }

    // 2nd packet v1 response
    private fun processPasskeyCheck(decryptedBuffer: ByteArray) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__CHECK_PASSKEY" + " " + DanaRSPacket.toHexString(decryptedBuffer))
        // Paring is not requested, sending time info
        if (decryptedBuffer[2] == 0x00.toByte()) sendTimeInfo()
        // Pairing on pump is requested
        else sendPairingRequest()
    }

    // 2nd packet v3
    // 0x00 Start encryption, 0x01 Request pairing
    private fun sendV3PairingInformation() {
        val randomPairingKey = preferences.get(DanaStringComposedKey.V3RandomParingKey, danaRSPlugin.mDeviceName)
        val pairingKey = preferences.get(DanaStringComposedKey.V3ParingKey, danaRSPlugin.mDeviceName)
        if (randomPairingKey.isNotEmpty() && pairingKey.isNotEmpty()) {
            val tPairingKey = Base64.decode(pairingKey, Base64.DEFAULT)
            val tRandomPairingKey = Base64.decode(randomPairingKey, Base64.DEFAULT)
            var tRandomSyncKey: Byte = 0
            val randomSyncKey = preferences.get(DanaStringComposedKey.V3RandomSyncKey, danaRSPlugin.mDeviceName)
            if (randomSyncKey.isNotEmpty()) {
                tRandomSyncKey = randomSyncKey.toInt(16).toByte()
            }
            bleEncryption.setPairingKeys(tPairingKey, tRandomPairingKey, tRandomSyncKey)
            sendV3PairingInformation(0)
        } else {
            sendV3PairingInformation(1)
        }
    }

    // 2nd packet BLE5
    private fun sendBLE5PairingInformation() {
        val params = ByteArray(4)
        val bytes: ByteArray = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION, params, null)
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + "ENCRYPTION__TIME_INFORMATION BLE5" + " " + DanaRSPacket.toHexString(bytes))
        bleTransport.gatt.writeCharacteristic(bytes)
    }

    private fun sendV3PairingInformation(requestNewPairing: Int) {
        val params = byteArrayOf(requestNewPairing.toByte())
        val bytes: ByteArray = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION, params, null)
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + "ENCRYPTION__TIME_INFORMATION" + " " + DanaRSPacket.toHexString(bytes))
        bleTransport.gatt.writeCharacteristic(bytes)
    }

    // 2nd packet response
    private fun processEncryptionResponse(decryptedBuffer: ByteArray) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__TIME_INFORMATION " + DanaRSPacket.toHexString(decryptedBuffer))
        if (encryption == EncryptionType.ENCRYPTION_BLE5) {
            isConnected = true
            isConnecting = false
            bleTransport.updatePairingState(PairingState(step = PairingStep.CONNECTED))
            aapsLogger.debug(LTag.PUMPBTCOMM, "Connect !!")
        } else if (encryption == EncryptionType.ENCRYPTION_RSv3) {
            // decryptedBuffer[2] : 0x00 OK  0x01 Error, No pairing
            if (decryptedBuffer[2] == 0x00.toByte()) {
                val randomPairingKey = preferences.get(DanaStringComposedKey.V3RandomParingKey, danaRSPlugin.mDeviceName)
                val pairingKey = preferences.get(DanaStringComposedKey.V3ParingKey, danaRSPlugin.mDeviceName)
                if (randomPairingKey.isNotEmpty() && pairingKey.isNotEmpty()) {
                    // expecting successful connect
                    isConnected = true
                    isConnecting = false
                    bleTransport.updatePairingState(PairingState(step = PairingStep.CONNECTED))
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Connect !!")
                    // Send one message to confirm communication
                } else {
                    bleTransport.updatePairingState(PairingState(step = PairingStep.WAITING_FOR_PIN))
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Request pairing keys !!")
                }
            } else {
                sendV3PairingInformation(1)
            }

        } else {
            val size = decryptedBuffer.size
            var pass: Int = (decryptedBuffer[size - 1].toInt() and 0x000000FF shl 8) + (decryptedBuffer[size - 2].toInt() and 0x000000FF)
            pass = pass xor 3463
            danaPump.rsPassword = String.format("%04X", pass)
            aapsLogger.debug(LTag.PUMPBTCOMM, "Pump user password: " + danaPump.rsPassword)
            if (!danaPump.isRSPasswordOK) {
                aapsLogger.error(LTag.PUMPBTCOMM, "Wrong pump password")
                notificationManager.post(NotificationId.WRONG_PUMP_PASSWORD, R.string.wrongpumppassword)
                bleTransport.updatePairingState(PairingState(step = PairingStep.WAITING_FOR_PASSWORD))
                disconnect("WrongPassword")
            } else {
                notificationManager.dismiss(NotificationId.WRONG_PUMP_PASSWORD)
                rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
                isConnected = true
                isConnecting = false
                bleTransport.updatePairingState(PairingState(step = PairingStep.CONNECTED))
                aapsLogger.debug(LTag.PUMPBTCOMM, "RS connected and status read")
            }
        }
    }

    // 3rd packet v1 existing pairing
    private fun sendTimeInfo() {
        val bytes = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION, null, null)
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + "ENCRYPTION__TIME_INFORMATION" + " " + DanaRSPacket.toHexString(bytes))
        bleTransport.gatt.writeCharacteristic(bytes)
    }

    //2nd or 3rd packet v1 pairing doesn't exist
    private fun sendPairingRequest() {
        // On pump pairing request is displayed and is waiting for confirmation
        bleTransport.updatePairingState(PairingState(step = PairingStep.WAITING_FOR_PAIRING_CONFIRM))
        val bytes = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST, null, null)
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + "ENCRYPTION__PASSKEY_REQUEST" + " " + DanaRSPacket.toHexString(bytes))
        bleTransport.gatt.writeCharacteristic(bytes)
    }

    // 3rd packet v3 : only after entering PIN codes
    fun finishV3Pairing() {
        val randomPairingKey = preferences.get(DanaStringComposedKey.V3RandomParingKey, danaRSPlugin.mDeviceName)
        val pairingKey = preferences.get(DanaStringComposedKey.V3ParingKey, danaRSPlugin.mDeviceName)
        if (randomPairingKey.isNotEmpty() && pairingKey.isNotEmpty()) {
            val tPairingKey = Base64.decode(pairingKey, Base64.DEFAULT)
            val tRandomPairingKey = Base64.decode(randomPairingKey, Base64.DEFAULT)
            val tRandomSyncKey: Byte = 0
            bleEncryption.setPairingKeys(tPairingKey, tRandomPairingKey, tRandomSyncKey)
            sendV3PairingInformation(0)
        } else throw java.lang.IllegalStateException("This should not be reached")
    }

    // 2nd or 3rd packet v1 response
    private fun processPairingRequest(decryptedBuffer: ByteArray) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PASSKEY_REQUEST " + DanaRSPacket.toHexString(decryptedBuffer))
        if (decryptedBuffer[2] != 0x00.toByte()) {
            disconnect("passkey request failed")
        }
    }

    // 2nd or 3rd packet v1 response
    private fun processPairingRequest2(decryptedBuffer: ByteArray) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PASSKEY_RETURN " + DanaRSPacket.toHexString(decryptedBuffer))
        // Paring is successful, sending time info
        sendTimeInfo()
        val pairingKey = byteArrayOf(decryptedBuffer[2], decryptedBuffer[3])
        // store pairing key to preferences
        preferences.put(DanaStringComposedKey.ParingKey, danaRSPlugin.mDeviceName, value = DanaRSPacket.bytesToHex(pairingKey))
        aapsLogger.debug(LTag.PUMPBTCOMM, "Got pairing key: " + DanaRSPacket.bytesToHex(pairingKey))
    }

    // 3rd packet Easy menu pump
    private fun sendEasyMenuCheck() {
        val bytes: ByteArray = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__GET_EASY_MENU_CHECK, null, null)
        bleTransport.gatt.writeCharacteristic(bytes)
    }

    // 3rd packet Easy menu response
    private fun processEasyMenuCheck(decryptedBuffer: ByteArray) {
        isEasyMode = decryptedBuffer[2] == 0x01.toByte()
        isUnitUD = decryptedBuffer[3] == 0x01.toByte()

        // request time information
        if (encryption == EncryptionType.ENCRYPTION_RSv3) sendV3PairingInformation()
        else sendTimeInfo()
    }

    // the rest of packets
    fun sendMessage(message: DanaRSPacket) {
        encryptedCommandSent = true
        processedMessage = message
        val command = byteArrayOf(message.type.toByte(), message.opCode.toByte())
        val params = message.getRequestParams()
        if (!isConnected && !isConnecting) {
            aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> IGNORING (NOT CONNECTED) " + message.friendlyName + " " + DanaRSPacket.toHexString(command) + " " + DanaRSPacket.toHexString(params))
            return
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + message.friendlyName + " " + DanaRSPacket.toHexString(command) + " " + DanaRSPacket.toHexString(params))
        var bytes = bleEncryption.getEncryptedPacket(message.opCode, params, null)
        // aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + DanaRS_Packet.toHexString(bytes))
        if (encryption != EncryptionType.ENCRYPTION_DEFAULT)
            bytes = bleEncryption.encryptSecondLevelPacket(bytes)
        // If there is another message not completely sent, add to queue only
        if (mSendQueue.isNotEmpty()) {
            // Split to parts per 20 bytes max
            while (true) {
                if (bytes.size > 20) {
                    val addBytes = ByteArray(20)
                    System.arraycopy(bytes, 0, addBytes, 0, addBytes.size)
                    val reBytes = ByteArray(bytes.size - addBytes.size)
                    System.arraycopy(bytes, addBytes.size, reBytes, 0, reBytes.size)
                    bytes = reBytes
                    synchronized(mSendQueue) { mSendQueue.add(addBytes) }
                } else {
                    synchronized(mSendQueue) { mSendQueue.add(bytes) }
                    break
                }
            }
        } else {
            if (bytes.size > 20) {
                // Cut first 20 bytes
                val sendBytes = ByteArray(20)
                System.arraycopy(bytes, 0, sendBytes, 0, sendBytes.size)
                var reBytes = ByteArray(bytes.size - sendBytes.size)
                System.arraycopy(bytes, sendBytes.size, reBytes, 0, reBytes.size)
                bytes = reBytes
                // and send
                bleTransport.gatt.writeCharacteristic(sendBytes)
                // The rest split to parts per 20 bytes max
                while (true) {
                    if (bytes.size > 20) {
                        val addBytes = ByteArray(20)
                        System.arraycopy(bytes, 0, addBytes, 0, addBytes.size)
                        reBytes = ByteArray(bytes.size - addBytes.size)
                        System.arraycopy(bytes, addBytes.size, reBytes, 0, reBytes.size)
                        bytes = reBytes
                        synchronized(mSendQueue) { mSendQueue.add(addBytes) }
                    } else {
                        synchronized(mSendQueue) { mSendQueue.add(bytes) }
                        break
                    }
                }
            } else {
                bleTransport.gatt.writeCharacteristic(bytes)
            }
        }
        // The rest from queue is send from onCharacteristicWrite (after sending 1st part)
        synchronized(message) {
            if (!message.isReceived) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "waiting for reply " + message.friendlyName + " on thread " + Thread.currentThread().name)
                try {
                    message.waitMillis(messageTimeoutMs)
                } catch (e: InterruptedException) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "sendMessage InterruptedException", e)
                }
                aapsLogger.debug(LTag.PUMPBTCOMM, "wait finished " + message.friendlyName + " isReceived=" + message.isReceived + " on thread " + Thread.currentThread().name)
            } else {
                aapsLogger.debug(LTag.PUMPBTCOMM, "already received " + message.friendlyName + " (no wait needed)")
            }
        }

        //SystemClock.sleep(200);
        if (!message.isReceived) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Reply not received " + message.friendlyName)
            message.handleMessageNotReceived()
            disconnect("Reply not received")
        }
        // verify encryption for v3 & BLE
        if (message is DanaRSPacketEtcKeepConnection && !message.isReceived)
            disconnect("KeepAlive not received")
    }

    // process common packet response
    private fun processMessage(decryptedBuffer: ByteArray) {
        val originalCommand = processedMessage?.command ?: 0xFFFF
        val receivedCommand = DanaRSPacket().getCommand(decryptedBuffer)
        val message: DanaRSPacket? = if (originalCommand == receivedCommand) {
            // it's response to last message
            processedMessage
        } else {
            // it's not response to last message, create new instance
            danaRSMessageHashTable.findMessage(receivedCommand)
        }
        if (message != null) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + message.friendlyName + " " + DanaRSPacket.toHexString(decryptedBuffer))
            // process received data
            message.handleMessage(decryptedBuffer)
            aapsLogger.debug(LTag.PUMPBTCOMM, "handleMessage done " + message.friendlyName + " setting received on thread " + Thread.currentThread().name)
            message.setReceived()
            synchronized(message) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "notifyAll " + message.friendlyName + " processedMessage===message: " + (processedMessage === message))
                // notify to sendMessage
                message.notifyAll()
            }
        } else aapsLogger.error(LTag.PUMPBTCOMM, "Unknown message received " + DanaRSPacket.toHexString(decryptedBuffer))
    }

}
