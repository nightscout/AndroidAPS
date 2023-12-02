package info.nightscout.pump.danars.services

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.notifyAll
import app.aaps.core.utils.waitMillis
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.pump.dana.DanaPump
import info.nightscout.pump.dana.R
import info.nightscout.pump.danars.activities.EnterPinActivity
import info.nightscout.pump.danars.activities.PairingHelperActivity
import info.nightscout.pump.danars.comm.DanaRSMessageHashTable
import info.nightscout.pump.danars.comm.DanaRSPacket
import info.nightscout.pump.danars.comm.DanaRSPacketEtcKeepConnection
import info.nightscout.pump.danars.encryption.EncryptionType
import info.nightscout.pump.danars.events.EventDanaRSPairingSuccess
import java.util.UUID
import java.util.concurrent.ScheduledFuture
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BLEComm @Inject internal constructor(
    private val injector: HasAndroidInjector,
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val context: Context,
    private val rxBus: RxBus,
    private val sp: SP,
    private val danaRSMessageHashTable: DanaRSMessageHashTable,
    private val danaPump: DanaPump,
    private val danaRSPlugin: info.nightscout.pump.danars.DanaRSPlugin,
    private val bleEncryption: BleEncryption,
    private val pumpSync: PumpSync,
    private val dateUtil: DateUtil,
    private val uiInteraction: UiInteraction
) {

    companion object {

        private const val WRITE_DELAY_MILLIS: Long = 50
        private const val UART_READ_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
        private const val UART_WRITE_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"
        private const val UART_BLE5_UUID = "00002902-0000-1000-8000-00805f9b34fb"

        private const val PACKET_START_BYTE = 0xA5.toByte()
        private const val PACKET_END_BYTE = 0x5A.toByte()
        private const val BLE5_PACKET_START_BYTE = 0xAA.toByte()
        private const val BLE5_PACKET_END_BYTE = 0xEE.toByte()
    }

    private var scheduledDisconnection: ScheduledFuture<*>? = null
    private var processedMessage: DanaRSPacket? = null
    private val mSendQueue = ArrayList<ByteArray>()
    private val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private var connectDeviceName: String? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private var encryption: EncryptionType = EncryptionType.ENCRYPTION_DEFAULT
        set(newValue) {
            bleEncryption.setEnhancedEncryption(newValue)
            field = newValue
        }
    private var isEasyMode: Boolean = false
    private var isUnitUD: Boolean = false

    var isConnected = false
    var isConnecting = false
    private var encryptedDataRead = false
    private var encryptedCommandSent = false
    private var uartRead: BluetoothGattCharacteristic? = null
    private var uartWrite: BluetoothGattCharacteristic? = null

    @Synchronized
    fun connect(from: String, address: String?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            ToastUtils.errorToast(context, context.getString(app.aaps.core.ui.R.string.need_connect_permission))
            aapsLogger.error(LTag.PUMPBTCOMM, "missing permission: $from")
            return false
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "Initializing BLEComm.")
        if (bluetoothAdapter == null) {
            aapsLogger.error("Unable to obtain a BluetoothAdapter.")
            return false
        }

        if (address == null) {
            aapsLogger.error("unspecified address.")
            return false
        }

        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            aapsLogger.error("Device not found.  Unable to connect from: $from")
            return false
        }
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                device.createBond()
                SystemClock.sleep(10000)
            }
            return false
        }
        isConnected = false
        encryption = EncryptionType.ENCRYPTION_DEFAULT
        encryptedDataRead = false
        encryptedCommandSent = false
        isConnecting = true
        bufferLength = 0
        aapsLogger.debug(LTag.PUMPBTCOMM, "Trying to create a new connection from: $from")
        connectDeviceName = device.name
        bluetoothGatt = device.connectGatt(context, false, mGattCallback)
        setCharacteristicNotification(uartReadBTGattChar, true)
        return true
    }

    @Synchronized
    fun stopConnecting() {
        isConnecting = false
    }

    @Synchronized
    fun disconnect(from: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            aapsLogger.error(LTag.PUMPBTCOMM, "missing permission: $from")
            return
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "disconnect from: $from")

        if (!encryptedDataRead && encryptedCommandSent && encryption == EncryptionType.ENCRYPTION_BLE5) {
            // there was no response from pump after started encryption
            // assume pairing keys are invalid
            val lastClearRequest = sp.getLong(R.string.key_rs_last_clear_key_request, 0)
            if (lastClearRequest != 0L && dateUtil.isOlderThan(lastClearRequest, 5)) {
                ToastUtils.errorToast(context, R.string.invalidpairing)
                danaRSPlugin.changePump()
                removeBond()
            } else if (lastClearRequest == 0L) {
                aapsLogger.error("Clearing pairing keys postponed")
                sp.putLong(R.string.key_rs_last_clear_key_request, dateUtil.now())
            }
        }
        if (!encryptedDataRead && encryptedCommandSent && encryption == EncryptionType.ENCRYPTION_RSv3) {
            // there was no response from pump after started encryption
            // assume pairing keys are invalid
            val lastClearRequest = sp.getLong(R.string.key_rs_last_clear_key_request, 0)
            if (lastClearRequest != 0L && dateUtil.isOlderThan(lastClearRequest, 5)) {
                aapsLogger.error("Clearing pairing keys !!!")
                sp.remove(rh.gs(R.string.key_danars_v3_randompairingkey) + danaRSPlugin.mDeviceName)
                sp.remove(rh.gs(R.string.key_danars_v3_pairingkey) + danaRSPlugin.mDeviceName)
                sp.remove(rh.gs(R.string.key_danars_v3_randomsynckey) + danaRSPlugin.mDeviceName)
                ToastUtils.errorToast(context, R.string.invalidpairing)
                danaRSPlugin.changePump()
            } else if (lastClearRequest == 0L) {
                aapsLogger.error("Clearing pairing keys postponed")
                sp.putLong(R.string.key_rs_last_clear_key_request, dateUtil.now())
            }
        }
        // cancel previous scheduled disconnection to prevent closing upcoming connection
        scheduledDisconnection?.cancel(false)
        scheduledDisconnection = null

        if (bluetoothAdapter == null || bluetoothGatt == null) {
            aapsLogger.error("disconnect not possible: (mBluetoothAdapter == null) " + (bluetoothAdapter == null))
            aapsLogger.error("disconnect not possible: (mBluetoothGatt == null) " + (bluetoothGatt == null))
            return
        }
        setCharacteristicNotification(uartReadBTGattChar, false)
        bluetoothGatt?.disconnect()
        isConnected = false
        encryptedDataRead = false
        encryptedCommandSent = false
        SystemClock.sleep(2000)
    }

    private fun removeBond() {
        sp.getStringOrNull(R.string.key_danars_address, null)?.let { address ->
            bluetoothAdapter?.getRemoteDevice(address)?.let { device ->
                try {
                    device::class.java.getMethod("removeBond").invoke(device)
                } catch (e: Exception) {
                    aapsLogger.error("Removing bond has been failed. ${e.message}")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized fun close() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "BluetoothAdapter close")
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            onConnectionStateChangeSynchronized(gatt, newState) // call it synchronized
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "onServicesDiscovered")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                findCharacteristic()
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            // for v3 after initial handshake it's encrypted - useless
            // aapsLogger.debug(LTag.PUMPBTCOMM, "onCharacteristicRead: " + DanaRS_Packet.toHexString(characteristic.value))
            readDataParsing(characteristic.value)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // for v3 after initial handshake it's encrypted - useless
            // aapsLogger.debug(LTag.PUMPBTCOMM, "onCharacteristicChanged: " + DanaRS_Packet.toHexString(characteristic.value))
            readDataParsing(characteristic.value)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            // for v3 after initial handshake it's encrypted - useless
            // aapsLogger.debug(LTag.PUMPBTCOMM, "onCharacteristicWrite: " + DanaRS_Packet.toHexString(characteristic.value))
            Thread {
                synchronized(mSendQueue) {
                    // after message sent, check if there is the rest of the message waiting and send it
                    if (mSendQueue.size > 0) {
                        val bytes = mSendQueue[0]
                        mSendQueue.removeAt(0)
                        writeCharacteristicNoResponse(uartWriteBTGattChar, bytes)
                    }
                }
            }.start()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            //aapsLogger.debug(LTag.PUMPBTCOMM, "onDescriptorWrite " + status)
            sendConnect()
            // 1st message sent to pump after connect
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    @Synchronized
    private fun setCharacteristicNotification(characteristic: BluetoothGattCharacteristic?, enabled: Boolean) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "setCharacteristicNotification")
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            aapsLogger.error("BluetoothAdapter not initialized_ERROR")
            isConnecting = false
            isConnected = false
            encryptedDataRead = false
            encryptedCommandSent = false
            return
        }
        bluetoothGatt?.setCharacteristicNotification(characteristic, enabled)
        // Dana-i BLE5 specific
        characteristic?.getDescriptor(UUID.fromString(UART_BLE5_UUID))?.let {
            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            bluetoothGatt?.writeDescriptor(it)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    @Synchronized
    private fun writeCharacteristicNoResponse(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        Thread(Runnable {
            SystemClock.sleep(WRITE_DELAY_MILLIS)
            if (bluetoothAdapter == null || bluetoothGatt == null) {
                aapsLogger.error("BluetoothAdapter not initialized_ERROR")
                isConnecting = false
                isConnected = false
                encryptedDataRead = false
                encryptedCommandSent = false
                return@Runnable
            }
            characteristic.value = data
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            //aapsLogger.debug("writeCharacteristic:" + DanaRS_Packet.toHexString(data))
            bluetoothGatt?.writeCharacteristic(characteristic)
        }).start()
        SystemClock.sleep(50)
    }

    private val uartReadBTGattChar: BluetoothGattCharacteristic
        get() = uartRead
            ?: BluetoothGattCharacteristic(UUID.fromString(UART_READ_UUID), BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0).also { uartRead = it }

    private val uartWriteBTGattChar: BluetoothGattCharacteristic
        get() = uartWrite
            ?: BluetoothGattCharacteristic(UUID.fromString(UART_WRITE_UUID), BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, 0).also { uartWrite = it }

    private fun getSupportedGattServices(): List<BluetoothGattService>? {
        aapsLogger.debug(LTag.PUMPBTCOMM, "getSupportedGattServices")
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            aapsLogger.error("BluetoothAdapter not initialized_ERROR")
            isConnecting = false
            isConnected = false
            encryptedDataRead = false
            encryptedCommandSent = false
            return null
        }
        return bluetoothGatt?.services
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

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun onConnectionStateChangeSynchronized(gatt: BluetoothGatt, newState: Int) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "onConnectionStateChange")
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            gatt.discoverServices()
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            close()
            isConnected = false
            isConnecting = false
            encryption = EncryptionType.ENCRYPTION_DEFAULT
            encryptedDataRead = false
            encryptedCommandSent = false
            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
            aapsLogger.debug(LTag.PUMPBTCOMM, "Device was disconnected " + gatt.device.name) //Device was disconnected
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
                    sp.putLong(R.string.key_rs_last_clear_key_request, 0L)
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
                    aapsLogger.error("length: " + length + "bufferLength: " + bufferLength)
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
                            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK.toByte()         ->
                                processConnectResponse(decryptedBuffer)

                            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION.toByte()   ->
                                processEncryptionResponse(decryptedBuffer)

                            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY.toByte()      ->
                                processPasskeyCheck(decryptedBuffer)

                            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST.toByte()    ->
                                processPairingRequest(decryptedBuffer)

                            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_RETURN.toByte()     ->
                                processPairingRequest2(decryptedBuffer)

                            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__GET_PUMP_CHECK.toByte()     -> {
                                // not easy mode, request time info
                                if (decryptedBuffer[2] == 0x05.toByte()) sendTimeInfo()
                                // easy mode
                                else sendEasyMenuCheck()
                            }

                            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__GET_EASYMENU_CHECK.toByte() ->
                                processEasyMenuCheck(decryptedBuffer)
                        }

                    } else {
                        // Retrieve message code from received buffer and last message sent
                        processMessage(decryptedBuffer)
                    }
                }
                if (decrypted == null)
                    throw IllegalStateException("Null decryptedInputBuffer")
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
    private fun sendConnect() {
        val deviceName = connectDeviceName
        if (deviceName == null || deviceName == "") {
            uiInteraction.addNotification(Notification.DEVICE_NOT_PAIRED, rh.gs(R.string.pairfirst), Notification.URGENT)
            return
        }
        val bytes = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK, null, deviceName)
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + "ENCRYPTION__PUMP_CHECK (0x00)" + " " + DanaRSPacket.toHexString(bytes))
        writeCharacteristicNoResponse(uartWriteBTGattChar, bytes)
    }

    // 1st packet response
    private fun processConnectResponse(decryptedBuffer: ByteArray) {
        // response OK v1
        if (decryptedBuffer.size == 4 && decryptedBuffer[2] == 'O'.code.toByte() && decryptedBuffer[3] == 'K'.code.toByte()) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PUMP_CHECK (OK)" + " " + DanaRSPacket.toHexString(decryptedBuffer))
            encryption = EncryptionType.ENCRYPTION_DEFAULT
            danaPump.ignoreUserPassword = false
            // Grab pairing key from preferences if exists
            val pairingKey = sp.getString(rh.gs(R.string.key_danars_pairingkey) + danaRSPlugin.mDeviceName, "")
            aapsLogger.debug(LTag.PUMPBTCOMM, "Using stored pairing key: $pairingKey")
            if (pairingKey.isNotEmpty()) {
                sendPasskeyCheck(pairingKey)
            } else {
                // Stored pairing key does not exists, request pairing
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
            sp.putString(rh.gs(R.string.key_danars_v3_randomsynckey) + danaRSPlugin.mDeviceName, String.format("%02x", decryptedBuffer[decryptedBuffer.size - 1]))

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
                sp.putString(rh.gs(R.string.key_dana_ble5_pairingkey) + danaRSPlugin.mDeviceName, pairingKey)

            val storedPairingKey = sp.getString(rh.gs(R.string.key_dana_ble5_pairingkey) + danaRSPlugin.mDeviceName, "")
            if (storedPairingKey.isBlank()) {
                removeBond()
                disconnect("Non existing pairing key")
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
            mSendQueue.clear()
            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED, rh.gs(R.string.pumperror)))
            pumpSync.insertAnnouncement(rh.gs(R.string.pumperror), null, danaPump.pumpType(), danaPump.serialNumber)
            uiInteraction.addNotification(Notification.PUMP_ERROR, rh.gs(R.string.pumperror), Notification.URGENT)
            // response BUSY: error status
        } else if (decryptedBuffer.size == 6 && decryptedBuffer[2] == 'B'.code.toByte() && decryptedBuffer[3] == 'U'.code.toByte() && decryptedBuffer[4] == 'S'.code.toByte() && decryptedBuffer[5] == 'Y'.code.toByte()) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PUMP_CHECK (BUSY)" + " " + DanaRSPacket.toHexString(decryptedBuffer))
            mSendQueue.clear()
            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED, rh.gs(app.aaps.core.ui.R.string.pump_busy)))
        } else {
            // ERROR in response, wrong serial number
            aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__PUMP_CHECK (ERROR)" + " " + DanaRSPacket.toHexString(decryptedBuffer))
            mSendQueue.clear()
            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED, rh.gs(app.aaps.core.ui.R.string.connection_error)))
            danaRSPlugin.clearPairing()
            uiInteraction.addNotification(Notification.WRONG_SERIAL_NUMBER, rh.gs(app.aaps.core.ui.R.string.password_cleared), Notification.URGENT)
        }
    }

    // 2nd packet v1 check passkey
    private fun sendPasskeyCheck(pairingKey: String) {
        val encodedPairingKey = DanaRSPacket.hexToBytes(pairingKey)
        val bytes = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY, encodedPairingKey, null)
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + "ENCRYPTION__CHECK_PASSKEY" + " " + DanaRSPacket.toHexString(bytes))
        writeCharacteristicNoResponse(uartWriteBTGattChar, bytes)
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
        val randomPairingKey = sp.getString(rh.gs(R.string.key_danars_v3_randompairingkey) + danaRSPlugin.mDeviceName, "")
        val pairingKey = sp.getString(rh.gs(R.string.key_danars_v3_pairingkey) + danaRSPlugin.mDeviceName, "")
        if (randomPairingKey.isNotEmpty() && pairingKey.isNotEmpty()) {
            val tPairingKey = Base64.decode(pairingKey, Base64.DEFAULT)
            val tRandomPairingKey = Base64.decode(randomPairingKey, Base64.DEFAULT)
            var tRandomSyncKey: Byte = 0
            val randomSyncKey = sp.getString(rh.gs(R.string.key_danars_v3_randomsynckey) + danaRSPlugin.mDeviceName, "")
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
        val params = ByteArray(4) { 0.toByte() }
        val bytes: ByteArray = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION, params, null)
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + "ENCRYPTION__TIME_INFORMATION BLE5" + " " + DanaRSPacket.toHexString(bytes))
        writeCharacteristicNoResponse(uartWriteBTGattChar, bytes)
    }

    private fun sendV3PairingInformation(requestNewPairing: Int) {
        val params = byteArrayOf(requestNewPairing.toByte())
        val bytes: ByteArray = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION, params, null)
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + "ENCRYPTION__TIME_INFORMATION" + " " + DanaRSPacket.toHexString(bytes))
        writeCharacteristicNoResponse(uartWriteBTGattChar, bytes)
    }

    // 2nd packet response
    private fun processEncryptionResponse(decryptedBuffer: ByteArray) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + "ENCRYPTION__TIME_INFORMATION " + DanaRSPacket.toHexString(decryptedBuffer))
        if (encryption == EncryptionType.ENCRYPTION_BLE5) {
            isConnected = true
            isConnecting = false
            aapsLogger.debug(LTag.PUMPBTCOMM, "Connect !!")
        } else if (encryption == EncryptionType.ENCRYPTION_RSv3) {
            // decryptedBuffer[2] : 0x00 OK  0x01 Error, No pairing
            if (decryptedBuffer[2] == 0x00.toByte()) {
                val randomPairingKey = sp.getString(rh.gs(R.string.key_danars_v3_randompairingkey) + danaRSPlugin.mDeviceName, "")
                val pairingKey = sp.getString(rh.gs(R.string.key_danars_v3_pairingkey) + danaRSPlugin.mDeviceName, "")
                if (randomPairingKey.isNotEmpty() && pairingKey.isNotEmpty()) {
                    // expecting successful connect
                    isConnected = true
                    isConnecting = false
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Connect !!")
                    // Send one message to confirm communication
                } else {
                    context.startActivity(Intent(context, EnterPinActivity::class.java).also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
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
                uiInteraction.addNotification(Notification.WRONG_PUMP_PASSWORD, rh.gs(R.string.wrongpumppassword), Notification.URGENT)
                disconnect("WrongPassword")
                SystemClock.sleep(T.mins(1).msecs())
            } else {
                rxBus.send(EventDismissNotification(Notification.WRONG_PUMP_PASSWORD))
                rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
                isConnected = true
                isConnecting = false
                aapsLogger.debug(LTag.PUMPBTCOMM, "RS connected and status read")
            }
        }
    }

    // 3rd packet v1 existing pairing
    private fun sendTimeInfo() {
        val bytes = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION, null, null)
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + "ENCRYPTION__TIME_INFORMATION" + " " + DanaRSPacket.toHexString(bytes))
        writeCharacteristicNoResponse(uartWriteBTGattChar, bytes)
    }

    //2nd or 3rd packet v1 pairing doesn't exist
    private fun sendPairingRequest() {
        // Start activity which is waiting 20sec
        // On pump pairing request is displayed and is waiting for conformation
        context.startActivity(Intent(context, PairingHelperActivity::class.java).also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        val bytes = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST, null, null)
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + "ENCRYPTION__PASSKEY_REQUEST" + " " + DanaRSPacket.toHexString(bytes))
        writeCharacteristicNoResponse(uartWriteBTGattChar, bytes)
    }

    // 3rd packet v3 : only after entering PIN codes
    fun finishV3Pairing() {
        val randomPairingKey = sp.getString(rh.gs(R.string.key_danars_v3_randompairingkey) + danaRSPlugin.mDeviceName, "")
        val pairingKey = sp.getString(rh.gs(R.string.key_danars_v3_pairingkey) + danaRSPlugin.mDeviceName, "")
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
        rxBus.send(EventDanaRSPairingSuccess())
        sendTimeInfo()
        val pairingKey = byteArrayOf(decryptedBuffer[2], decryptedBuffer[3])
        // store pairing key to preferences
        sp.putString(rh.gs(R.string.key_danars_pairingkey) + danaRSPlugin.mDeviceName, DanaRSPacket.bytesToHex(pairingKey))
        aapsLogger.debug(LTag.PUMPBTCOMM, "Got pairing key: " + DanaRSPacket.bytesToHex(pairingKey))
    }

    // 3rd packet Easy menu pump
    private fun sendEasyMenuCheck() {
        val bytes: ByteArray = bleEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__GET_EASYMENU_CHECK, null, null)
        writeCharacteristicNoResponse(uartWriteBTGattChar, bytes)
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
        if (bluetoothGatt == null) {
            aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> IGNORING (NOT CONNECTED) " + message.friendlyName + " " + DanaRSPacket.toHexString(command) + " " + DanaRSPacket.toHexString(params))
            return
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + message.friendlyName + " " + DanaRSPacket.toHexString(command) + " " + DanaRSPacket.toHexString(params))
        var bytes = bleEncryption.getEncryptedPacket(message.opCode, params, null)
        // aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + DanaRS_Packet.toHexString(bytes))
        if (encryption != EncryptionType.ENCRYPTION_DEFAULT)
            bytes = bleEncryption.encryptSecondLevelPacket(bytes)
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
            disconnect("Reply not received")
        }
        // verify encryption for v3 & BLE
        if (message is DanaRSPacketEtcKeepConnection)
            if (!message.isReceived) disconnect("KeepAlive not received")
    }

    // process common packet response
    private fun processMessage(decryptedBuffer: ByteArray) {
        val originalCommand = processedMessage?.command ?: 0xFFFF
        val receivedCommand = DanaRSPacket(injector).getCommand(decryptedBuffer)
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
            message.setReceived()
            synchronized(message) {
                // notify to sendMessage
                message.notifyAll()
            }
        } else aapsLogger.error("Unknown message received " + DanaRSPacket.toHexString(decryptedBuffer))
    }

}