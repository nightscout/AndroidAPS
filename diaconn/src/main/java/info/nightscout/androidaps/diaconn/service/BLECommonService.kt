package info.nightscout.androidaps.diaconn.service

import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.androidaps.diaconn.R
import info.nightscout.androidaps.diaconn.packet.*
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.extensions.notify
import info.nightscout.androidaps.extensions.waitMillis
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.util.*
import java.util.concurrent.ScheduledFuture
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BLECommonService @Inject internal constructor(
    private val injector: HasAndroidInjector,
    private val aapsLogger: AAPSLogger,
    private val resourceHelper: ResourceHelper,
    private val context: Context,
    private val rxBus: RxBusWrapper,
    private val diaconnG8ResponseMessageHashTable: DiaconnG8ResponseMessageHashTable,
    private val diaconnG8SettingResponseMessageHashTable: DiaconnG8SettingResponseMessageHashTable,
    private val diaconnG8Pump: DiaconnG8Pump,
){

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
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var connectDeviceName: String? = null
    private var bluetoothGatt: BluetoothGatt? = null


    var isConnected = false
    var isConnecting = false
    private var uartIndicate: BluetoothGattCharacteristic? = null
    private var uartWrite: BluetoothGattCharacteristic? = null

    private var mSequence : Int = 0

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
        aapsLogger.debug(LTag.PUMPBTCOMM, "Initializing Bluetooth ")
        if (bluetoothManager == null) {
            bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            if (bluetoothManager == null) {
                aapsLogger.error("Unable to initialize BluetoothManager.")
                return false
            }
        }
        bluetoothAdapter = bluetoothManager?.adapter
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
        aapsLogger.debug(LTag.PUMPBTCOMM, "disconnect from: $from")

        // cancel previous scheduled disconnection to prevent closing upcoming connection
        scheduledDisconnection?.cancel(false)
        scheduledDisconnection = null

        if (bluetoothAdapter == null || bluetoothGatt == null) {
            aapsLogger.error("disconnect is not possible: (mBluetoothAdapter == null) " + (bluetoothAdapter == null))
            aapsLogger.error("disconnect is not possible: (mBluetoothGatt == null) " + (bluetoothGatt == null))
            return
        }
        bluetoothGatt?.setCharacteristicNotification(uartIndicate, false)
        bluetoothGatt?.disconnect()
        isConnected = false
        SystemClock.sleep(2000)
    }

    @Synchronized
    fun close() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "BluetoothAdapter close")
        bluetoothGatt?.close()
        bluetoothGatt = null
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
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "(응답) onCharacteristicChanged: " + DiaconnG8Packet.toHex(characteristic.value))
            // 대량로그응답 처리.
            if(characteristic.value[1] == 0xb2.toByte()) {
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

    @Synchronized
    private fun writeCharacteristicNoResponse(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        Thread(Runnable {
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
                    bluetoothGatt!!.writeDescriptor(descriptor)
                }
                if (WRITE_UUID == uuid) {
                    uartWrite = gattCharacteristic
                }
            }
        }
    }

    @Synchronized
    private fun onConnectionStateChangeSynchronized(gatt: BluetoothGatt, newState: Int) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "onConnectionStateChange newState : $newState")
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            gatt.discoverServices()
            isConnected = true
            isConnecting = false
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

        aapsLogger.debug(LTag.PUMPBTCOMM, "sendMessage() before mSendQueue.size :: ${mSendQueue.size}")
        // 펌프에 요청 보내기.
        writeCharacteristicNoResponse(uartWriteBTGattChar, bytes)
        // 요청 큐에 요청할 바이트 정보 담기.
        synchronized(mSendQueue) {
            if(mSendQueue.size > 10) mSendQueue.clear()
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

        //요청정보
        val originalMessageSeq = processedMessage?.getSeq(processedMessageByte)

        // 응답정보
        val receivedCommand = DiaconnG8Packet(injector).getCmd(data)
        val receivedSeq = DiaconnG8Packet(injector).getSeq(data)
        val receivedType = DiaconnG8Packet(injector).getType(data)

        aapsLogger.debug(LTag.PUMPBTCOMM, "originalMessageSeq :: $originalMessageSeq, receivedSeq :: $receivedSeq")
        aapsLogger.debug(LTag.PUMPBTCOMM, "receivedCommand :: $receivedCommand")

        // 응답메시지가 조회응답인지. 설정응답인지 구분해야됨.
        var message:DiaconnG8Packet? = null
        // 요청시퀀스와 응답의 시퀀스가 동일한 경우에만 처리.
        // 펌프로부터 받은 보고응답의 경우 처리
        if(receivedType == 3) {
            message = diaconnG8ResponseMessageHashTable.findMessage(receivedCommand)
            // injection Blocked Report
            if(message is InjectionBlockReportPacket ) {
                message.handleMessage(data)
                diaconnG8Pump.bolusBlocked = true
                val i = Intent(context, ErrorHelperActivity::class.java)
                i.putExtra("soundid", R.raw.boluserror)
                i.putExtra("status", resourceHelper.gs(R.string.injectionblocked))
                i.putExtra("title", resourceHelper.gs(R.string.injectionblocked))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                return
            }
            // battery warning report
            if(message is BatteryWarningReportPacket ) {
                message.handleMessage(data)
                val i = Intent(context, ErrorHelperActivity::class.java)
                i.putExtra("soundid", R.raw.boluserror)
                i.putExtra("status", resourceHelper.gs(R.string.needbatteryreplace))
                i.putExtra("title", resourceHelper.gs(R.string.batterywarning))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                return
            }

            // insulin lack warning report
            if(message is InsulinLackReportPacket ) {
                message.handleMessage(data)
                val i = Intent(context, ErrorHelperActivity::class.java)
                i.putExtra("soundid", R.raw.boluserror)
                i.putExtra("status", resourceHelper.gs(R.string.needinsullinreplace))
                i.putExtra("title", resourceHelper.gs(R.string.insulinlackwarning))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                return
            }

        } else {
            // 큐에 담긴 명령 대기중에 있는 것 조회 처리.
            synchronized(mSendQueue) {
                val sendQueueSize = mSendQueue.size
                if (sendQueueSize > 0) {
                    for (i in sendQueueSize-1 downTo 0 ) {
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

        if (message != null) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< " + message!!.friendlyName + " " + DiaconnG8Packet.toHex(data))
            // process received data
            message!!.handleMessage(data)
            message!!.setReceived()
            synchronized(message!!) {
                // notify to sendMessage
                message!!.notify()
            }
        } else aapsLogger.error("Unknown message received " + DiaconnG8Packet.toHex(data))
    }
}
