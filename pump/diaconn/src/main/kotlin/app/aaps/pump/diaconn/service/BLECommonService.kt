package app.aaps.pump.diaconn.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.pump.ble.BondState
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.utils.notifyAll
import app.aaps.core.utils.waitMillis
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.R
import app.aaps.pump.diaconn.di.DiaconnBle
import app.aaps.pump.diaconn.packet.BatteryWarningReportPacket
import app.aaps.pump.diaconn.packet.BigLogInquirePacket
import app.aaps.pump.diaconn.packet.BigLogInquireResponsePacket
import app.aaps.pump.diaconn.packet.DiaconnG8Packet
import app.aaps.pump.diaconn.packet.DiaconnG8ResponseMessageHashTable
import app.aaps.pump.diaconn.packet.DiaconnG8SettingResponseMessageHashTable
import app.aaps.pump.diaconn.packet.InjectionBlockReportPacket
import app.aaps.pump.diaconn.packet.InsulinLackReportPacket
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Bluetooth link to the Diaconn G8 pump. It sends request packets and passes each answer from the pump to the packet
 * that reads it. The radio is [bleTransport]: the real Bluetooth one, or the in-tree emulator.
 */
@Suppress("SpellCheckingInspection")
@SingleIn(AppScope::class)
@Inject
class BLECommonService internal constructor(
    private val injector: MetroMemberInjector,
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val context: Context,
    private val rxBus: RxBus,
    private val diaconnG8ResponseMessageHashTable: DiaconnG8ResponseMessageHashTable,
    private val diaconnG8SettingResponseMessageHashTable: DiaconnG8SettingResponseMessageHashTable,
    private val diaconnG8Pump: DiaconnG8Pump,
    private val uiInteraction: UiInteraction,
    @param:DiaconnBle private val bleTransport: BleTransport
) {

    private var processedMessage: DiaconnG8Packet? = null
    private var processedMessageByte: ByteArray? = null
    private val mSendQueue = ArrayList<ByteArray>()
    private var connectDeviceName: String? = null

    // A link was opened and not closed yet: requests can be written.
    private var linkOpen = false

    var isConnected = false
    var isConnecting = false

    private var mSequence: Int = 0

    private fun getMsgSequence(): Int {
        val seq = mSequence % 255
        mSequence++
        if (mSequence == 255) {
            mSequence = 0
        }
        return seq
    }

    private val transportListener = object : BleTransportListener {
        override fun onConnectionStateChanged(connected: Boolean) = onConnectionStateChangeSynchronized(connected)

        override fun onServicesDiscovered(success: Boolean) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "onServicesDiscovered")
            if (success) {
                if (bleTransport.gatt.findCharacteristics()) bleTransport.gatt.enableNotifications()
                SystemClock.sleep(1600)
                isConnected = true
                isConnecting = false
            }
        }

        override fun onDescriptorWritten() {}

        override fun onCharacteristicChanged(data: ByteArray) = onAnswer(data)

        override fun onCharacteristicWritten() {}
    }

    init {
        bleTransport.setListener(transportListener)
    }

    @Synchronized
    fun connect(from: String, address: String?): Boolean {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            rxBus.send(EventShowSnackbar(context.getString(app.aaps.core.ui.R.string.need_connect_permission), EventShowSnackbar.Type.Error))
            aapsLogger.error(LTag.PUMPBTCOMM, "missing permission: $from")
            return false
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "Initializing Bluetooth ")
        if (address == null) {
            aapsLogger.error("unspecified address.")
            return false
        }

        if (bleTransport.adapter.bondState(address) == BondState.NONE) {
            bleTransport.adapter.createBond(address)
            SystemClock.sleep(10000)
            return false
        }

        aapsLogger.debug(LTag.PUMPBTCOMM, "Trying to create a new connection from: $from")
        connectDeviceName = bleTransport.adapter.getDeviceName(address)
        if (!bleTransport.gatt.connect(address)) {
            aapsLogger.error("Device not found.  Unable to connect from: $from")
            return false
        }
        linkOpen = true

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
        if (!linkOpen) {
            aapsLogger.error("disconnect is not possible: no link")
            isConnected = false
            return
        }
        bleTransport.gatt.disconnect()
        isConnected = false
        SystemClock.sleep(2000)
    }

    @Synchronized
    fun close() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "BluetoothAdapter close")
        bleTransport.gatt.close()
        linkOpen = false
    }

    // An answer or a report from the pump.
    private fun onAnswer(data: ByteArray) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "(answer) onCharacteristicChanged: " + DiaconnG8Packet.toHex(data))
        // Android drops an exception thrown here and logs it only as a warning of its own, so it is logged here.
        try {
            // handle the big log answer.
            if (data[1] == 0xb2.toByte()) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "(big log answer) onCharacteristicChanged: " + DiaconnG8Packet.toHex(data))
                // The page request that waits for it. The answer has a number of the pump's own, not the request's (real
                // G8: request 54 got 0x27, request 55 got 0x29), so it cannot be matched by number. It woke nothing: each
                // page waited its whole time, and the history read could not tell a read page from a lost one.
                val request = processedMessage as? BigLogInquirePacket
                // Answered now, also when storing its records takes longer than the request waits (the first record of
                // a new pump took 1.8 s on an emulator).
                request?.setReceived()
                val message = BigLogInquireResponsePacket(injector)
                message.handleMessage(data)

                // reset
                mSendQueue.clear()
                request?.let { synchronized(it) { it.notifyAll() } }
            } else {
                processResponseMessage(data)
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPBTCOMM, "A pump message could not be read: " + DiaconnG8Packet.toHex(data), e)
        }
    }

    @Synchronized
    private fun onConnectionStateChangeSynchronized(connected: Boolean) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "onConnectionStateChange connected : $connected")
        if (connected) {
            bleTransport.gatt.discoverServices()
            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
        } else {
            close()
            isConnected = false
            isConnecting = false
            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
            aapsLogger.debug(LTag.PUMPBTCOMM, "Device was disconnected $connectDeviceName") //Device was disconnected
        }
    }

    // the rest of packets
    /** Sends [message] to the pump and waits for its answer, at most [waitMillis]. */
    fun sendMessage(message: DiaconnG8Packet, waitMillis: Long) {
        // keep the request before it goes to the pump, to check the last request later.
        processedMessage = message
        val sequence = getMsgSequence()
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> " + message.friendlyName + "  Sequence >>" + sequence)
        // request
        val bytes = message.encode(sequence)
        processedMessageByte = bytes

        if (!linkOpen) {
            aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> IGNORING (NOT CONNECTED) " + message.friendlyName)
            return
        }

        // The request is in the send queue before it is written: an answer is matched to it there, and one that came
        // before the add was dropped as an unknown message.
        synchronized(mSendQueue) {
            if (mSendQueue.size > 10) mSendQueue.clear()
            mSendQueue.add(bytes)
        }
        bleTransport.gatt.writeCharacteristic(bytes)

        // Waits until the answer is in, at most waitMillis. The answer wakes this request in processResponseMessage. It woke
        // only the answer packet, so each request waited its whole time: 5 s a message, a status read 41 s on a real G8
        // that answered each one in 0.1 s.
        synchronized(message) {
            if (!message.isReceived) {
                try {
                    message.waitMillis(waitMillis)
                } catch (e: InterruptedException) {
                    aapsLogger.error("sendMessage InterruptedException", e)
                }
            }
        }
    }

    // process common packet response
    private fun processResponseMessage(data: ByteArray) {
        isConnected = true
        isConnecting = false

        // request info
        val originalMessageSeq = processedMessage?.getSeq(processedMessageByte ?: throw IllegalStateException())

        // answer info
        val receivedCommand = DiaconnG8Packet(injector).getCmd(data)
        val receivedSeq = DiaconnG8Packet(injector).getSeq(data)
        val receivedType = DiaconnG8Packet(injector).getType(data)

        aapsLogger.debug(LTag.PUMPBTCOMM, "originalMessageSeq :: $originalMessageSeq, receivedSeq :: $receivedSeq")
        aapsLogger.debug(LTag.PUMPBTCOMM, "receivedCommand :: $receivedCommand")

        // must find out if the answer is to an inquiry or to a setting.
        var message: DiaconnG8Packet? = null
        // handled only when the sequence of the request and of the answer are the same.
        // handle a report that the pump sent
        if (receivedType == 3) {
            message = diaconnG8ResponseMessageHashTable.findMessage(receivedCommand)
            // injection Blocked Report
            if (message is InjectionBlockReportPacket) {
                message.handleMessage(data)
                diaconnG8Pump.bolusBlocked = true
                uiInteraction.runAlarm(rh.gs(R.string.injectionblocked), rh.gs(R.string.injectionblocked), AlarmSound.BOLUS_ERROR)
                return
            }
            // battery warning report
            if (message is BatteryWarningReportPacket) {
                message.handleMessage(data)
                uiInteraction.runAlarm(rh.gs(R.string.needbatteryreplace), rh.gs(R.string.batterywarning), AlarmSound.BOLUS_ERROR)
                return
            }

            // insulin lack warning report
            if (message is InsulinLackReportPacket) {
                message.handleMessage(data)
                uiInteraction.runAlarm(rh.gs(R.string.needinsullinreplace), rh.gs(R.string.insulinlackwarning), AlarmSound.BOLUS_ERROR)
                return
            }

        } else {
            // find the waiting request in the send queue.
            synchronized(mSendQueue) {
                val sendQueueSize = mSendQueue.size
                if (sendQueueSize > 0) {
                    for (i in sendQueueSize - 1 downTo 0) {
                        val sendQueueSeq = DiaconnG8Packet(injector).getSeq(mSendQueue[i])
                        val sendQueueType = DiaconnG8Packet(injector).getType(mSendQueue[i])
                        if (sendQueueSeq == receivedSeq) {
                            // answer to a setting command
                            if (sendQueueType == 0) {
                                message = diaconnG8SettingResponseMessageHashTable.findMessage(receivedCommand)

                                // answer to an inquiry command
                            } else if (sendQueueType == 1) {
                                message = diaconnG8ResponseMessageHashTable.findMessage(receivedCommand)
                            }
                            // remove it from the queue after it is handled.
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
            try {
                it.handleMessage(data)
            } catch (e: Exception) {
                // A pump clock at 00:70 threw here on the Bluetooth thread: Android dropped the rest of the answer, with
                // nothing in the AAPS log.
                it.failed = true
                aapsLogger.error(LTag.PUMPBTCOMM, "The answer " + it.friendlyName + " could not be read", e)
            }
            it.setReceived()
            synchronized(it) {
                // notify to sendMessage
                it.notifyAll()
            }
            // The answer is another packet than the request that sendMessage waits on: wake the request it answers.
            val request = processedMessage
            if (receivedType != 3 && request != null && originalMessageSeq == receivedSeq) {
                synchronized(request) {
                    request.setReceived()
                    request.notifyAll()
                }
            }
        } ?: aapsLogger.error("Unknown message received " + DiaconnG8Packet.toHex(data))
    }
}
