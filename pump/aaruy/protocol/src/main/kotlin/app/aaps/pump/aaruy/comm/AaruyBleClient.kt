package app.aaps.pump.aaruy.comm

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Talks to the pump. Turns commands into encrypted packets and pump messages into [AaruyBleListener] calls.
 *
 * Recovered from the vendor library `mylibrary-release.aar`. It should do the same things in the same order.
 * Commands that the driver did not use are left out.
 */
class AaruyBleClient(
    context: Context,
    private val aapsLogger: AAPSLogger,
    private val transport: AaruyBleTransport,
    private val testLooper: Looper? = null
) {

    /**
     * All pump events run here. Not the main thread: the pump must not wait for the screen, and the screen must not wait for the pump.
     * It starts at the first use, as this class exists also for users of other pumps.
     */
    val eventLooper: Looper by lazy { testLooper ?: HandlerThread("AaruyPump").apply { start() }.looper }

    private val appContext = context
    private val link = PumpLink()
    private val scanner = AaruyScanner()

    private var prepareStartBolus = false
    private var prepareStartBasal = false
    private var currentStatus: CurrentStatusPacket? = null
    private var needRestoreUnRunState = false
    private var isSmbType = false
    private var pumpVersionNum = ""

    /** Sent by the pump when it reports the device. It changes the size of the control packets. */
    private var protocolVersion = 0

    private val runningStatus: Int? get() = currentStatus?.runningStatus?.toInt()

    private val bondReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            aapsLogger.debug(LTag.PUMPBTCOMM, "onReceive action=$action")
            when (action) {
                BluetoothAdapter.ACTION_STATE_CHANGED     -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0)
                    aapsLogger.debug(LTag.PUMPBTCOMM, "blueState=$state")
                    // Seen on a real phone: no GATT event comes for this, and the driver stays connected to a dead link.
                    if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) link.onAdapterOff()
                }

                BluetoothDevice.ACTION_FOUND              -> onDeviceFound(device)
                BluetoothDevice.ACTION_PAIRING_REQUEST    -> onPairingRequest(device, intent)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun onDeviceFound(device: BluetoothDevice?) {
        val name = device?.name ?: return
        if (!name.contains(DEVICE_NAME, ignoreCase = true)) return
        when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> bindDevice(device)
            BluetoothDevice.BOND_NONE   ->
                try {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "attempt to bond: $name")
                    device.createBond()
                } catch (e: Exception) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "createBond failed", e)
                }
        }
    }

    @SuppressLint("MissingPermission")
    private fun BroadcastReceiver.onPairingRequest(device: BluetoothDevice?, intent: Intent) {
        val name = device?.name ?: return
        if (!name.contains(DEVICE_NAME, ignoreCase = true)) return
        val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, Int.MIN_VALUE)
        try {
            if (setPairingPin(device, name, variant)) {
                abortBroadcast()
                bindDevice(device)
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPBTCOMM, "pairing failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun setPairingPin(device: BluetoothDevice, name: String, variant: Int): Boolean {
        val pin = pinFromDeviceName(name).toByteArray(Charsets.UTF_8)
        val success = when (variant) {
            BluetoothDevice.PAIRING_VARIANT_PIN                   -> device.setPin(pin)

            PAIRING_VARIANT_PASSKEY_CONFIRMATION                  -> {
                device.setPairingConfirmation(true)
                device.setPin(pin)
            }

            else                                                  -> false
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "set pin variant=$variant success=$success")
        return success
    }

    private fun registerBondReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        appContext.registerReceiver(bondReceiver, filter)
    }

    fun scanDevice(listener: ScanResultListener) {
        aapsLogger.info(LTag.PUMPBTCOMM, "scanDevice")
        scanner.start()
        scanner.listener = listener
    }

    fun stopScanDevice() = scanner.stop()

    @SuppressLint("MissingPermission")
    fun bindDevice(device: BluetoothDevice) {
        registerBondReceiver()
        // The bond state of the transport comes first: the emulator has a bond, though Android knows no such device.
        if (!transport.adapter.isDeviceBonded(device.address) && device.bondState == BluetoothDevice.BOND_NONE) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "createBond")
            device.createBond()
        } else {
            link.connectDevice(device.address)
        }
    }

fun setOnBleListener(listener: AaruyBleListener) {
        link.listener = listener
    }

    fun getOnBleListener(): AaruyBleListener? = link.listener

    fun getPumpVersionNum(): String {
        link.readVersion()
        SystemClock.sleep(500L)
        return pumpVersionNum
    }

    fun setupBasicParamInfo(param: BasicParam) {
        val pumpType = link.pumpType
        val packet = BasicParamPacket().apply {
            settingTime = AaruyPumpTime.now()
            bolusStep = param.bolusStep.toByte()
            closeInjectSound = param.closeInjectSound.toByte()
            lowAlert = param.lowAlert.toLong().toShort()
            maxBasal = param.maxBasal.toThousandths()
            maxBolus = param.maxBolus.toThousandths()
            this.pumpType = pumpType.toByte()
            bInit = 1
            bolusDelayState = if ((pumpType and 0x7F) != 0 && (pumpType and 0x7F) != 2) 1 else 0
            bolusInc = 2
            fastBolusStep = 0
            originBasal = 0.5.toThousandths()
            tempBasalState = 1
            precision = 25
            noOperateWarning = 0
            noRunWarning = 1
            softNeedleType = 0
            storageCapacity = 0
            dailyLimit = 0
            timeZoneCorrection = 0
        }
        link.write(AaruyParamId.BASIC_PARAM, packet.toBytes())
    }

    fun setupBasalInfo(basalInfo: BasalInfo) {
        val info = BasalInfoPacket()
        basalInfo.basalItems?.let { items ->
            info.segment = 0
            info.injectPerDay = (items.injectPerDay * 10).toInt().toShort()
            info.totalArea = items.totalArea.toByte()
            info.areaBytes = items.basalItemList.map { item ->
                BasalAreaPacket().apply {
                    startTime = item.startTime.toByte()
                    inject = item.basalSpeed.toThousandths()
                }.toBytes()
            }
        }
        val packet = BasalSettingPacket().apply {
            settingTime = AaruyPumpTime.now()
            totalBasal = 3
            curIndex = 0
            basalInfoBytes = info.toBytes()
        }
        link.write(AaruyParamId.BASAL_SETTING, packet.toBytes())
    }

    fun confirmReply(errorType: Int) {
        val type = errorType.toByte()
        val value: Byte = if (type.toInt() != 1 && type.toInt() != 3) 1 else 0
        link.write(AaruyParamId.CONFIRM_WARN, byteArrayOf(value))
    }

    fun startBasal() {
        when (val status = runningStatus) {
            1    -> Unit

            8    ->
                if (needRestoreUnRunState) {
                    needRestoreUnRunState = false
                    restoreUnRunState()
                } else {
                    link.listener?.onRunReplaceFirst(status)
                }

            0    -> {
                prepareStartBasal = false
                link.write(AaruyParamId.BASAL_CONTROL, BasalControlPacket(AaruyPumpTime.now(), 1).toBytes())
            }

            7    -> continueInject()

            else -> Unit
        }
    }

    /**
     * The bolus is sent in two steps: first the setting, then the start command when the pump has
     * taken the setting. See `onWriteDone`.
     *
     * @param now units to give now
     * @param delayed units to give as an extended bolus
     * @param delayMinutes length of the extended bolus, in steps of 30 minutes
     */
    fun setupAndStartBolus(now: Double, delayed: Double, delayMinutes: Int, isSmb: Boolean) {
        val status = runningStatus
        isSmbType = isSmb
        val listener = link.listener
        when {
            status == 0                                                -> listener?.onRunBasalFirst(status)
            status == 7                                                -> listener?.onRunReplaceFirst(status)
            delayed > 0.0 && delayMinutes > 0 && delayMinutes % 30 != 0 -> listener?.onInjectTimeError(delayMinutes)

            else                                                       -> {
                val total = delayed + now
                val maxBolus = link.maxBolus
                if (total > maxBolus) {
                    listener?.onBolusValueTooHigh(total, maxBolus)
                } else if (status == 1 || status == 2) {
                    prepareStartBolus = true
                    val packet = BolusSettingPacket().apply {
                        bolus = total.toThousandths()
                        bolusNow = now.toThousandths()
                        bolusDelayTime = delayMinutes.toShort()
                    }
                    link.write(AaruyParamId.BOLUS_SETTING, packet.toBytes())
                }
            }
        }
    }

    private fun startBolus() {
        val listener = link.listener
        when (val status = runningStatus) {
            1, 2 -> {
                val type: Byte = if (isSmbType) 2 else 0
                isSmbType = false
                link.write(AaruyParamId.BOLUS_CONTROL, ControlPacket(AaruyPumpTime.now(), 1, type).toBytes(protocolVersion))
            }

            3    -> listener?.onBolusIsRunning(status)
            else -> listener?.onRunBasalFirst(status ?: UNKNOWN_STATUS)
        }
    }

    fun cancelBolus() {
        link.write(AaruyParamId.BOLUS_CONTROL, ControlPacket(AaruyPumpTime.now(), 0).toBytes(protocolVersion))
    }

    fun startTempBasal(rate: Double, durationMinutes: Int) {
        val listener = link.listener
        if (durationMinutes > 0 && durationMinutes % 30 != 0) {
            listener?.onInjectTimeError(durationMinutes)
            return
        }
        when (val status = runningStatus) {
            7    -> listener?.onRunReplaceFirst(status)
            1    -> link.write(
                AaruyParamId.TEMP_BASAL_CONTROL,
                TempBasalControlPacket(AaruyPumpTime.now(), 1, rate.toThousandths(), durationMinutes.toShort()).toBytes()
            )

            else -> listener?.onRunBasalFirst(status ?: UNKNOWN_STATUS)
        }
    }

    fun cancelTempBasal() {
        when (val status = runningStatus) {
            2    -> link.write(AaruyParamId.TEMP_BASAL_CONTROL, TempBasalControlPacket(AaruyPumpTime.now(), 0, 0, 0).toBytes())
            else -> link.listener?.onRunTempBasalFirst(status ?: UNKNOWN_STATUS)
        }
    }

    fun setClosedMode(mode: Int) {
        link.write(AaruyParamId.CLOSED_MODE, byteArrayOf(if (mode == 0) 0 else 1))
    }

    fun continueInject() {
        val listener = link.listener
        when (val status = runningStatus) {
            8    -> if (needRestoreUnRunState) restoreUnRunState() else listener?.onRunReplaceFirst(status)
            0, 7 -> sendTotalControl(AaruyTotalControl.CONTINUE)
            else -> listener?.onCurrentNoSuspend(status ?: UNKNOWN_STATUS)
        }
    }

    private fun restoreUnRunState() {
        prepareStartBasal = true
        sendTotalControl(AaruyTotalControl.RESTORE_UN_RUN)
    }

    fun pushPosition() = sendTotalControl(AaruyTotalControl.PUSH_POSITION)

    fun replaceDevice() {
        needRestoreUnRunState = true
        sendTotalControl(AaruyTotalControl.REPLACE)
    }

    fun needlePushAir(softNeedleType: Byte) = sendTotalControl(AaruyTotalControl.PUSH_AIR, softNeedleType)

    private fun sendTotalControl(action: Byte, extra: Byte = 0) {
        link.write(AaruyParamId.TOTAL_CONTROL, ControlPacket(AaruyPumpTime.now(), action, extra).toBytes(protocolVersion))
    }

    fun getHistoryDailyData(lastTime: Long) {
        val start = if (lastTime > AaruyPumpTime.BASE_TIME_MS) ((lastTime - AaruyPumpTime.BASE_TIME_MS) / (AaruyPumpTime.DAY_SECONDS * 1000)).toInt() else 0
        link.write(AaruyParamId.HISTORY_REQUEST, HistoryRequestPacket(start, 0, 1).toBytes())
    }

    fun getHistoryLogData(lastTime: Long) {
        val start = if (lastTime > AaruyPumpTime.BASE_TIME_MS) ((lastTime - AaruyPumpTime.BASE_TIME_MS) / 1000).toInt() else 0
        link.write(AaruyParamId.HISTORY_REQUEST, HistoryRequestPacket(start, 0, 0).toBytes())
    }

    /** As in the vendor library, this does not drop the link. It only made a request and never sent it. */
    fun disconnectDevice() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "disconnectDevice: the link stays up")
    }

    fun unbindDevice() {
        link.write(AaruyParamId.UNBIND, byteArrayOf(1))
    }

    fun updatePumpTime() = link.syncTime()

    fun getCurStatus(): Int? {
        link.read(AaruyParamId.CURRENT_STATUS)
        link.read(AaruyParamId.REAL_DATA)
        return runningStatus
    }

    private class Request(val paramId: Short, val isRead: Boolean, val data: ByteArray = ByteArray(0))

    /**
     * The connection and the command queue. Runs the steps the vendor library got from its BLE library:
     * connect with a timeout and retries, wait, discover services, ask for a large MTU, enable notifications.
     * Events come from any thread and are handled one by one on the pump thread, in the order they came.
     */
    private inner class PumpLink : BleTransportListener, AaruyTransportExtras {

        private val events by lazy { Handler(eventLooper) }
        private val connectTimeout = Runnable { onConnectFailed("timeout") }
        private val setupTimeout = Runnable { onConnectFailed("setup timeout") }
        private var address: String? = null
        private var retriesLeft = 0
        private var servicesFound = false

        /** From the first connect until the setup ended, either way. Closing the GATT in this time also ends a running pairing. */
        private var connecting = false

        /** Commands may be sent. It is set when the setup is complete. */
        private var ready = false
        private val sendLock = Any()
        private var sequence: Short = 0
        private var writing = false
        private val queue = ConcurrentLinkedQueue<Request>()
        private val inFlight = ConcurrentLinkedQueue<Short>()
        private var status = BleStatus.DISCONNECTED

        var listener: AaruyBleListener? = null
        var maxBolus = 0.0
        var pumpType = DEFAULT_PUMP_TYPE

        fun connectDevice(address: String) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "connectDevice status=$status")
            this.address = address
            if (status == BleStatus.CONNECTED) {
                listener?.onBleStatusChanged(status)
                return
            }
            if (connecting) return
            connecting = true
            events.removeCallbacks(connectTimeout)
            status = BleStatus.CONNECTING
            listener?.onBleStatusChanged(status)
            retriesLeft = CONNECT_RETRIES
            startConnect()
        }

        private fun startConnect() {
            val target = address ?: return
            servicesFound = false
            transport.setListener(this)
            transport.setExtrasListener(this)
            events.removeCallbacks(connectTimeout)
            events.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
            if (!transport.gatt.connect(target)) onConnectFailed("connect refused")
        }

        /** The status stays CONNECTING: the driver asks to connect again, as it did with the vendor library. */
        private fun onConnectFailed(reason: String) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "connect failed: $reason, retries left: $retriesLeft")
            events.removeCallbacks(connectTimeout)
            events.removeCallbacks(setupTimeout)
            transport.gatt.close()
            if (retriesLeft > 0) {
                retriesLeft--
                events.postDelayed({ startConnect() }, CONNECT_RETRY_DELAY_MS)
            } else {
                connecting = false
            }
        }

        override fun onConnectionStateChanged(connected: Boolean) {
            events.post {
                if (connected) onConnected() else onDisconnected()
            }
        }

        private fun onConnected() {
            events.removeCallbacks(connectTimeout)
            events.removeCallbacks(setupTimeout)
            events.postDelayed(setupTimeout, SETUP_TIMEOUT_MS)
            val bonded = address?.let { transport.adapter.isDeviceBonded(it) } == true
            events.postDelayed({ transport.gatt.discoverServices() }, if (bonded) DISCOVERY_DELAY_BONDED_MS else DISCOVERY_DELAY_MS)
        }

        fun onAdapterOff() {
            events.post { if (status == BleStatus.CONNECTED || connecting) onDisconnected() }
        }

        private fun onDisconnected() {
            aapsLogger.debug(LTag.PUMPBTCOMM, "disconnected, servicesFound=$servicesFound")
            transport.gatt.close()
            if (!servicesFound) {
                onConnectFailed("link lost during setup")
                return
            }
            events.removeCallbacks(setupTimeout)
            connecting = false
            servicesFound = false
            ready = false
            writing = false
            inFlight.clear()
            status = BleStatus.DISCONNECTED
            listener?.onBleStatusChanged(status)
        }

        override fun onServicesDiscovered(success: Boolean) {
            events.post {
                if (success && transport.gatt.findCharacteristics()) {
                    servicesFound = true
                    transport.requestMtu(REQUESTED_MTU)
                } else {
                    aapsLogger.error(LTag.PUMPBTCOMM, "The pump services are missing")
                    transport.gatt.disconnect()
                }
            }
        }

        override fun onMtuChanged(mtu: Int) {
            events.post {
                aapsLogger.debug(LTag.PUMPBTCOMM, "mtu: $mtu")
                transport.gatt.enableNotifications()
            }
        }

        override fun onDescriptorWritten() {
            events.post { onReady() }
        }

        private fun onReady() {
            events.removeCallbacks(setupTimeout)
            connecting = false
            status = BleStatus.CONNECTED
            ready = true
            sendReportDevice()
            CoroutineScope(Dispatchers.IO).launch {
                delay(500L)
                listener?.onBleStatusChanged(status)
            }
        }

        override fun onCharacteristicChanged(data: ByteArray) {
            events.post { handleFrame(data) }
        }

        override fun onCharacteristicWritten() {
            events.post { inFlight.poll()?.let { onWriteDone(it) } }
        }

        override fun onWriteFailed() {
            events.post { inFlight.poll()?.let { onWriteFailed(it) } }
        }

        override fun onFirmwareVersionRead(data: ByteArray) {
            if (data.isNotEmpty()) pumpVersionNum = String(data, Charsets.UTF_8)
        }

        fun read(paramId: Short) {
            if (!ready) return
            queue.add(Request(paramId, isRead = true))
            sendNextIfIdle()
        }

        fun write(paramId: Short, data: ByteArray) {
            if (!ready) return
            queue.add(Request(paramId, isRead = false, data = data))
            sendNextIfIdle()
        }

        private fun sendNextIfIdle() {
            synchronized(sendLock) {
                if (!writing) queue.poll()?.let { send(it) }
            }
        }

        fun syncTime() {
            write(AaruyParamId.SYNC_TIME, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(AaruyPumpTime.now()).array())
        }

        private fun sendReportDevice() {
            val packet = ReportDevicePacket().apply {
                deviceType = 7
                version = 1
                settingTime = AaruyPumpTime.now()
            }
            write(AaruyParamId.REPORT_DEVICE, packet.toBytes())
        }

        fun readVersion() = transport.readFirmwareVersion()

        /** Writes come from the pump thread and from coroutines. The pump refuses a frame whose sequence is out of order. */
        private fun send(request: Request) {
            synchronized(sendLock) {
                sequence = if (sequence.unsigned() >= 0xFFFF) 0 else (sequence.unsigned() + 1).toShort()
                val frame =
                    if (request.isRead) AaruyFrame.buildRead(request.paramId, sequence)
                    else AaruyFrame.buildWrite(request.paramId, sequence, request.data)
                aapsLogger.debug(LTag.PUMPBTCOMM, "TX ${if (request.isRead) "read" else "write"} param=${request.paramId} seq=${sequence.unsigned()} data=${if (request.isRead) "" else request.data.toHexText()}")
                writing = true
                inFlight.add(request.paramId)
                transport.gatt.writeCharacteristic(frame)
            }
        }

        private fun onWriteDone(paramId: Short) {
            if (paramId == AaruyParamId.UNBIND) {
                address?.let { unbound ->
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(3000L)
                        transport.adapter.removeBond(unbound)
                    }
                }
            } else if (paramId == AaruyParamId.BOLUS_SETTING && prepareStartBolus) {
                prepareStartBolus = false
                startBolus()
            }
            writing = false
            listener?.onRequestResult(true, paramId.unsigned())
            queue.poll()?.let { send(it) }
        }

        private fun onWriteFailed(paramId: Short) {
            writing = false
            listener?.onRequestResult(false, paramId.unsigned())
        }

        private fun handleFrame(frame: ByteArray) {
            try {
                val message = AaruyFrame.parse(frame)
                if (message == null) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "RX frame not made of full blocks: ${frame.toHexText()}")
                    return
                }
                aapsLogger.debug(LTag.PUMPBTCOMM, "RX param=${message.paramId} data=${message.payload.toHexText()}")
                if (message.paramId != AaruyParamId.SERIAL) queue.poll()?.let { send(it) }
                handleMessage(message.paramId, message.payload)
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMPBTCOMM, "Failed to handle a pump message", e)
            }
        }

        private fun handleMessage(paramId: Short, payload: ByteArray) {
            when (paramId) {
                AaruyParamId.REPORT_DEVICE                                 -> handleReportDevice(payload)

                AaruyParamId.SERIAL                                        ->
                    if (payload.size == 2) synchronized(sendLock) { sequence = AaruyByteReader(payload).u16() }

                AaruyParamId.BASIC_PARAM                                   -> {
                    val param = BasicParamPacket.parse(payload).toBasicParam()
                    maxBolus = param.maxBolus
                    pumpType = param.pumpType
                    listener?.onReceiveBasicParamSetting(param)
                }

                AaruyParamId.BASAL_SETTING                                 ->
                    listener?.onReceiveBasalSetting(BasalSettingPacket.parse(payload).toBasalInfo())

                AaruyParamId.CURRENT_STATUS                                -> {
                    val packet = CurrentStatusPacket.parse(payload)
                    currentStatus = packet
                    if (packet.runningStatus.toInt() == 0 && prepareStartBasal) {
                        prepareStartBasal = false
                        startBasal()
                    }
                    listener?.onReceiveCurrentStatus(packet.toStatusInfo())
                }

                AaruyParamId.HISTORY_LOG                                   ->
                    listener?.onReceiveHistoryLog(HistoryLogPacket.parse(payload).toLogInfo())

                AaruyParamId.HISTORY_DAILY                                 ->
                    listener?.onReceiveHistoryDaily(HistoryDailyPacket.parse(payload).toDailyInfo())

                AaruyParamId.REAL_DATA                                     -> {
                    val packet = RealDataPacket.parse(payload)
                    listener?.onReceiveRealData(packet.remainBattery.unsigned(), packet.bleSignal)
                }

                AaruyParamId.CLOSED_MODE                                   ->
                    listener?.onPumpClosedMode(AaruyByteReader(payload).u8())

                AaruyParamId.ERROR_INFO                                    -> handleError(payload)

                AaruyParamId.CONFIRM_WARN                                  ->
                    listener?.onReceiveConfirmWarn(AaruyByteReader(payload).u8())

                // The pump also sends these. Nothing is done with them.
                AaruyParamId.BASAL_SETTING_TWO, AaruyParamId.BASAL_SETTING_THREE, AaruyParamId.BOLUS_SETTING,
                AaruyParamId.PARAM_MINUS_20, AaruyParamId.PARAM_MINUS_21, AaruyParamId.PARAM_19 -> Unit
            }
        }

        private fun handleReportDevice(payload: ByteArray) {
            val packet = ReportDevicePacket.parse(payload)
            synchronized(sendLock) { sequence = packet.serial }
            protocolVersion = packet.version.toInt()
            if (packet.deviceType.toInt() == REPORT_DEVICE_ACCEPTED) {
                syncTime()
            } else {
                listener?.onUnbindClearInfo()
                unbindDevice()
            }
        }

        private fun handleError(payload: ByteArray) {
            val packet = ErrorInfoPacket.parse(payload)
            if (packet.error == 0 && packet.errorType == ERROR_TYPE_LIQUID_SHOW) listener?.onLiquidShow()
            if (packet.error == ERROR_OPERATION && packet.errorType == ERROR_TYPE_READ_SERIAL) read(AaruyParamId.SERIAL)
            listener?.onReceiveErrorData(packet.errorType, packet.error)
        }
    }

    companion object {

        private const val CONNECT_TIMEOUT_MS = 15_000L

        /** Pairing waits for a button on the pump, and Android gives up on it after 30 s. */
        private const val SETUP_TIMEOUT_MS = 45_000L
        private const val CONNECT_RETRIES = 2
        private const val CONNECT_RETRY_DELAY_MS = 1_000L
        private const val DISCOVERY_DELAY_MS = 300L
        private const val DISCOVERY_DELAY_BONDED_MS = 1_600L
        private const val REQUESTED_MTU = 517
        private const val DEVICE_NAME = "aaruy"
        private const val DEFAULT_PIN = "210101"
        private const val DEFAULT_PUMP_TYPE = 131
        private const val REPORT_DEVICE_ACCEPTED = 6
        private const val ERROR_TYPE_LIQUID_SHOW = 25
        private const val ERROR_TYPE_READ_SERIAL = 28
        private const val ERROR_OPERATION = 128

        /** Hidden constant of `BluetoothDevice`. */
        private const val PAIRING_VARIANT_PASSKEY_CONFIRMATION = 2

        /** Used only so that a missing status is logged and does not crash. */
        private const val UNKNOWN_STATUS = -1


        private fun pinCode(char: Char): Char = when (char) {
            '0'  -> '3'
            '1'  -> '7'
            '2'  -> '6'
            '3'  -> '2'
            '4'  -> '1'
            '5'  -> '8'
            '6'  -> '9'
            '7'  -> '0'
            '8'  -> '5'
            '9'  -> '4'
            'Q'  -> '5'
            'R'  -> '6'
            'S'  -> '0'
            'T'  -> '1'
            'U'  -> '2'
            'V'  -> '3'
            'W'  -> '4'
            'X'  -> '9'
            'Y'  -> '8'
            'Z'  -> '7'
            else -> ' '
        }

        /** The PIN comes from the six characters after `aaruy_` in the device name. */
        internal fun pinFromDeviceName(name: String): String {
            if (name.trim().length !in 12 until 18) return DEFAULT_PIN
            val pin = name.substring(6, 12).map { pinCode(it) }.joinToString("")
            return if (pin.trim().length < 6) DEFAULT_PIN else pin
        }
    }
}

private fun ByteArray.toHexText(): String = joinToString(" ") { "%02X".format(Locale.ROOT, it) }

/** The encrypted frame around each packet. */
/** The AES key of the BLE frames. The emulator needs it too. */
fun aaruyFrameKey(): ByteArray = AaruyFrame.key.copyOf()

internal object AaruyFrame {

    class Message(val paramId: Short, val payload: ByteArray)

    private const val ALGORITHM = "AES/ECB/PKCS5Padding"
    private const val FRAME_START: Byte = 0x55
    private const val OUTER_HEADER = 3
    private const val INNER_HEADER = 7
    private const val INNER_HEADER_AFTER_LENGTH = 5
    private const val COMMAND_READ: Byte = 0
    private const val COMMAND_WRITE: Byte = 1
    private const val BLOCK_SIZE = 16

    val key = byteArrayOf(65, 97, 114, 117, 121, 121, 105, 108, 105, 97, 111, 49, 50, 51, 52, 53)

    private fun cipher(mode: Int): Cipher = Cipher.getInstance(ALGORITHM).apply { init(mode, SecretKeySpec(key, "AES")) }

    private fun wrap(plain: ByteArray): ByteArray {
        val encrypted = cipher(Cipher.ENCRYPT_MODE).doFinal(plain)
        return ByteBuffer.allocate(encrypted.size + OUTER_HEADER).order(ByteOrder.LITTLE_ENDIAN)
            .put(FRAME_START)
            .putShort(encrypted.size.toShort())
            .put(encrypted)
            .array()
    }

    private fun inner(length: Short, command: Byte, paramId: Short, sequence: Short, payload: ByteArray): ByteArray =
        ByteBuffer.allocate(INNER_HEADER + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(length)
            .put(command)
            .putShort(paramId)
            .putShort(sequence)
            .put(payload)
            .array()

    fun buildRead(paramId: Short, sequence: Short): ByteArray =
        wrap(inner(INNER_HEADER_AFTER_LENGTH.toShort(), COMMAND_READ, paramId, sequence, ByteArray(0)))

    fun buildWrite(paramId: Short, sequence: Short, payload: ByteArray): ByteArray =
        wrap(inner((payload.size + INNER_HEADER_AFTER_LENGTH).toShort(), COMMAND_WRITE, paramId, sequence, payload))

    /** @return null if the frame is not made of full blocks */
    fun parse(frame: ByteArray): Message? {
        val body = AaruyByteReader.slice(frame, OUTER_HEADER, frame.size - OUTER_HEADER)
        if (body.size % BLOCK_SIZE != 0) return null
        val plain = cipher(Cipher.DECRYPT_MODE).doFinal(body)
        val reader = AaruyByteReader(plain)
        reader.u16()
        reader.u8()
        val paramId = reader.u16()
        return Message(paramId, AaruyByteReader.slice(plain, INNER_HEADER, plain.size - INNER_HEADER))
    }
}
