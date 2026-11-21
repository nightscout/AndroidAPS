package app.aaps.pump.aaruy.comm

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.ble.BleLink
import app.aaps.core.interfaces.pump.ble.BleResult
import app.aaps.core.interfaces.pump.ble.BleUuid
import app.aaps.core.interfaces.pump.ble.BondState
import app.aaps.core.interfaces.pump.ble.CharacteristicId
import app.aaps.core.interfaces.pump.ble.ConnectOptions
import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.core.interfaces.pump.ble.LinkState
import app.aaps.core.interfaces.pump.ble.Subscription
import app.aaps.core.interfaces.pump.ble.WriteType
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Talks to the pump. Turns commands into encrypted packets and pump messages into [AaruyBleListener] calls.
 *
 * Recovered from the vendor library `mylibrary-release.aar`. `AaruyCommandBytesTest` checks that it still sends the
 * same bytes as that library did.
 * Commands that the driver did not use are left out.
 *
 * The radio is the shared [BleCentral]: the phone's, or an emulated pump in its place.
 *
 * @param pumpDispatcher all pump events run here, one after the other. Not the main thread: the pump must not wait for
 * the screen, and the screen must not wait for the pump.
 * @param timeSource for the waits of the link. A test gives the time of its dispatcher.
 */
class AaruyBleClient(
    private val aapsLogger: AAPSLogger,
    private val central: BleCentral,
    val pumpDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    private val timeSource: TimeSource = TimeSource.Monotonic
) {

    private val scope = CoroutineScope(
        SupervisorJob() + pumpDispatcher + CoroutineExceptionHandler { _, e -> aapsLogger.error(LTag.PUMPBTCOMM, "Pump event failed", e) }
    )

    /** Runs [block] on the thread of the pump events, after the events that came before. */
    fun onPumpThread(block: () -> Unit) {
        scope.launch { block() }
    }

    private val link = PumpLink()

    @Volatile private var prepareStartBasal = false
    @Volatile private var currentStatus: CurrentStatusPacket? = null
    @Volatile private var needRestoreUnRunState = false
    @Volatile private var isSmbType = false
    @Volatile private var pumpVersionNum = ""

    /** Sent by the pump when it reports the device. It changes the size of the control packets. */
    @Volatile private var protocolVersion = 0

    private val runningStatus: Int? get() = currentStatus?.runningStatus?.toInt()

    /**
     * Connects to the pump. A pump that is not bonded yet is bonded first, as the vendor library did, with the PIN that
     * comes from its name. Seen on a real pump: the user presses its button to allow it.
     */
    fun connect(address: String, name: String) = link.connectDevice(address, name)

    fun setOnBleListener(listener: AaruyBleListener) {
        link.listener = listener
    }

    fun getOnBleListener(): AaruyBleListener? = link.listener

    /** The firmware the pump reports, or the last one read when the link cannot tell. */
    suspend fun readFirmwareVersion(): String {
        link.readFirmware()?.takeIf { it.isNotEmpty() }?.let { pumpVersionNum = it.decodeToString() }
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

            // The flag stays until basal runs: a restore that was lost or refused must be sent again when the user tries again.
            8    -> if (needRestoreUnRunState) restoreUnRunState() else link.listener?.onRunReplaceFirst(status)

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
        link.newBolus()
        val listener = link.listener
        when {
            status == 0                                                 -> listener?.onRunBasalFirst(status)
            status == 7                                                 -> listener?.onRunReplaceFirst(status)
            delayed > 0.0 && delayMinutes > 0 && delayMinutes % 30 != 0 -> listener?.onInjectTimeError(delayMinutes)

            else                                                        -> {
                val total = delayed + now
                val maxBolus = link.maxBolus
                if (total > maxBolus) {
                    listener?.onBolusValueTooHigh(total, maxBolus)
                } else if (status == 1 || status == 2) {
                    val packet = BolusSettingPacket().apply {
                        bolus = total.toThousandths()
                        bolusNow = now.toThousandths()
                        bolusDelayTime = delayMinutes.toShort()
                    }
                    link.writeBolusSetting(packet.toBytes())
                }
            }
        }
    }

    /** @return false when the start was not queued, so it can never be sent */
    private fun startBolus(): Boolean {
        val listener = link.listener
        when (val status = runningStatus) {
            1, 2 -> {
                val type: Byte = if (isSmbType) 2 else 0
                isSmbType = false
                link.writeBolusStart(ControlPacket(AaruyPumpTime.now(), 1, type).toBytes(protocolVersion))
                return true
            }

            3    -> listener?.onBolusIsRunning(status)
            else -> listener?.onRunBasalFirst(status ?: UNKNOWN_STATUS)
        }
        return false
    }

    /**
     * The driver stopped waiting for the pump to report the bolus. A start that is still waiting here is never sent.
     * The pump answers a start only with a status, so a start that went to the radio may run with no answer seen.
     *
     * @return true when the start went to the radio and the pump may run the bolus
     */
    fun abandonBolusStart(): Boolean = link.abandonBolusStart()

    fun cancelBolus() {
        // A stop that goes out before the start stops nothing, and then the whole bolus runs.
        link.abandonBolusStart()
        link.write(AaruyParamId.BOLUS_CONTROL, ControlPacket(AaruyPumpTime.now(), 0).toBytes(protocolVersion))
    }

    /**
     * The driver gave up on the temp basal it set or ended: a frame of it that still waits in the queue, or that the
     * pump's count shows lost, never goes out again. Sent again after the driver had reported the failure, it ran on
     * the pump while AAPS said it had failed.
     */
    fun abandonTempBasal() = link.abandon(AaruyParamId.TEMP_BASAL_CONTROL)

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

    /**
     * The rewind of a change of reservoir was done in an earlier app run, and the pump log shows no delivery since. The pump
     * leaves its stop only with a restore, and this client sends one only after a rewind it knows of. Without this the user
     * had to rewind and prime again after an app restart, with more insulin.
     */
    fun allowRestoreAfterRewind() {
        needRestoreUnRunState = true
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

    /**
     * Ends the link and the tries to make one: when the command queue has nothing more to send, for an unpaired pump, or
     * for a plugin that is off. Nothing connects again until the driver asks. (The vendor library meant to drop the link
     * here too, but it never sent its request, so the link stayed up.)
     */
    fun closeLink() = link.close()

    fun unbindDevice() {
        link.write(AaruyParamId.UNBIND, byteArrayOf(1))
    }

    fun updatePumpTime() = link.syncTime()

    fun getCurStatus(): Int? {
        link.read(AaruyParamId.CURRENT_STATUS)
        link.read(AaruyParamId.REAL_DATA)
        return runningStatus
    }

    /** @param bolusId not 0 for the start of a bolus: which bolus it starts */
    private class Request(val paramId: Short, val isRead: Boolean, val data: ByteArray = ByteArray(0), val bolusId: Int = 0) {

        /** The driver gave up on it: it never goes out again. Set and read under the send lock. */
        var abandoned = false
    }

    /** A frame that went to the radio, with its number. */
    private class Sent(val sequence: Short, val request: Request)

    /** NONE: no bolus asked. PENDING: the setting is sent, the start is not yet at the radio. SENT: the start went to the radio. DROPPED: the start must never be sent. */
    private enum class BolusStart { NONE, PENDING, SENT, DROPPED }

    /**
     * The connection and the command queue. Runs the steps the vendor library got from its BLE library:
     * bond, connect with retries, ask for a large MTU, enable notifications.
     * Events are handled one by one on [pumpDispatcher], in the order they came.
     */
    private inner class PumpLink {

        /** The work of one link: the connect, its frames, its timers. Cancelled when the link ends. */
        @Volatile private var linkScope = newLinkScope()
        @Volatile private var bleLink: BleLink? = null
        private var address: String? = null

        /** From the first connect until the setup ended, either way. */
        private var connecting = false

        /** Commands may be sent. It is set when the setup is complete. */
        @Volatile private var ready = false
        private val sendLock = AapsLock()
        private var sequence: Short = 0
        private var writing = false

        /** The pump has answered the report of this link with its sequence. See `reportConnected`. */
        @Volatile private var reportAnswered = false
        private val queue = ArrayDeque<Request>()
        private val inFlight = ArrayList<Sent>()
        private var status = BleStatus.DISCONNECTED

        /**
         * Frames the pump counts and may not have taken yet, oldest first. Under [sendLock]. Seen on a real pump: a frame
         * whose number is not the next one is refused (error 28) and not carried out, and the pump's count stays where it
         * was. So the count tells which of these to send again, see [onPumpCount].
         */
        private val unconfirmed = ArrayDeque<Sent>()

        /** A frame was refused for its number. Nothing but the count read goes out until the pump has told its count. */
        private var resyncing = false

        /** The count read is the next frame to go out. */
        private var countWanted = false

        /** A count read is out, and its answer is wanted. */
        private var countAsked = false

        /** When the last count read went out. Seen on a real pump: it answers a count read twice, the second answer later. */
        private var countReadAt = 0L
        private var countTries = 0
        private var countTimeout: Job? = null

        /** Changed only under [sendLock], so the start cannot go out while the driver gives up on it. */
        private var bolusStart = BolusStart.NONE

        /** The bolus [bolusStart] is about. A start of an older bolus is never sent. */
        private var bolusId = 0

        /** The driver has asked whether the start of [bolusId] went out. From then on it never goes out again. */
        private var bolusStartAsked = false

        @Volatile var listener: AaruyBleListener? = null
        @Volatile var maxBolus = 0.0
        @Volatile var pumpType = DEFAULT_PUMP_TYPE

        private fun newLinkScope() = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))

        private val clockStart = timeSource.markNow()
        private fun uptime(): Long = clockStart.elapsedNow().inWholeMilliseconds

        fun connectDevice(address: String, name: String) = onPumpThread {
            aapsLogger.debug(LTag.PUMPBTCOMM, "connectDevice status=$status")
            if (status == BleStatus.CONNECTED) {
                if (address == this.address) {
                    listener?.onBleStatusChanged(status)
                    return@onPumpThread
                }
                // A link to another pump is no link to this one.
                closeNow()
            }
            this.address = address
            if (connecting) return@onPumpThread
            connecting = true
            status = BleStatus.CONNECTING
            listener?.onBleStatusChanged(status)
            linkScope.launch { connectWithRetries(DeviceId(address), name) }
        }

        /** After the last try the link is DISCONNECTED. The command queue then asks again, until its own time is up. */
        private suspend fun connectWithRetries(device: DeviceId, name: String) {
            for (attempt in 0..CONNECT_RETRIES) {
                if (attempt > 0) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "connect retries left: ${CONNECT_RETRIES - attempt + 1}")
                    delay(CONNECT_RETRY_DELAY_MS)
                }
                val opened = open(device, name) ?: continue
                onLinkUp(opened)
                return
            }
            connecting = false
            status = BleStatus.DISCONNECTED
            listener?.onBleStatusChanged(status)
        }

        /** @return the link set up for the pump, or null when a step failed */
        private suspend fun open(device: DeviceId, name: String): BleLink? {
            central.bonding?.let { bonding ->
                if (bonding.bondState(device).first() != BondState.BONDED) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "createBond")
                    val bonded = bonding.createBond(device, BOND_TIMEOUT, pin = pinFromDeviceName(name))
                    if (bonded is BleResult.Failed) {
                        aapsLogger.debug(LTag.PUMPBTCOMM, "bond failed: ${bonded.failure}")
                        return null
                    }
                }
            }
            val opened = when (val result = central.connect(device, ConnectOptions(timeout = CONNECT_TIMEOUT, services = listOf(SERVICE_PUMP, SERVICE_DEVICE_INFO)))) {
                is BleResult.Ok     -> result.value
                is BleResult.Failed -> {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "connect failed: ${result.failure}")
                    return null
                }
            }
            try {
                // Seen on a real pump: MTU 247.
                aapsLogger.debug(LTag.PUMPBTCOMM, "mtu: ${opened.requestMtu(REQUESTED_MTU)}")
                val subscribed = opened.subscribe(CHARACTERISTIC_NOTIFY, Subscription.NOTIFY)
                if (subscribed is BleResult.Failed) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "The pump notifications could not be enabled: ${subscribed.failure}")
                    opened.close()
                    return null
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { opened.close() }
                throw e
            }
            return opened
        }

        private fun onLinkUp(opened: BleLink) {
            // Closed while it was set up: nobody wants it now.
            if (!connecting) {
                scope.launch { opened.close() }
                return
            }
            bleLink = opened
            linkScope.launch {
                opened.incoming.collect { incoming -> if (incoming.characteristic == CHARACTERISTIC_NOTIFY && bleLink === opened) handleFrame(incoming.data) }
            }
            linkScope.launch {
                opened.state.first { it != LinkState.Up }
                linkUp = false
                if (bleLink === opened) onDisconnected()
            }
            linkScope.launch {
                // Seen on a real phone: no GATT event comes for this, and the driver stays connected to a dead link.
                central.adapterState.first { it == AdapterState.OFF || it == AdapterState.UNAUTHORIZED }
                linkUp = false
                if (bleLink === opened) onDisconnected()
            }
            onReady()
        }

        /** False from the moment the link is known lost, before the loss is handled: nothing more may go out then. */
        @Volatile private var linkUp = false

        /** Ends the link and every try to make one. Nothing connects again until the driver asks. */
        fun close() = onPumpThread { closeNow() }

        private fun closeNow() {
            aapsLogger.debug(LTag.PUMPBTCOMM, "close link, status=$status connecting=$connecting")
            val wasUp = status != BleStatus.DISCONNECTED || connecting
            endLink()
            status = BleStatus.DISCONNECTED
            if (wasUp) listener?.onBleStatusChanged(status)
        }

        private fun onDisconnected() {
            aapsLogger.debug(LTag.PUMPBTCOMM, "disconnected")
            endLink()
            status = BleStatus.DISCONNECTED
            listener?.onBleStatusChanged(status)
        }

        /** Also the retries and the timers of the link. */
        private fun endLink() {
            linkUp = false
            connecting = false
            ready = false
            val old = bleLink
            bleLink = null
            linkScope.cancel()
            linkScope = newLinkScope()
            sendLock.withLock {
                writing = false
                inFlight.clear()
                // A command that waited here was asked on the old link, and the driver has given up on it. After a reconnect it would come too late.
                queue.clear()
                endResync()
                if (bolusStart == BolusStart.PENDING) bolusStart = BolusStart.DROPPED
            }
            old?.let { scope.launch { it.close() } }
        }

        private fun onReady() {
            connecting = false
            status = BleStatus.CONNECTED
            ready = true
            linkUp = true
            reportAnswered = false
            sendReportDevice()
            reportConnected(CONNECTED_DELAY_MS, uptime() + REPORT_ANSWER_TIMEOUT_MS)
        }

        /**
         * Tells the driver the link is up once the pump has answered the report with its sequence. After a fixed 500 ms,
         * the driver's first frames could go out before a late answer, which then set the sequence back below them: the
         * next frame was refused (error 28) and its request lost. Without an answer in [REPORT_ANSWER_TIMEOUT_MS] the link
         * is reported up anyway, as before. On the pump thread, so it comes before or after a loss of the link, never over it.
         */
        private fun reportConnected(delayMs: Long, latest: Long) {
            linkScope.launch {
                delay(delayMs)
                if (status != BleStatus.CONNECTED || !ready) return@launch
                if (!reportAnswered && uptime() < latest) reportConnected(REPORT_ANSWER_POLL_MS, latest)
                else listener?.onBleStatusChanged(status)
            }
        }

        suspend fun readFirmware(): ByteArray? = (bleLink?.read(CHARACTERISTIC_FIRMWARE) as? BleResult.Ok)?.value

        fun read(paramId: Short) {
            if (!ready) return
            sendLock.withLock { queue.addLast(Request(paramId, isRead = true)) }
            sendNextIfIdle()
        }

        fun write(paramId: Short, data: ByteArray) {
            if (!ready) return
            sendLock.withLock { queue.addLast(Request(paramId, isRead = false, data = data)) }
            sendNextIfIdle()
        }

        fun writeBolusStart(data: ByteArray) {
            if (!ready) return
            sendLock.withLock { queue.addLast(Request(AaruyParamId.BOLUS_CONTROL, isRead = false, data = data, bolusId = bolusId)) }
            sendNextIfIdle()
        }

        /** A new bolus: what the last one left behind is never sent. */
        fun newBolus() = sendLock.withLock {
            abandonBolusStart()
            bolusId++
            bolusStart = BolusStart.NONE
            bolusStartAsked = false
        }

        /** The start follows when the pump has taken the setting, see [onWriteDone]. */
        fun writeBolusSetting(data: ByteArray) {
            if (!ready) return
            sendLock.withLock { bolusStart = BolusStart.PENDING }
            write(AaruyParamId.BOLUS_SETTING, data)
        }

        fun abandonBolusStart(): Boolean = sendLock.withLock {
            bolusStartAsked = true
            when (bolusStart) {
                BolusStart.SENT    -> true

                BolusStart.PENDING -> {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "bolus start dropped before it was sent")
                    bolusStart = BolusStart.DROPPED
                    queue.removeAll { it.bolusId != 0 }
                    false
                }

                else               -> false
            }
        }

        /** See [AaruyBleClient.abandonTempBasal]. */
        fun abandon(paramId: Short) = sendLock.withLock {
            queue.removeAll { it.paramId == paramId && !it.isRead }
            unconfirmed.forEach { if (it.request.paramId == paramId) it.request.abandoned = true }
        }

        private fun sendNextIfIdle() {
            sendLock.withLock {
                if (!writing) nextRequest()?.let { send(it) }
            }
        }

        private fun sendNext() {
            sendLock.withLock { nextRequest()?.let { send(it) } }
        }

        /** Under [sendLock]. While the pump's count is asked, only the count read goes out. */
        private fun nextRequest(): Request? = when {
            countWanted -> {
                countWanted = false
                Request(AaruyParamId.SERIAL, isRead = true)
            }

            resyncing   -> null
            else        -> queue.removeFirstOrNull()
        }

        fun syncTime() {
            write(AaruyParamId.SYNC_TIME, allocate(4).putInt(AaruyPumpTime.now()).array())
        }

        private fun sendReportDevice() {
            val packet = ReportDevicePacket().apply {
                deviceType = 7
                version = 1
                settingTime = AaruyPumpTime.now()
            }
            write(AaruyParamId.REPORT_DEVICE, packet.toBytes())
        }

        /** Writes come from the pump thread and from coroutines. The pump refuses a frame whose sequence is out of order. */
        private fun send(first: Request) {
            sendLock.withLock {
                var request = first
                // Taken from the queue just before the driver gave up on it.
                while (request.bolusId != 0 && (request.bolusId != bolusId || bolusStart != BolusStart.PENDING)) request = nextRequest() ?: return
                if (request.bolusId != 0) bolusStart = BolusStart.SENT
                sequence = if (sequence.unsigned() >= 0xFFFF) 0 else (sequence.unsigned() + 1).toShort()
                val frame =
                    if (request.isRead) AaruyFrame.buildRead(request.paramId, sequence)
                    else AaruyFrame.buildWrite(request.paramId, sequence, request.data)
                aapsLogger.debug(LTag.PUMPBTCOMM, "TX ${if (request.isRead) "read" else "write"} param=${request.paramId} seq=${sequence.unsigned()} data=${if (request.isRead) "" else request.data.toHexText()}")
                val sent = Sent(sequence, request)
                when (request.paramId) {
                    AaruyParamId.SERIAL        -> {
                        countAsked = true
                        countReadAt = uptime()
                    }

                    // The pump does not count the report: it answers it with its count.
                    AaruyParamId.REPORT_DEVICE -> Unit

                    else                       -> {
                        unconfirmed.addLast(sent)
                        // A refusal comes within a few frames. Older ones are taken, or lost for good as before this list.
                        if (unconfirmed.size > MAX_UNCONFIRMED) unconfirmed.removeFirst()
                    }
                }
                val target = bleLink ?: run {
                    // The link went away since the request was queued; its end cleared the queue.
                    unconfirmed.remove(sent)
                    return
                }
                writing = true
                inFlight.add(sent)
                // Writes without response, as the vendor library: the pump answers with a frame of its own.
                linkScope.launch {
                    val result = target.write(CHARACTERISTIC_WRITE, frame, WriteType.WITHOUT_RESPONSE)
                    if (result is BleResult.Failed) aapsLogger.warn(LTag.PUMPBTCOMM, "write of param ${request.paramId} failed: ${result.failure}")
                    onWriteEnd(sent, result is BleResult.Ok)
                }
            }
        }

        private fun onWriteEnd(sent: Sent, written: Boolean) {
            // Not when the link was closed meanwhile: its end cleared the writes.
            if (!sendLock.withLock { inFlight.remove(sent) }) return
            if (written) onWriteDone(sent.request.paramId) else onWriteFailed(sent)
        }

        private fun onWriteDone(paramId: Short) {
            if (paramId == AaruyParamId.UNBIND) {
                address?.let { unbound ->
                    scope.launch {
                        delay(UNBIND_BOND_REMOVAL_DELAY_MS)
                        central.bonding?.removeBond(DeviceId(unbound))
                    }
                }
            } else if (paramId == AaruyParamId.BOLUS_SETTING) {
                sendLock.withLock {
                    if (bolusStart == BolusStart.PENDING && !startBolus()) bolusStart = BolusStart.NONE
                }
            }
            sendLock.withLock { writing = false }
            listener?.onRequestResult(true, paramId.unsigned())
            sendNext()
        }

        private fun onWriteFailed(sent: Sent) {
            sendLock.withLock {
                writing = false
                // The driver hears that it failed, so it never goes out later. The pump never got it.
                unconfirmed.remove(sent)
            }
            listener?.onRequestResult(false, sent.request.paramId.unsigned())
            // The commands behind it are still wanted now, not at the next command, when the driver has given up on them.
            // Not into a link that is gone: a bolus start sent there would count as sent, and its loss clears the queue anyway.
            if (linkUp) sendNext()
        }

        /**
         * The pump refused a frame for its number (error 28). The frames sent behind it are refused too, so one count read
         * covers them all. Not before both answers of the last count read can have come: the second one could be taken for
         * the answer of this one, and its count is old.
         */
        private fun startResync() {
            val wait = sendLock.withLock {
                if (resyncing) return
                resyncing = true
                countTries = 0
                countReadAt + COUNT_SECOND_ANSWER_MS - uptime()
            }
            if (wait > 0) countTimeout = linkScope.launch {
                delay(wait)
                countTimeout = null
                askCount()
            } else askCount()
        }

        private fun askCount() {
            countTimeout?.cancel()
            countTimeout = null
            sendLock.withLock {
                if (!resyncing) return
                if (countTries++ == COUNT_TRIES) {
                    // As before the count was used: what was refused stays lost, and the waits of the driver tell.
                    aapsLogger.error(LTag.PUMPBTCOMM, "The pump did not tell its count")
                    endResync()
                } else {
                    countWanted = true
                    countTimeout = linkScope.launch {
                        delay(COUNT_ANSWER_TIMEOUT_MS)
                        countTimeout = null
                        askCount()
                    }
                }
            }
            sendNextIfIdle()
        }

        /**
         * The pump's count: the number of the last frame it took. A frame with a higher number was refused or lost, and
         * the pump did not carry it out. So it goes out again, in the same order, with a new number.
         */
        private fun onPumpCount(count: Short) {
            sendLock.withLock {
                // The second answer to a count read, or one to a read from before: its count can be old.
                if (!resyncing || !countAsked) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "count ${count.unsigned()} not asked for, ignored")
                    return
                }
                sequence = count
                val again = unconfirmed.filter { it.sequence.isAfter(count) }.mapNotNull { sendAgain(it.request) }
                endResync()
                again.asReversed().forEach { queue.addFirst(it) }
                aapsLogger.debug(LTag.PUMPBTCOMM, "pump count ${count.unsigned()}, sent again: ${again.map { it.paramId }}")
            }
            sendNextIfIdle()
        }

        /** Under [sendLock]. @return the request to send again, or null when it must never go out again */
        private fun sendAgain(request: Request): Request? {
            if (request.abandoned) return null
            if (request.bolusId == 0) return request
            // A start the driver has asked about, or the start of an older bolus.
            if (request.bolusId != bolusId || bolusStart != BolusStart.SENT || bolusStartAsked) return null
            bolusStart = BolusStart.PENDING
            return request
        }

        /** Under [sendLock]. */
        private fun endResync() {
            countTimeout?.cancel()
            countTimeout = null
            resyncing = false
            countWanted = false
            countAsked = false
            unconfirmed.clear()
        }

        private fun handleFrame(frame: ByteArray) {
            try {
                val message = AaruyFrame.parse(frame)
                if (message == null) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "RX frame not made of full blocks: ${frame.toHexText()}")
                    return
                }
                aapsLogger.debug(LTag.PUMPBTCOMM, "RX param=${message.paramId} data=${message.payload.toHexText()}")
                if (message.paramId != AaruyParamId.SERIAL) sendNext()
                handleMessage(message.paramId, message.payload)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMPBTCOMM, "Failed to handle a pump message", e)
            }
        }

        private fun handleMessage(paramId: Short, payload: ByteArray) {
            when (paramId) {
                AaruyParamId.REPORT_DEVICE                                 -> handleReportDevice(payload)

                AaruyParamId.SERIAL                                        ->
                    if (payload.size == 2) onPumpCount(AaruyByteReader(payload).u16())

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
                    // From stopped to not running: the restore worked. From here basal starts without it.
                    if (runningStatus == 8 && packet.runningStatus.toInt() == 0) needRestoreUnRunState = false
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
            sendLock.withLock { sequence = packet.serial }
            reportAnswered = true
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
            if (packet.error == ERROR_OPERATION && packet.errorType == ERROR_TYPE_READ_SERIAL) startResync()
            listener?.onReceiveErrorData(packet.errorType, packet.error)
        }
    }

    companion object {

        val SERVICE_PUMP: BleUuid = BleUuid.of("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val CHARACTERISTIC_WRITE = CharacteristicId(SERVICE_PUMP, BleUuid.of("6e400002-b5a3-f393-e0a9-e50e24dcca9e"))
        val CHARACTERISTIC_NOTIFY = CharacteristicId(SERVICE_PUMP, BleUuid.of("6e400003-b5a3-f393-e0a9-e50e24dcca9e"))
        val SERVICE_DEVICE_INFO: BleUuid = BleUuid.of("180a")
        val CHARACTERISTIC_FIRMWARE = CharacteristicId(SERVICE_DEVICE_INFO, BleUuid.of("2a26"))

        /** Pumps advertise as `aaruy_` and six characters. */
        const val DEVICE_NAME_PREFIX = "aaruy_"

        private val CONNECT_TIMEOUT = 15.seconds

        /** Pairing waits for a button on the pump, and Android gives up on it after 30 s. */
        private val BOND_TIMEOUT = 45.seconds
        private const val CONNECT_RETRIES = 2
        private const val CONNECT_RETRY_DELAY_MS = 1_000L

        /** As in the vendor library: the answers to the first frames come before the driver hears of the link. */
        private const val CONNECTED_DELAY_MS = 500L

        /** How long the link waits for the pump's sequence before it is reported up anyway, and how often it looks. */
        private const val REPORT_ANSWER_TIMEOUT_MS = 5_000L
        private const val REPORT_ANSWER_POLL_MS = 100L

        /** As in the vendor library: the bond is removed this long after the pump was told to forget the phone. */
        private const val UNBIND_BOND_REMOVAL_DELAY_MS = 3_000L

        /** A refusal comes within a few frames. */
        private const val MAX_UNCONFIRMED = 32

        /** Seen on a real pump: the second answer to a count read came up to about 0.6 s after the read. */
        private const val COUNT_SECOND_ANSWER_MS = 1_500L
        private const val COUNT_ANSWER_TIMEOUT_MS = 1_000L
        private const val COUNT_TRIES = 5

        /** Whether this frame number comes after [count]. The numbers go round after 0xFFFF. */
        private fun Short.isAfter(count: Short): Boolean = ((unsigned() - count.unsigned()) and 0xFFFF) in 1..0x7FFF
        private const val REQUESTED_MTU = 517
        private const val DEFAULT_PIN = "210101"
        private const val DEFAULT_PUMP_TYPE = 131
        private const val REPORT_DEVICE_ACCEPTED = 6
        private const val ERROR_TYPE_LIQUID_SHOW = 25
        private const val ERROR_TYPE_READ_SERIAL = 28
        private const val ERROR_OPERATION = 128

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

        /**
         * The PIN comes from the six characters after `aaruy_` in the device name. Android gives it to the system by itself;
         * on iOS the user types it into the system's dialog, so the pairing screen shows it there.
         */
        fun pinFromDeviceName(name: String): String {
            if (name.trim().length !in 12 until 18) return DEFAULT_PIN
            val pin = name.substring(6, 12).map { pinCode(it) }.joinToString("")
            return if (pin.trim().length < 6) DEFAULT_PIN else pin
        }
    }
}

private fun ByteArray.toHexText(): String = joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }

/** The AES key of the BLE frames. The emulator needs it too. */
fun aaruyFrameKey(): ByteArray = AaruyFrame.key.copyOf()

/** The encrypted frame around each packet. */
@OptIn(DelicateCryptographyApi::class)
internal object AaruyFrame {

    class Message(val paramId: Short, val payload: ByteArray)

    private const val FRAME_START: Byte = 0x55
    private const val OUTER_HEADER = 3
    private const val INNER_HEADER = 7
    private const val INNER_HEADER_AFTER_LENGTH = 5
    private const val COMMAND_READ: Byte = 0
    private const val COMMAND_WRITE: Byte = 1
    private const val BLOCK_SIZE = 16

    val key = byteArrayOf(65, 97, 114, 117, 121, 121, 105, 108, 105, 97, 111, 49, 50, 51, 52, 53)

    /** AES in ECB mode with PKCS#7 padding: what the vendor's `AES/ECB/PKCS5Padding` is for a 16 byte block. */
    private val cipher by lazy {
        CryptographyProvider.Default.get(AES.ECB).keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key).cipher(padding = true)
    }

    private fun wrap(plain: ByteArray): ByteArray {
        val encrypted = cipher.encryptBlocking(plain)
        return allocate(encrypted.size + OUTER_HEADER)
            .put(FRAME_START)
            .putShort(encrypted.size.toShort())
            .put(encrypted)
            .array()
    }

    private fun inner(length: Short, command: Byte, paramId: Short, sequence: Short, payload: ByteArray): ByteArray =
        allocate(INNER_HEADER + payload.size)
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
        val plain = cipher.decryptBlocking(body)
        val reader = AaruyByteReader(plain)
        reader.u16()
        reader.u8()
        val paramId = reader.u16()
        return Message(paramId, AaruyByteReader.slice(plain, INNER_HEADER, plain.size - INNER_HEADER))
    }
}
