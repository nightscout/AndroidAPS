package app.aaps.pump.aaruy.comm

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ConnectRequest
import no.nordicsemi.android.ble.EmulatorGattLink
import no.nordicsemi.android.ble.data.Data
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Talks to the pump. Turns commands into encrypted packets and pump messages into [AaruyBleListener] calls.
 *
 * Recovered from the vendor library `mylibrary-release.aar`. It should do the same things in the same order.
 * Commands that the driver did not use are left out.
 */
class AaruyBleClient(context: Context, private val aapsLogger: AAPSLogger) {

    private val appContext = context
    private val bleManager = PumpBleManager(context)
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
                BluetoothAdapter.ACTION_STATE_CHANGED     ->
                    aapsLogger.debug(LTag.PUMPBTCOMM, "blueState=${intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0)}")

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
        aapsLogger.debug(LTag.PUMPBTCOMM, "set pin success=$success")
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
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "createBond")
            device.createBond()
        } else {
            bleManager.connectDevice(device)
        }
    }

    /** For the emulator: `bindDevice` asks Android to bond an unbonded device, which cannot happen without a radio. */
    fun connectWithoutBonding(device: BluetoothDevice) = bleManager.connectDevice(device)

    fun setOnBleListener(listener: AaruyBleListener) {
        bleManager.listener = listener
    }

    fun getOnBleListener(): AaruyBleListener? = bleManager.listener

    fun getPumpVersionNum(): String {
        bleManager.readVersion()
        SystemClock.sleep(500L)
        return pumpVersionNum
    }

    fun setupBasicParamInfo(param: BasicParam) {
        val pumpType = bleManager.pumpType
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
        bleManager.write(AaruyParamId.BASIC_PARAM, packet.toBytes())
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
        bleManager.write(AaruyParamId.BASAL_SETTING, packet.toBytes())
    }

    fun confirmReply(errorType: Int) {
        val type = errorType.toByte()
        val value: Byte = if (type.toInt() != 1 && type.toInt() != 3) 1 else 0
        bleManager.write(AaruyParamId.CONFIRM_WARN, byteArrayOf(value))
    }

    fun startBasal() {
        when (val status = runningStatus) {
            1    -> Unit

            8    ->
                if (needRestoreUnRunState) {
                    needRestoreUnRunState = false
                    restoreUnRunState()
                } else {
                    bleManager.listener?.onRunReplaceFirst(status)
                }

            0    -> {
                prepareStartBasal = false
                bleManager.write(AaruyParamId.BASAL_CONTROL, BasalControlPacket(AaruyPumpTime.now(), 1).toBytes())
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
        val listener = bleManager.listener
        when {
            status == 0                                                -> listener?.onRunBasalFirst(status)
            status == 7                                                -> listener?.onRunReplaceFirst(status)
            delayed > 0.0 && delayMinutes > 0 && delayMinutes % 30 != 0 -> listener?.onInjectTimeError(delayMinutes)

            else                                                       -> {
                val total = delayed + now
                val maxBolus = bleManager.maxBolus
                if (total > maxBolus) {
                    listener?.onBolusValueTooHigh(total, maxBolus)
                } else if (status == 1 || status == 2) {
                    prepareStartBolus = true
                    val packet = BolusSettingPacket().apply {
                        bolus = total.toThousandths()
                        bolusNow = now.toThousandths()
                        bolusDelayTime = delayMinutes.toShort()
                    }
                    bleManager.write(AaruyParamId.BOLUS_SETTING, packet.toBytes())
                }
            }
        }
    }

    private fun startBolus() {
        val listener = bleManager.listener
        when (val status = runningStatus) {
            1, 2 -> {
                val type: Byte = if (isSmbType) 2 else 0
                isSmbType = false
                bleManager.write(AaruyParamId.BOLUS_CONTROL, ControlPacket(AaruyPumpTime.now(), 1, type).toBytes(protocolVersion))
            }

            3    -> listener?.onBolusIsRunning(status)
            else -> listener?.onRunBasalFirst(status ?: UNKNOWN_STATUS)
        }
    }

    fun cancelBolus() {
        bleManager.write(AaruyParamId.BOLUS_CONTROL, ControlPacket(AaruyPumpTime.now(), 0).toBytes(protocolVersion))
    }

    fun startTempBasal(rate: Double, durationMinutes: Int) {
        val listener = bleManager.listener
        if (durationMinutes > 0 && durationMinutes % 30 != 0) {
            listener?.onInjectTimeError(durationMinutes)
            return
        }
        when (val status = runningStatus) {
            7    -> listener?.onRunReplaceFirst(status)
            1    -> bleManager.write(
                AaruyParamId.TEMP_BASAL_CONTROL,
                TempBasalControlPacket(AaruyPumpTime.now(), 1, rate.toThousandths(), durationMinutes.toShort()).toBytes()
            )

            else -> listener?.onRunBasalFirst(status ?: UNKNOWN_STATUS)
        }
    }

    fun cancelTempBasal() {
        when (val status = runningStatus) {
            2    -> bleManager.write(AaruyParamId.TEMP_BASAL_CONTROL, TempBasalControlPacket(AaruyPumpTime.now(), 0, 0, 0).toBytes())
            else -> bleManager.listener?.onRunTempBasalFirst(status ?: UNKNOWN_STATUS)
        }
    }

    fun setClosedMode(mode: Int) {
        bleManager.write(AaruyParamId.CLOSED_MODE, byteArrayOf(if (mode == 0) 0 else 1))
    }

    fun continueInject() {
        val listener = bleManager.listener
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
        bleManager.write(AaruyParamId.TOTAL_CONTROL, ControlPacket(AaruyPumpTime.now(), action, extra).toBytes(protocolVersion))
    }

    fun getHistoryDailyData(lastTime: Long) {
        val start = if (lastTime > AaruyPumpTime.BASE_TIME_MS) ((lastTime - AaruyPumpTime.BASE_TIME_MS) / (AaruyPumpTime.DAY_SECONDS * 1000)).toInt() else 0
        bleManager.write(AaruyParamId.HISTORY_REQUEST, HistoryRequestPacket(start, 0, 1).toBytes())
    }

    fun getHistoryLogData(lastTime: Long) {
        val start = if (lastTime > AaruyPumpTime.BASE_TIME_MS) ((lastTime - AaruyPumpTime.BASE_TIME_MS) / 1000).toInt() else 0
        bleManager.write(AaruyParamId.HISTORY_REQUEST, HistoryRequestPacket(start, 0, 0).toBytes())
    }

    /** As in the vendor library, the request is made but not enqueued, so the link stays up. */
    fun disconnectDevice() {
        bleManager.disconnect()
    }

    fun unbindDevice() {
        bleManager.write(AaruyParamId.UNBIND, byteArrayOf(1))
    }

    fun updatePumpTime() = bleManager.syncTime()

    fun getCurStatus(): Int? {
        bleManager.read(AaruyParamId.CURRENT_STATUS)
        bleManager.read(AaruyParamId.REAL_DATA)
        return runningStatus
    }

    private class Request(val paramId: Short, val isRead: Boolean, val data: ByteArray = ByteArray(0))

    private inner class PumpBleManager(context: Context) : BleManager(context), EmulatorGattLink.CharacteristicsTarget {

        /** The emulator has no real `BluetoothGatt` for `isRequiredServiceSupported`, so it gives these directly. */
        override fun setCharacteristics(
            notify: BluetoothGattCharacteristic,
            write: BluetoothGattCharacteristic,
            hardware: BluetoothGattCharacteristic,
            firmware: BluetoothGattCharacteristic
        ) {
            notifyCharacteristic = notify
            writeCharacteristic = write
            hardwareCharacteristic = hardware
            firmwareCharacteristic = firmware
        }

        private var notifyCharacteristic: BluetoothGattCharacteristic? = null
        private var writeCharacteristic: BluetoothGattCharacteristic? = null
        private var hardwareCharacteristic: BluetoothGattCharacteristic? = null
        private var firmwareCharacteristic: BluetoothGattCharacteristic? = null
        private var sequence: Short = 0
        private var writing = false
        private val queue = ConcurrentLinkedQueue<Request>()
        private var status = BleStatus.DISCONNECTED
        private var connectRequest: ConnectRequest? = null
        private var device: BluetoothDevice? = null

        var listener: AaruyBleListener? = null
        var maxBolus = 0.0
        var pumpType = DEFAULT_PUMP_TYPE

        fun connectDevice(device: BluetoothDevice) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "connectDevice status=$status")
            this.device = device
            if (status == BleStatus.CONNECTED) {
                listener?.onBleStatusChanged(status)
                return
            }
            if (status == BleStatus.CONNECTING) connectRequest?.cancel()
            status = BleStatus.CONNECTING
            listener?.onBleStatusChanged(status)
            connectRequest = connect(device).useAutoConnect(false).timeout(15000L).retry(2, 1000)
            connectRequest?.enqueue()
        }

        override fun initialize() {
            super.initialize()
            beginAtomicRequestQueue()
                .add(
                    requestMtu(517)
                        .with { _, mtu -> aapsLogger.debug(LTag.PUMPBTCOMM, "mtu: $mtu") }
                        .fail { _, error -> aapsLogger.error(LTag.PUMPBTCOMM, "mtu failed: $error") }
                )
                .add(enableNotifications(notifyCharacteristic))
                .done { }
                .enqueue()
            setNotificationCallback(notifyCharacteristic).with { _, data -> data.value?.let { handleFrame(it) } }
        }

        override fun onDeviceReady() {
            super.onDeviceReady()
            status = BleStatus.CONNECTED
            sendReportDevice()
            CoroutineScope(Dispatchers.IO).launch {
                delay(500L)
                listener?.onBleStatusChanged(status)
            }
        }

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val pumpService = gatt.getService(SERVICE_PUMP) ?: return false
            notifyCharacteristic = pumpService.getCharacteristic(CHARACTERISTIC_NOTIFY)
            writeCharacteristic = pumpService.getCharacteristic(CHARACTERISTIC_WRITE)
            gatt.getService(SERVICE_DEVICE_INFO)?.characteristics?.forEach { characteristic ->
                val uuid = characteristic.uuid.toString()
                if (uuid.contains("2a26", ignoreCase = true)) firmwareCharacteristic = characteristic
                if (uuid.contains("2a27", ignoreCase = true)) hardwareCharacteristic = characteristic
            }
            return notifyCharacteristic != null && writeCharacteristic != null
        }

        override fun onServicesInvalidated() {
            super.onServicesInvalidated()
            status = BleStatus.DISCONNECTED
            listener?.onBleStatusChanged(status)
            notifyCharacteristic = null
            writeCharacteristic = null
            hardwareCharacteristic = null
        }

        fun read(paramId: Short) {
            if (writeCharacteristic == null) return
            queue.add(Request(paramId, isRead = true))
            sendNextIfIdle()
        }

        fun write(paramId: Short, data: ByteArray) {
            if (writeCharacteristic == null) return
            queue.add(Request(paramId, isRead = false, data = data))
            sendNextIfIdle()
        }

        private fun sendNextIfIdle() {
            if (!writing) queue.poll()?.let { send(it) }
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

        fun readVersion() {
            val firmware = firmwareCharacteristic
            val hardware = hardwareCharacteristic
            if (firmware == null || hardware == null) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "version characteristic is missing")
                return
            }
            readCharacteristic(firmware).with { _, data: Data ->
                data.value?.takeIf { it.isNotEmpty() }?.let { pumpVersionNum = String(it, Charsets.UTF_8) }
            }.enqueue()
            readCharacteristic(hardware).enqueue()
        }

        private fun send(request: Request) {
            sequence = if (sequence.unsigned() >= 0xFFFF) 0 else (sequence.unsigned() + 1).toShort()
            val frame =
                if (request.isRead) AaruyFrame.buildRead(request.paramId, sequence)
                else AaruyFrame.buildWrite(request.paramId, sequence, request.data)
            val characteristic = writeCharacteristic ?: return
            writing = true
            writeCharacteristic(characteristic, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                .done { onWriteDone(request.paramId) }
                .fail { _, _ ->
                    writing = false
                    listener?.onRequestResult(false, request.paramId.unsigned())
                }
                .enqueue()
        }

        @SuppressLint("MissingPermission")
        private fun onWriteDone(paramId: Short) {
            if (paramId == AaruyParamId.UNBIND) {
                device?.let { unbound ->
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(3000L)
                        removeBond(unbound)
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

        /** `removeBond` is hidden in the Android API, so it can only be reached by reflection. */
        private fun removeBond(device: BluetoothDevice) {
            try {
                device.javaClass.getMethod("removeBond").invoke(device)
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMPBTCOMM, "removeBond failed", e)
            }
        }

        private fun handleFrame(frame: ByteArray) {
            try {
                val message = AaruyFrame.parse(frame) ?: return
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
                    if (payload.size == 2) sequence = AaruyByteReader(payload).u16()

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
            sequence = packet.serial
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

        private val SERVICE_PUMP: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        private val CHARACTERISTIC_WRITE: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        private val CHARACTERISTIC_NOTIFY: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        private val SERVICE_DEVICE_INFO: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")

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
