package info.nightscout.androidaps.plugins.pump.danaRS.services

import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin
import info.nightscout.androidaps.plugins.pump.danaRS.activities.PairingHelperActivity
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRSMessageHashTable
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet
import info.nightscout.androidaps.plugins.pump.danaRS.encryption.BleEncryption
import info.nightscout.androidaps.plugins.pump.danaRS.events.EventDanaRSPairingSuccess
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import okhttp3.internal.notify
import okhttp3.internal.waitMillis
import java.util.*
import java.util.concurrent.ScheduledFuture
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BLEComm @Inject internal constructor(
    private val aapsLogger: AAPSLogger,
    private val resourceHelper: ResourceHelper,
    private val context: Context,
    private val rxBus: RxBusWrapper,
    private val sp: SP,
    private val danaRSMessageHashTable: DanaRSMessageHashTable,
    private val danaRPump: DanaRPump,
    private val bleEncryption: BleEncryption
) {

    companion object {
        private const val WRITE_DELAY_MILLIS: Long = 50
        private const val UART_READ_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
        private const val UART_WRITE_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"

        private const val PACKET_START_BYTE = 0xA5.toByte()
        private const val PACKET_END_BYTE = 0x5A.toByte()
    }

    private var scheduledDisconnection: ScheduledFuture<*>? = null
    private var processedMessage: DanaRS_Packet? = null
    private val mSendQueue = ArrayList<ByteArray>()
    private var mBluetoothManager: BluetoothManager? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var connectDeviceName: String? = null
    private var mBluetoothGatt: BluetoothGatt? = null

    var isConnected = false
    var isConnecting = false
    private var uartRead: BluetoothGattCharacteristic? = null
    private var uartWrite: BluetoothGattCharacteristic? = null

    init {
        initialize()
    }

    private fun initialize(): Boolean {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Initializing BLEComm.")
        if (mBluetoothManager == null) {
            mBluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            if (mBluetoothManager == null) {
                aapsLogger.error("Unable to initialize BluetoothManager.")
                return false
            }
        }
        mBluetoothAdapter = mBluetoothManager?.adapter
        if (mBluetoothAdapter == null) {
            aapsLogger.error("Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
    }

    fun connect(from: String, address: String?): Boolean {
        // test existing BT device
        val tBluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
            ?: return false
        tBluetoothManager.adapter ?: return false

        if (address == null) {
            aapsLogger.error("unspecified address.")
            return false
        }

        if (mBluetoothAdapter == null) {
            if (!initialize()) {
                return false
            }
        }
        isConnecting = true
        val device = mBluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            aapsLogger.error("Device not found.  Unable to connect from: $from")
            return false
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "Trying to create a new connection from: $from")
        connectDeviceName = device.name
        mBluetoothGatt = device.connectGatt(context, false, mGattCallback)
        setCharacteristicNotification(uartReadBTGattChar, true)
        return true
    }

    fun stopConnecting() {
        isConnecting = false
    }

    @Synchronized
    fun disconnect(from: String) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "disconnect from: $from")

        // cancel previous scheduled disconnection to prevent closing upcoming connection
        scheduledDisconnection?.cancel(false)
        scheduledDisconnection = null

        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            aapsLogger.error("disconnect not possible: (mBluetoothAdapter == null) " + (mBluetoothAdapter == null))
            aapsLogger.error("disconnect not possible: (mBluetoothGatt == null) " + (mBluetoothGatt == null))
            return
        }
        setCharacteristicNotification(uartReadBTGattChar, false)
        mBluetoothGatt?.disconnect()
        isConnected = false
        SystemClock.sleep(2000)
    }

    @Synchronized fun close() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "BluetoothAdapter close")
        mBluetoothGatt?.close()
        mBluetoothGatt = null
    }

    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            onConnectionStateChangeSynchronized(gatt, newState) // call it synchronized
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "onServicesDiscovered")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                findCharacteristic()
            }
            sendPumpCheck()
            // 1st message sent to pump after connect
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "onCharacteristicRead: " + DanaRS_Packet.toHexString(characteristic.value))
            addToReadBuffer(characteristic.value)
            readDataParsing()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "onCharacteristicChanged: " + DanaRS_Packet.toHexString(characteristic.value))
            addToReadBuffer(characteristic.value)
            Thread(Runnable { readDataParsing() }).start()
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "onCharacteristicWrite: " + DanaRS_Packet.toHexString(characteristic.value))
            Thread(Runnable {
                synchronized(mSendQueue) {
                    // after message sent, check if there is the rest of the message waiting and send it
                    if (mSendQueue.size > 0) {
                        val bytes = mSendQueue[0]
                        mSendQueue.removeAt(0)
                        writeCharacteristicNoResponse(uartWriteBTGattChar, bytes)
                    }
                }
            }).start()
        }
    }

    @Synchronized
    private fun setCharacteristicNotification(characteristic: BluetoothGattCharacteristic?, enabled: Boolean) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "setCharacteristicNotification")
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            aapsLogger.error("BluetoothAdapter not initialized_ERROR")
            isConnecting = false
            isConnected = false
            return
        }
        mBluetoothGatt!!.setCharacteristicNotification(characteristic, enabled)
    }

    @Synchronized
    private fun writeCharacteristicNoResponse(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        Thread(Runnable {
            SystemClock.sleep(WRITE_DELAY_MILLIS)
            if (mBluetoothAdapter == null || mBluetoothGatt == null) {
                aapsLogger.error("BluetoothAdapter not initialized_ERROR")
                isConnecting = false
                isConnected = false
                return@Runnable
            }
            characteristic.value = data
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            aapsLogger.debug("writeCharacteristic:" + DanaRS_Packet.toHexString(data))
            mBluetoothGatt!!.writeCharacteristic(characteristic)
        }).start()
    }

    private val uartReadBTGattChar: BluetoothGattCharacteristic
        get() = uartRead
            ?: BluetoothGattCharacteristic(UUID.fromString(UART_READ_UUID), BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0).also { uartRead = it }

    private val uartWriteBTGattChar: BluetoothGattCharacteristic
        get() = uartWrite
            ?: BluetoothGattCharacteristic(UUID.fromString(UART_WRITE_UUID), BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, 0).also { uartWrite = it }

    private fun getSupportedGattServices(): List<BluetoothGattService>? {
            aapsLogger.debug(LTag.PUMPBTCOMM, "getSupportedGattServices")
            if (mBluetoothAdapter == null || mBluetoothGatt == null) {
                aapsLogger.error("BluetoothAdapter not initialized_ERROR")
                isConnecting = false
                isConnected = false
                return null
            }
            return mBluetoothGatt?.services
        }

    private fun findCharacteristic() {
        val gattServices = getSupportedGattServices() ?: return
        var uuid: String
        for (gattService in gattServices) {
            val gattCharacteristics = gattService.characteristics
            for (gattCharacteristic in gattCharacteristics) {
                uuid = gattCharacteristic.uuid.toString()
                if (UART_READ_UUID == uuid) {
                    uartRead = gattCharacteristic
                    setCharacteristicNotification(uartRead, true)
                }
                if (UART_WRITE_UUID == uuid) {
                    uartWrite = gattCharacteristic
                }
            }
        }
    }

    @Synchronized
    private fun onConnectionStateChangeSynchronized(gatt: BluetoothGatt, newState: Int) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "onConnectionStateChange")
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            gatt.discoverServices()
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            close()
            isConnected = false
            isConnecting = false
            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
            aapsLogger.debug(LTag.PUMPBTCOMM, "Device was disconnected " + gatt.device.name) //Device was disconnected
        }
    }

    private val readBuffer = ByteArray(1024)
    private var bufferLength = 0

    private fun addToReadBuffer(buffer: ByteArray) {
        //log.debug("addToReadBuffer " + DanaRS_Packet.toHexString(buffer));
        if (buffer.isEmpty()) {
            return
        }
        synchronized(readBuffer) {
            // Append incoming data to input buffer
            System.arraycopy(buffer, 0, readBuffer, bufferLength, buffer.size)
            bufferLength += buffer.size
        }
    }

    private fun readDataParsing() {
        var startSignatureFound = false
        var packetIsValid = false
        var isProcessing: Boolean
        isProcessing = true
        var inputBuffer: ByteArray? = null

        while (isProcessing) {
            var length = 0
            synchronized(readBuffer) {

                // Find packet start [A5 A5]
                if (bufferLength >= 6) {
                    for (idxStartByte in 0 until bufferLength - 2) {
                        if (readBuffer[idxStartByte] == PACKET_START_BYTE && readBuffer[idxStartByte + 1] == PACKET_START_BYTE) {
                            if (idxStartByte > 0) {
                                // if buffer doesn't start with signature remove the leading trash
                                aapsLogger.debug(LTag.PUMPBTCOMM, "Shifting the input buffer by $idxStartByte bytes")
                                System.arraycopy(readBuffer, idxStartByte, readBuffer, 0, bufferLength - idxStartByte)
                                bufferLength -= idxStartByte
                            }
                            startSignatureFound = true
                            break
                        }
                    }
                }
                // A5 A5 LEN TYPE CODE PARAMS CHECKSUM1 CHECKSUM2 5A 5A
                //           ^---- LEN -----^
                // total packet length 2 + 1 + readBuffer[2] + 2 + 2
                if (startSignatureFound) {
                    length = readBuffer[2].toInt()
                    // test if there is enough data loaded
                    if (length + 7 > bufferLength) return
                    // Verify packed end [5A 5A]
                    if (readBuffer[length + 5] == PACKET_END_BYTE && readBuffer[length + 6] == PACKET_END_BYTE) {
                        packetIsValid = true
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
                        aapsLogger.error("length: " + length + "bufferLength: " + bufferLength)
                        throw e
                    }
                    bufferLength -= length + 7
                    // now we have encrypted packet in inputBuffer
                }
            }
            if (packetIsValid) {
                try {
                    // decrypt the packet
                    bleEncryption.getDecryptedPacket(inputBuffer)?.let { decryptedBuffer ->
                        when (decryptedBuffer[0]) {
                            BleEncryption.DANAR_PACKET__TYPE_ENCRYPTION_RESPONSE.toByte() -> when (decryptedBuffer[1]) {
                                BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK.toByte()       -> if (decryptedBuffer.size == 4 && decryptedBuffer[2] == 'O'.toByte() && decryptedBuffer[3] == 'K'.toByte()) {
                                    aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PUMP_CHECK (OK)" + " " + DanaRS_Packet.toHexString(decryptedBuffer))
                                    // Grab pairing key from preferences if exists
                                    val pairingKey = sp.getString(resourceHelper.gs(R.string.key_danars_pairingkey) + DanaRSPlugin.mDeviceName, "")
                                    aapsLogger.debug(LTag.PUMPBTCOMM, "Using stored pairing key: $pairingKey")
                                    if (pairingKey.isNotEmpty()) {
                                        val encodedPairingKey = DanaRS_Packet.hexToBytes(pairingKey)
                                        val bytes = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY, encodedPairingKey, null)
                                        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + "ENCRYPTION__CHECK_PASSKEY" + " " + DanaRS_Packet.toHexString(bytes))
                                        writeCharacteristicNoResponse(uartWriteBTGattChar, bytes)
                                    } else {
                                        // Stored pairing key does not exists, request pairing
                                        sendPairingRequest()
                                    }
                                } else if (decryptedBuffer.size == 6 && decryptedBuffer[2] == 'P'.toByte() && decryptedBuffer[3] == 'U'.toByte() && decryptedBuffer[4] == 'M'.toByte() && decryptedBuffer[5] == 'P'.toByte()) {
                                    aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PUMP_CHECK (PUMP)" + " " + DanaRS_Packet.toHexString(decryptedBuffer))
                                    mSendQueue.clear()
                                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED, resourceHelper.gs(R.string.pumperror)))
                                    NSUpload.uploadError(resourceHelper.gs(R.string.pumperror))
                                    val n = Notification(Notification.PUMPERROR, resourceHelper.gs(R.string.pumperror), Notification.URGENT)
                                    rxBus.send(EventNewNotification(n))
                                } else if (decryptedBuffer.size == 6 && decryptedBuffer[2] == 'B'.toByte() && decryptedBuffer[3] == 'U'.toByte() && decryptedBuffer[4] == 'S'.toByte() && decryptedBuffer[5] == 'Y'.toByte()) {
                                    aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PUMP_CHECK (BUSY)" + " " + DanaRS_Packet.toHexString(decryptedBuffer))
                                    mSendQueue.clear()
                                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED, resourceHelper.gs(R.string.pumpbusy)))
                                } else {
                                    // ERROR in response, wrong serial number
                                    aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PUMP_CHECK (ERROR)" + " " + DanaRS_Packet.toHexString(decryptedBuffer))
                                    mSendQueue.clear()
                                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED, resourceHelper.gs(R.string.connectionerror)))
                                    sp.remove(resourceHelper.gs(R.string.key_danars_pairingkey) + DanaRSPlugin.mDeviceName)
                                    val n = Notification(Notification.WRONGSERIALNUMBER, resourceHelper.gs(R.string.wrongpassword), Notification.URGENT)
                                    rxBus.send(EventNewNotification(n))
                                }

                                BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY.toByte()    -> {
                                    aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__CHECK_PASSKEY" + " " + DanaRS_Packet.toHexString(decryptedBuffer))
                                    if (decryptedBuffer[2] == 0x00.toByte()) {
                                        // Paring is not requested, sending time info
                                        sendTimeInfo()
                                    } else {
                                        // Pairing on pump is requested
                                        sendPairingRequest()
                                    }
                                }

                                BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST.toByte()  -> {
                                    aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PASSKEY_REQUEST " + DanaRS_Packet.toHexString(decryptedBuffer))
                                    if (decryptedBuffer[2] != 0x00.toByte()) {
                                        disconnect("passkey request failed")
                                    }
                                }

                                BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_RETURN.toByte()   -> {
                                    aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PASSKEY_RETURN " + DanaRS_Packet.toHexString(decryptedBuffer))
                                    // Paring is successful, sending time info
                                    rxBus.send(EventDanaRSPairingSuccess())
                                    sendTimeInfo()
                                    val pairingKey = byteArrayOf(decryptedBuffer[2], decryptedBuffer[3])
                                    // store pairing key to preferences
                                    sp.putString(resourceHelper.gs(R.string.key_danars_pairingkey) + DanaRSPlugin.mDeviceName, DanaRS_Packet.bytesToHex(pairingKey))
                                    aapsLogger.debug(LTag.PUMPBTCOMM, "Got pairing key: " + DanaRS_Packet.bytesToHex(pairingKey))
                                }

                                BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION.toByte() -> {
                                    aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__TIME_INFORMATION " +  /*message.getMessageName() + " " + */DanaRS_Packet.toHexString(decryptedBuffer))
                                    val size = decryptedBuffer.size
                                    var pass: Int = (decryptedBuffer[size - 1].toInt() and 0x000000FF shl 8) + (decryptedBuffer[size - 2].toInt() and 0x000000FF)
                                    pass = pass xor 3463
                                    danaRPump.rsPassword = Integer.toHexString(pass)
                                    aapsLogger.debug(LTag.PUMPBTCOMM, "Pump user password: " + Integer.toHexString(pass))
                                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
                                    isConnected = true
                                    isConnecting = false
                                    aapsLogger.debug(LTag.PUMPBTCOMM, "RS connected and status read")
                                }
                            }

                            else                                                          -> {
                                // Retrieve message code from received buffer and last message sent
                                val originalCommand = processedMessage?.command ?: 0xFFFF
                                val receivedCommand = DanaRS_Packet.getCommand(decryptedBuffer)
                                val message: DanaRS_Packet? = if (originalCommand == receivedCommand) {
                                    // it's response to last message
                                    processedMessage
                                } else {
                                    // it's not response to last message, create new instance
                                    danaRSMessageHashTable.findMessage(receivedCommand)
                                }
                                if (message != null) {
                                    aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + message.friendlyName + " " + DanaRS_Packet.toHexString(decryptedBuffer))
                                    // process received data
                                    message.handleMessage(decryptedBuffer)
                                    message.setReceived()
                                    synchronized(message) {
                                        // notify to sendMessage
                                        message.notify()
                                    }
                                } else {
                                    aapsLogger.error("Unknown message received " + DanaRS_Packet.toHexString(decryptedBuffer))
                                }
                            }
                        }
                    } ?: throw IllegalStateException("Null decryptedInputBuffer")
                } catch (e: Exception) {
                    aapsLogger.error("Unhandled exception", e)
                }
                startSignatureFound = false
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

    fun sendMessage(message: DanaRS_Packet?) {
        processedMessage = message
        if (message == null) return
        val command = byteArrayOf(message.type.toByte(), message.opCode.toByte())
        val params = message.requestParams
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + message.friendlyName + " " + DanaRS_Packet.toHexString(command) + " " + DanaRS_Packet.toHexString(params))
        var bytes = bleEncryption.getEncryptedPacket(message.opCode, params, null)
        // If there is another message not completely sent, add to queue only
        if (mSendQueue.size > 0) {
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
                writeCharacteristicNoResponse(uartWriteBTGattChar, sendBytes)
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
                writeCharacteristicNoResponse(uartWriteBTGattChar, bytes)
            }
        }
        // The rest from queue is send from onCharacteristicWrite (after sending 1st part)
        synchronized(message) {
            try {
                message.waitMillis(5000)
            } catch (e: InterruptedException) {
                aapsLogger.error("sendMessage InterruptedException", e)
            }
        }

        //SystemClock.sleep(200);
        if (!message.isReceived) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Reply not received " + message.friendlyName)
            message.handleMessageNotReceived()
        }
    }

    private fun sendPairingRequest() {
        // Start activity which is waiting 20sec
        // On pump pairing request is displayed and is waiting for conformation
        val i = Intent()
        i.setClass(context, PairingHelperActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
        val bytes = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST, null, null)
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + "ENCRYPTION__PASSKEY_REQUEST" + " " + DanaRS_Packet.toHexString(bytes))
        writeCharacteristicNoResponse(uartWriteBTGattChar, bytes)
    }

    private fun sendPumpCheck() {
        // 1st message sent to pump after connect
        val deviceName = connectDeviceName
        if (deviceName == null || deviceName == "") {
            val n = Notification(Notification.DEVICENOTPAIRED, resourceHelper.gs(R.string.pairfirst), Notification.URGENT)
            rxBus.send(EventNewNotification(n))
            return
        }
        val bytes = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK, null, deviceName)
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + "ENCRYPTION__PUMP_CHECK (0x00)" + " " + DanaRS_Packet.toHexString(bytes))
        writeCharacteristicNoResponse(uartWriteBTGattChar, bytes)
    }

    private fun sendTimeInfo() {
        val bytes = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION, null, null)
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + "ENCRYPTION__TIME_INFORMATION" + " " + DanaRS_Packet.toHexString(bytes))
        writeCharacteristicNoResponse(uartWriteBTGattChar, bytes)
    }
}