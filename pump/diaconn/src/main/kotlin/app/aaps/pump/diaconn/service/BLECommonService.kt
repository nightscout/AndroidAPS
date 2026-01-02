package app.aaps.pump.diaconn.service

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
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.notifyAll
import app.aaps.core.utils.waitMillis
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.R
import app.aaps.pump.diaconn.packet.BatteryWarningReportPacket
import app.aaps.pump.diaconn.packet.BigLogInquireResponsePacket
import app.aaps.pump.diaconn.packet.DiaconnG8Packet
import app.aaps.pump.diaconn.packet.DiaconnG8ResponseMessageHashTable
import app.aaps.pump.diaconn.packet.DiaconnG8SettingResponseMessageHashTable
import app.aaps.pump.diaconn.packet.InjectionBlockReportPacket
import app.aaps.pump.diaconn.packet.InsulinLackReportPacket
import dagger.android.HasAndroidInjector
import java.util.UUID
import java.util.concurrent.ScheduledFuture
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("SpellCheckingInspection")
@Singleton
class BLECommonService @Inject internal constructor(
    private val injector: HasAndroidInjector,
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val context: Context,
    private val rxBus: RxBus,
    private val diaconnG8ResponseMessageHashTable: DiaconnG8ResponseMessageHashTable,
    private val diaconnG8SettingResponseMessageHashTable: DiaconnG8SettingResponseMessageHashTable,
    private val diaconnG8Pump: DiaconnG8Pump,
    private val uiInteraction: UiInteraction
) {

    companion object {

        private const val WRITE_DELAY_MILLIS: Long = 50
        private const val INDICATION_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
        private const val WRITE_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
        private const val CHARACTERISTIC_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"
    }

    private var scheduledDisconnection: ScheduledFuture<*>? = null
    private var processedMessage: DiaconnG8Packet? = null
    private var processedMessageByte: ByteArray? = null
    private val mSendQueue = ArrayList<ByteArray>()
    private val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private var connectDeviceName: String? = null
    private var bluetoothGatt: BluetoothGatt? = null

    var isConnected = false
    var isConnecting = false
    private var uartIndicate: BluetoothGattCharacteristic? = null
    private var uartWrite: BluetoothGattCharacteristic? = null

    private var mSequence: Int = 0

    private fun getMsgSequence(): Int {
        val seq = mSequence % 255
        mSequence++
        if (mSequence == 255) {
            mSequence = 0
        }
        return seq
    }

    @Synchronized
    fun connect(from: String, address: String?): Boolean {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ToastUtils.errorToast(context, context.getString(app.aaps.core.ui.R.string.need_connect_permission))
            aapsLogger.error(LTag.PUMPBTCOMM, "missing permission: $from")
            return false
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "Initializing Bluetooth ")
        if (bluetoothAdapter == null) {
            aapsLogger.error("Unable to obtain a BluetoothAdapter.")
            return false
        }

        if (address == null) {
            aapsLogger.error("unspecified address.")
            return false
        }

        bluetoothGatt?.let {
            it.disconnect()
            SystemClock.sleep(200)
            it.close()
            SystemClock.sleep(200)
            bluetoothGatt = null
        }

        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            aapsLogger.error("Device not found.  Unable to connect from: $from")
            return false
        }

        if (device.bondState == BluetoothDevice.BOND_NONE) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                device.createBond()
                SystemClock.sleep(10000)
            }
            return false
        }

        aapsLogger.debug(LTag.PUMPBTCOMM, "Trying to create a new connection from: $from")
        connectDeviceName = device.name
        bluetoothGatt = device.connectGatt(context, false, mGattCallback)

        isConnected = false
        isConnecting = true
        return true
    }

    @Synchronized
    fun stopConnecting() {
        isConnecting = false
    }

    @Synchronized
    fun disconnect(from: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            aapsLogger.error(LTag.PUMPBTCOMM, "missing permission: $from")
            return
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "disconnect from: $from")

        // cancel previous scheduled disconnection to prevent closing upcoming connection
        scheduledDisconnection?.cancel(false)
        scheduledDisconnection = null

        if (bluetoothAdapter == null || bluetoothGatt == null || uartIndicate == null) {
            aapsLogger.error("disconnect is not possible: (mBluetoothAdapter == null) " + (bluetoothAdapter == null))
            aapsLogger.error("disconnect is not possible: (mBluetoothGatt == null) " + (bluetoothGatt == null))
            aapsLogger.error("disconnect is not possible: (uartIndicate == null) " + (uartIndicate == null))
            isConnected = false
            return
        }
        bluetoothGatt?.setCharacteristicNotification(uartIndicate, false)
        bluetoothGatt?.disconnect()
        isConnected = false
        SystemClock.sleep(2000)
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun close() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "BluetoothAdapter close")
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            onConnectionStateChangeSynchronized(gatt, newState) // call it synchronized
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "onServicesDiscovered")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                findCharacteristic()
                SystemClock.sleep(1600)
                isConnected = true
                isConnecting = false
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "(응답) onCharacteristicChanged: " + DiaconnG8Packet.toHex(characteristic.value))
            // 대량로그응답 처리.
            if (characteristic.value[1] == 0xb2.toByte()) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "(대량 로그 처리 응답) onCharacteristicChanged: " + DiaconnG8Packet.toHex(characteristic.value))
                val message = BigLogInquireResponsePacket(injector)
                message.handleMessage(characteristic.value)

                // 초기화
                mSendQueue.clear()
            } else {
                processResponseMessage(characteristic.value)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "(요청) onCharacteristicWrite: " + DiaconnG8Packet.toHex(characteristic.value))
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    @Synchronized
    private fun writeCharacteristicNoResponse(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        Thread(Runnable {
            synchronized(this) {
                SystemClock.sleep(WRITE_DELAY_MILLIS)
                if (bluetoothAdapter == null || bluetoothGatt == null) {
                    aapsLogger.error("BluetoothAdapter not initialized_ERROR")
                    isConnecting = false
                    isConnected = false
                    return@Runnable
                }
                characteristic.value = data
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                bluetoothGatt?.writeCharacteristic(characteristic)
            }
        }).start()
        SystemClock.sleep(50)
    }

    // private val uartIndicateBTGattChar: BluetoothGattCharacteristic
    //     get() = uartIndicate
    //         ?: BluetoothGattCharacteristic(UUID.fromString(INDICATION_UUID), BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0).also { uartIndicate = it }

    private val uartWriteBTGattChar: BluetoothGattCharacteristic
        get() = uartWrite
            ?: BluetoothGattCharacteristic(UUID.fromString(WRITE_UUID), BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, 0).also { uartWrite = it }

    private fun getSupportedGattServices(): List<BluetoothGattService>? {
        aapsLogger.debug(LTag.PUMPBTCOMM, "getSupportedGattServices")
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            aapsLogger.error("BluetoothAdapter not initialized_ERROR")
            isConnecting = false
            isConnected = false
            return null
        }
        return bluetoothGatt?.services
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    @Synchronized
    private fun findCharacteristic() {
        val gattServices = getSupportedGattServices() ?: return
        var uuid: String
        for (gattService in gattServices) {
            val gattCharacteristics = gattService.characteristics
            for (gattCharacteristic in gattCharacteristics) {
                uuid = gattCharacteristic.uuid.toString()
                if (INDICATION_UUID == uuid) {
                    uartIndicate = gattCharacteristic
                    //setCharacteristicNotification(uartIndicate, true)
                    bluetoothGatt?.setCharacteristicNotification(uartIndicate, true)

                    // nRF Connect 참고하여 추가함
                    val descriptor: BluetoothGattDescriptor = uartIndicate!!.getDescriptor(UUID.fromString(CHARACTERISTIC_CONFIG_UUID))
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    bluetoothGatt?.writeDescriptor(descriptor)
                }
                if (WRITE_UUID == uuid) {
                    uartWrite = gattCharacteristic
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun onConnectionStateChangeSynchronized(gatt: BluetoothGatt, newState: Int) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "onConnectionStateChange newState : $newState")
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            gatt.discoverServices()
            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            close()
            isConnected = false
            isConnecting = false
            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
            aapsLogger.debug(LTag.PUMPBTCOMM, "Device was disconnected " + gatt.device.name) //Device was disconnected
        }
    }

    // the rest of packets
    fun sendMessage(message: DiaconnG8Packet, waitMillis: Long) {
        // 펌프로 요청하기전 변수에 담기. 마지막 요청정보 확인용.
        processedMessage = message
        val sequence = getMsgSequence()
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + message.friendlyName + "  Sequence >>" + sequence)
        // 요청
        val bytes = message.encode(sequence)
        processedMessageByte = bytes

        if (bluetoothGatt == null) {
            aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> IGNORING (NOT CONNECTED) " + message.friendlyName)
            return
        }

        aapsLogger.debug(LTag.PUMPBTCOMM, "sendMessage() before mSendQueue.size :: ${mSendQueue.size}")
        // 펌프에 요청 보내기.
        writeCharacteristicNoResponse(uartWriteBTGattChar, bytes)
        // 요청 큐에 요청할 바이트 정보 담기.
        synchronized(mSendQueue) {
            if (mSendQueue.size > 10) mSendQueue.clear()
            mSendQueue.add(bytes)
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "sendMessage() after mSendQueue.size :: ${mSendQueue.size}")

        // 메시지동기화
        synchronized(message) {
            try {
                message.waitMillis(waitMillis) //0.5초 대기.
            } catch (e: InterruptedException) {
                aapsLogger.error("sendMessage InterruptedException", e)
            }
        }
    }

    // process common packet response
    private fun processResponseMessage(data: ByteArray) {
        isConnected = true
        isConnecting = false

        //요청정보
        val originalMessageSeq = processedMessage?.getSeq(processedMessageByte ?: throw IllegalStateException())

        // 응답정보
        val receivedCommand = DiaconnG8Packet(injector).getCmd(data)
        val receivedSeq = DiaconnG8Packet(injector).getSeq(data)
        val receivedType = DiaconnG8Packet(injector).getType(data)

        aapsLogger.debug(LTag.PUMPBTCOMM, "originalMessageSeq :: $originalMessageSeq, receivedSeq :: $receivedSeq")
        aapsLogger.debug(LTag.PUMPBTCOMM, "receivedCommand :: $receivedCommand")

        // 응답메시지가 조회응답인지. 설정응답인지 구분해야됨.
        var message: DiaconnG8Packet? = null
        // 요청시퀀스와 응답의 시퀀스가 동일한 경우에만 처리.
        // 펌프로부터 받은 보고응답의 경우 처리
        if (receivedType == 3) {
            message = diaconnG8ResponseMessageHashTable.findMessage(receivedCommand)
            // injection Blocked Report
            if (message is InjectionBlockReportPacket) {
                message.handleMessage(data)
                diaconnG8Pump.bolusBlocked = true
                uiInteraction.runAlarm(rh.gs(R.string.injectionblocked), rh.gs(R.string.injectionblocked), app.aaps.core.ui.R.raw.boluserror)
                return
            }
            // battery warning report
            if (message is BatteryWarningReportPacket) {
                message.handleMessage(data)
                uiInteraction.runAlarm(rh.gs(R.string.needbatteryreplace), rh.gs(R.string.batterywarning), app.aaps.core.ui.R.raw.boluserror)
                return
            }

            // insulin lack warning report
            if (message is InsulinLackReportPacket) {
                message.handleMessage(data)
                uiInteraction.runAlarm(rh.gs(R.string.needinsullinreplace), rh.gs(R.string.insulinlackwarning), app.aaps.core.ui.R.raw.boluserror)
                return
            }

        } else {
            // 큐에 담긴 명령 대기중에 있는 것 조회 처리.
            synchronized(mSendQueue) {
                val sendQueueSize = mSendQueue.size
                if (sendQueueSize > 0) {
                    for (i in sendQueueSize - 1 downTo 0) {
                        val sendQueueSeq = DiaconnG8Packet(injector).getSeq(mSendQueue[i])
                        val sendQueueType = DiaconnG8Packet(injector).getType(mSendQueue[i])
                        if (sendQueueSeq == receivedSeq) {
                            // 설정명령에 대한 응답
                            if (sendQueueType == 0) {
                                message = diaconnG8SettingResponseMessageHashTable.findMessage(receivedCommand)

                                // 조회명령에 대한 응답
                            } else if (sendQueueType == 1) {
                                message = diaconnG8ResponseMessageHashTable.findMessage(receivedCommand)
                            }
                            // 처리 후 큐에서 삭제.
                            mSendQueue.removeAt(i)
                            break
                        }
                    }
                }
            }
        }

        message?.let {
            aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + it.friendlyName + " " + DiaconnG8Packet.toHex(data))
            // process received data
            it.handleMessage(data)
            it.setReceived()
            synchronized(it) {
                // notify to sendMessage
                it.notifyAll()
            }
        } ?: aapsLogger.error("Unknown message received " + DiaconnG8Packet.toHex(data))
    }
}
