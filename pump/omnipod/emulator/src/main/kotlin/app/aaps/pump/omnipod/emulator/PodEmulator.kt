package app.aaps.pump.omnipod.emulator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.emulator.EmulatedAlarm
import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import app.aaps.core.interfaces.pump.emulator.EmulatedPumpState
import app.aaps.core.interfaces.pump.emulator.EmulatedTempBasal
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.pod.util.MessageUtil
import app.aaps.pump.omnipod.emulator.EmulatedPodState.Companion.PULSE_UNITS
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.Calendar
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * An Omnipod Dash pod, as far as the driver can see it: it takes the pod commands and answers them, and gives its
 * insulin pulse by pulse in its own time.
 *
 * It knows nothing of Bluetooth or of the encryption. [OmnipodEmulatorTransport] does that part and hands each command
 * to [handleCommand] as the bytes the driver built.
 *
 * Where the answers come from: the answer layouts are the ones the driver parses, and the fixed values of a new pod
 * (firmware 4.10.0, 52 and 10 priming pulses, 80 h) are the bytes of a real pod in `VersionResponseTest`,
 * `SetUniqueIdResponseTest` and `AlarmStatusResponseTest` of `:pump:omnipod:dash`. Nothing here was captured for this
 * emulator on a real pod yet, so what a real pod does in a case those bytes do not show is marked UNVERIFIED.
 *
 * The pod has no clock and no history. It counts minutes since its activation and pulses, and that is all it reports.
 */
class PodEmulator(
    private val aapsLogger: AAPSLogger? = null,
    state: EmulatedPodState = newPod(),
    private val now: () -> Long = System::currentTimeMillis,
    /** What the pod says it is: 4 for a Dash, 5 for an Omnipod 5. See `PodType` in the driver. */
    private val productId: Int = PRODUCT_ID_DASH
) {

    var state: EmulatedPodState = state
        private set

    private val lostCommands = ArrayDeque<EmulatedCommand>()
    private val lostAnswers = ArrayDeque<EmulatedCommand>()

    /** The next bolus for the user stops with a fault after this many pulses. */
    private var nextBolusFaultAtPulses: Int? = null

    // ---- Test hooks ----------------------------------------------------------------------------------------------

    @Synchronized fun loseCommand(command: EmulatedCommand): Boolean {
        if (command !in KNOWN_COMMANDS) return false
        lostCommands.add(command)
        return true
    }

    @Synchronized fun loseAnswer(command: EmulatedCommand): Boolean {
        if (command !in KNOWN_COMMANDS) return false
        lostAnswers.add(command)
        return true
    }

    /**
     * A blockage or an empty reservoir is a fault of the pod: it stops all insulin, and answers every command with its
     * fault from then on. A low reservoir is only an alert bit in the status. A pod reports no battery, and it cannot be
     * "stopped too long" without the controller.
     *
     * UNVERIFIED: the bytes of a fault answer other than the alarm type, the pod status and the counters. The layout is
     * that of the real answer in `AlarmStatusResponseTest`, which is of a pod without a fault.
     */
    @Synchronized fun raise(alarm: EmulatedAlarm): Boolean {
        settle()
        if (!state.running) return false
        when (alarm) {
            EmulatedAlarm.OCCLUSION       -> fault(ALARM_OCCLUDED, now())
            EmulatedAlarm.EMPTY_RESERVOIR -> {
                state.reservoirPulses = 0
                fault(ALARM_EMPTY_RESERVOIR, now())
            }

            EmulatedAlarm.LOW_RESERVOIR   -> {
                state.activeAlerts = state.activeAlerts or ALERT_LOW_RESERVOIR
                state.alertMinutes[ALERT_LOW_RESERVOIR_SLOT] = minutesSince(now())
            }
            else                          -> return false
        }
        return true
    }

    /** UNVERIFIED: that a blockage during a bolus is reported as the fault "occluded", with the bolus cut at that pulse. */
    @Synchronized fun stopNextBolusAt(units: Double): Boolean {
        nextBolusFaultAtPulses = (units / PULSE_UNITS).roundToInt()
        return true
    }

    /** A new pod out of its box: a new address and serial, not paired, nothing running. */
    @Synchronized fun replaceWithNewPod() {
        state = newPod()
        lostCommands.clear()
        lostAnswers.clear()
        nextBolusFaultAtPulses = null
    }

    @Synchronized fun snapshot(linkUp: Boolean): EmulatedPumpState {
        settle()
        val s = state
        return EmulatedPumpState(
            boluses = s.deliveredBoluses.toList(),
            bolusRunning = s.bolus?.priming == false,
            tempBasal = s.tempBasal?.let { EmulatedTempBasal(absolute = it.pulsesPerHour * PULSE_UNITS, percent = null) },
            suspended = s.suspended,
            reservoir = s.reservoirPulses * PULSE_UNITS,
            linkUp = linkUp,
            commands = s.commands,
            basal = s.basalPulsesPerSlot?.let { slots -> (0 until HOURS_PER_DAY).map { (slots[2 * it] + slots[2 * it + 1]) * PULSE_UNITS } }
        )
    }

    @Synchronized fun saveState(): ByteArray {
        settle()
        return ByteArrayOutputStream().also { bytes -> ObjectOutputStream(bytes).use { it.writeObject(state) } }.toByteArray()
    }

    // ---- Pairing, called by the transport ------------------------------------------------------------------------

    /** The pairing ended: the pod keeps the long term key. */
    @Synchronized fun paired(ltk: ByteArray) {
        state.ltk = ltk
    }

    // ---- Commands ------------------------------------------------------------------------------------------------

    /**
     * One command of the driver, as `Command.encoded` builds it: the pod id, the sequence number and length, the command
     * blocks and a CRC. Returns the answer in the same envelope, or null when the pod sends none.
     *
     * A lost command and a lost answer both give no answer. They differ in what the pod did: after a lost answer the pod
     * ran the command, and its "last programming command" number says so in the next status. That number is how the Dash
     * driver finds out.
     */
    @Synchronized fun handleCommand(encoded: ByteArray): ByteArray? {
        settle()
        val s = state
        if (s.podStatus == EmulatedPodState.STATUS_DEACTIVATED) return null
        if (encoded.size < ENVELOPE_SIZE) return null
        val header = ByteBuffer.wrap(encoded)
        val podId = header.int
        val lengthAndSequence = header.short.toInt() and 0xffff
        val sequence = (lengthAndSequence shr 10) and 0x0f
        val length = lengthAndSequence and 0x3ff
        val blocks = parseBlocks(encoded, HEADER_SIZE, min(HEADER_SIZE + length, encoded.size - CRC_SIZE))
        if (blocks.isEmpty()) return null
        s.commands++

        val kind = kindOf(blocks)
        if (kind != null && lostCommands.peek() == kind) {
            lostCommands.poll()
            aapsLogger?.debug(LTag.PUMPEMULATOR, "Pod: the command $kind is lost: ${encoded.toHex()}")
            return null
        }
        val answer = run(blocks, sequence)
        if (kind != null && lostAnswers.peek() == kind) {
            lostAnswers.poll()
            aapsLogger?.debug(LTag.PUMPEMULATOR, "Pod: the answer to $kind is lost")
            return null
        }
        return envelope(podId, sequence, answer)
    }

    private fun run(blocks: List<Block>, sequence: Int): ByteArray {
        val s = state
        val first = blocks[0]
        if (s.faulted && first.type != CMD_DEACTIVATE) return alarmStatus()
        when (first.type) {
            CMD_GET_VERSION    -> return versionAnswer(first.body)
            CMD_SET_UNIQUE_ID  -> return setUniqueId(first.body)
            CMD_GET_STATUS     -> return when (first.body.firstOrNull()?.toInt()) {
                STATUS_PAGE_DEFAULT -> defaultStatus()
                STATUS_PAGE_ALARM   -> alarmStatus()
                STATUS_PAGE_ALERTS  -> triggeredAlerts()
                STATUS_PAGE_TIME    -> activationTime()
                // UNVERIFIED: the Dash driver asks for no other page.
                else                -> nak(NAK_ILLEGAL_PARAM)
            }

            CMD_PROGRAM_INSULIN -> {
                val program = blocks.getOrNull(1) ?: return nak(NAK_ILLEGAL_PARAM)
                when (program.type) {
                    CMD_PROGRAM_BOLUS      -> if (!startBolus(program.body)) return nak(NAK_ILLEGAL_COMMAND_STATE)
                    CMD_PROGRAM_TEMP_BASAL -> if (!startTempBasal(first.body, program.body)) return nak(NAK_ILLEGAL_COMMAND_STATE)
                    CMD_PROGRAM_BASAL      -> setBasal(first.body)
                    else                   -> return nak(NAK_ILLEGAL_PARAM)
                }
            }

            CMD_STOP_DELIVERY  -> {
                stopDelivery(first.body.getOrNull(NONCE_SIZE)?.toInt() ?: 0)
                // A suspend is a stop of everything with the "suspend ended" alert programmed in the same message.
            }

            CMD_SILENCE_ALERTS -> {
                val mask = first.body.getOrNull(NONCE_SIZE)?.toInt() ?: 0
                s.activeAlerts = s.activeAlerts and mask.inv() and 0xff
            }

            CMD_PROGRAM_ALERTS -> programAlerts(first.body)
            CMD_PROGRAM_BEEPS  -> s.beepCommands++
            CMD_DEACTIVATE     -> deactivate()
            else               -> return nak(NAK_ILLEGAL_PARAM)
        }
        s.lastProgrammingSequence = sequence
        return defaultStatus()
    }

    /**
     * Keeps what the controller programs for each alert slot. After the nonce come 6 bytes for each alert, as
     * `AlertConfiguration` in the driver writes them: the slot in the high 4 bits of the first byte, bit 3 for "on",
     * and the trigger in bytes 2 and 3. The pod does not fire these alerts by itself here.
     */
    private fun programAlerts(body: ByteArray) {
        var at = NONCE_SIZE
        while (at + ALERT_SIZE <= body.size) {
            val first = body[at].toInt() and 0xff
            val slot = first shr 4
            if (slot < state.alertEnabled.size) {
                state.alertEnabled[slot] = first and 0x08 != 0
                state.alertTrigger[slot] = ((body[at + 2].toInt() and 0xff) shl 8) or (body[at + 3].toInt() and 0xff)
            }
            at += ALERT_SIZE
        }
    }

    /** The command by what it does, for the lost command and lost answer hooks. Only of a pod in use: not the priming. */
    private fun kindOf(blocks: List<Block>): EmulatedCommand? {
        val first = blocks[0]
        return when (first.type) {
            CMD_GET_STATUS      -> EmulatedCommand.STATUS
            CMD_PROGRAM_INSULIN -> when (blocks.getOrNull(1)?.type) {
                CMD_PROGRAM_BOLUS      -> EmulatedCommand.BOLUS_START.takeIf { state.running }
                CMD_PROGRAM_TEMP_BASAL -> EmulatedCommand.TEMP_BASAL_START
                CMD_PROGRAM_BASAL      -> EmulatedCommand.BASAL_PROFILE.takeIf { state.running }
                else                   -> null
            }

            CMD_STOP_DELIVERY   -> when ((first.body.getOrNull(NONCE_SIZE)?.toInt() ?: 0) and STOP_ALL) {
                STOP_BOLUS      -> EmulatedCommand.BOLUS_STOP
                STOP_TEMP_BASAL -> EmulatedCommand.TEMP_BASAL_STOP
                else            -> null
            }

            else                -> null
        }
    }

    private fun setUniqueId(body: ByteArray): ByteArray {
        val s = state
        if (body.size >= 4) s.uniqueId = ByteBuffer.wrap(body, 0, 4).int
        s.activatedAt = now()
        s.settledAt = now()
        s.podStatus = EmulatedPodState.STATUS_UID_SET
        // 011B 1388 10 08 34 0A 50 040A00 010300 04 03 <lot> <sequence> <id>, as in SetUniqueIdResponseTest.
        return ByteBuffer.allocate(SET_UNIQUE_ID_ANSWER_SIZE)
            .put(0x01.toByte()).put(0x1b.toByte())
            .putShort(PULSE_VOLUME)
            .put(PULSE_RATE).put(PRIME_PULSE_RATE)
            .put(EmulatedPodState.PRIME_PULSES.toByte()).put(EmulatedPodState.CANNULA_PULSES.toByte())
            .put(POD_LIFE_HOURS)
            .put(FIRMWARE).put(BLE_FIRMWARE).put(productId.toByte())
            .put(s.podStatus.toByte())
            .putInt(s.lotNumber).putInt(s.podSequenceNumber)
            .putInt(s.uniqueId ?: NO_ID)
            .array()
    }

    private fun versionAnswer(body: ByteArray): ByteArray {
        val s = state
        // 0115 040A00 010300 04 02 <lot> <sequence> 00 <id of the command>, as in VersionResponseTest.
        return ByteBuffer.allocate(VERSION_ANSWER_SIZE)
            .put(0x01.toByte()).put(0x15.toByte())
            .put(FIRMWARE).put(BLE_FIRMWARE).put(productId.toByte())
            .put(s.podStatus.toByte())
            .putInt(s.lotNumber).putInt(s.podSequenceNumber)
            .put(0.toByte()) // rssi and gain
            .putInt(if (body.size >= 4) ByteBuffer.wrap(body, 0, 4).int else NO_ID)
            .array()
    }

    /**
     * A bolus: tenth pulses and the time between two tenth pulses. Before the pod runs, this is one of the two priming
     * steps. UNVERIFIED: that a pod which gives a bolus refuses another one, and with which NAK code. The driver never
     * sends one then.
     */
    private fun startBolus(body: ByteArray): Boolean {
        val s = state
        if (s.bolus != null || body.size < BOLUS_BODY_MIN) return false
        val buffer = ByteBuffer.wrap(body, 1, 6)
        val pulses = (buffer.short.toInt() and 0xffff) / TENTHS
        val pulseMs = buffer.int / USEC_OF_TENTH_PULSE_TO_MS_OF_PULSE
        val priming = !s.running
        s.bolus = RunningPodBolus(now(), pulses, pulseMs.toLong(), priming).also {
            if (!priming) {
                it.faultAtPulses = nextBolusFaultAtPulses
                nextBolusFaultAtPulses = null
                s.bolusPulsesNotDelivered = 0
            }
        }
        if (priming) {
            s.podStatus = if (s.podStatus < EmulatedPodState.STATUS_BASAL_PROGRAM_SET) EmulatedPodState.STATUS_ENGAGING_CLUTCH_DRIVE
            else EmulatedPodState.STATUS_PRIMING
        }
        return true
    }

    /**
     * A temp basal. The duration is the slot count of the 0x1a block, the rate comes from the time between two tenth
     * pulses of the 0x16 block. UNVERIFIED: that a pod which runs a temp basal refuses a new one. The driver always
     * cancels first.
     */
    private fun startTempBasal(insulin: ByteArray, program: ByteArray): Boolean {
        val s = state
        if (s.tempBasal != null || insulin.size <= SLOT_COUNT_OFFSET || program.size < TEMP_BASAL_BODY_MIN) return false
        val slots = insulin[SLOT_COUNT_OFFSET].toInt() and 0xff
        val delay = ByteBuffer.wrap(program, TEMP_BASAL_FIRST_ELEMENT + 2, 4).int
        // The top bit marks a rate of zero: then the delay is only the length of a slot.
        val pulsesPerHour = if (delay < 0) 0.0 else Math.round(USEC_PER_HOUR / TENTHS / delay * 1000.0) / 1000.0
        s.tempBasal = RunningPodTempBasal(pulsesPerHour, now() + slots * SLOT_MS)
        return true
    }

    /**
     * The basal program, read from the table of the 0x1a block: pulses per half hour, for runs of half hours. The block
     * also says in which half hour of its day the pod is now, and how far into it.
     */
    private fun setBasal(insulin: ByteArray) {
        val s = state
        if (insulin.size < BASAL_TABLE_OFFSET) return
        val currentSlot = insulin[SLOT_COUNT_OFFSET].toInt() and 0xff
        val eighthSecondsLeft = ByteBuffer.wrap(insulin, SLOT_COUNT_OFFSET + 1, 2).short.toInt() and 0xffff
        val slots = IntArray(SLOTS_PER_DAY)
        var slot = 0
        var at = BASAL_TABLE_OFFSET
        while (at + 1 < insulin.size && slot < SLOTS_PER_DAY) {
            val b0 = insulin[at].toInt() and 0xff
            val count = (b0 shr 4) + 1
            val extraAlternatePulse = b0 and 0x08 != 0
            val pulses = ((b0 and 0x03) shl 8) or (insulin[at + 1].toInt() and 0xff)
            for (n in 0 until count) {
                if (slot >= SLOTS_PER_DAY) break
                // Every second half hour of the run gets one pulse more, for a rate with an odd number of pulses per hour.
                slots[slot++] = pulses + if (extraAlternatePulse && n % 2 == 1) 1 else 0
            }
            at += 2
        }
        s.basalPulsesPerSlot = slots
        s.basalDayStart = now() - (currentSlot * SLOT_MS + (SLOT_MS - eighthSecondsLeft * EIGHTH_SECOND_MS))
        s.suspended = false
        if (s.podStatus == EmulatedPodState.STATUS_CLUTCH_DRIVE_ENGAGED) s.podStatus = EmulatedPodState.STATUS_BASAL_PROGRAM_SET
    }

    private fun stopDelivery(flags: Int) {
        val s = state
        if (flags and STOP_BOLUS != 0) s.bolus?.let { endBolus(it) }
        if (flags and STOP_TEMP_BASAL != 0) s.tempBasal = null
        if (flags and STOP_BASAL != 0) s.suspended = true
    }

    /**
     * A deactivated pod answers the deactivate command with a default status that says "deactivated" and
     * "suspended": seen on 2026-11-15 on a Dash pod (firmware 11.7.0) and an Omnipod 5 pod (firmware 9.0.4).
     */
    private fun deactivate() {
        val s = state
        s.bolus?.let { endBolus(it) }
        s.tempBasal = null
        s.suspended = true
        s.podStatus = EmulatedPodState.STATUS_DEACTIVATED
    }

    // ---- Time ----------------------------------------------------------------------------------------------------

    /** Brings the pod up to the present: the pulses of the bolus, of the basal and of the temp basal, and their ends. */
    private fun settle() {
        val s = state
        val nowMs = now()
        s.bolus?.let { bolus ->
            val due = min(bolus.pulses.toLong(), (nowMs - bolus.startedAt) / bolus.pulseMs.coerceAtLeast(1)).toInt()
            val faultAt = bolus.faultAtPulses
            if (faultAt != null && due >= faultAt) {
                give(faultAt - bolus.givenPulses)
                bolus.givenPulses = faultAt
                val at = bolus.startedAt + faultAt * bolus.pulseMs
                settleBasal(at)
                fault(ALARM_OCCLUDED, at)
            } else {
                give(due - bolus.givenPulses)
                bolus.givenPulses = due
                if (due >= bolus.pulses && !s.faulted) endBolus(bolus)
            }
        }
        settleBasal(nowMs)
        if (s.running) {
            s.podStatus = if (s.reservoirPulses < EmulatedPodState.REPORTED_RESERVOIR_PULSES) EmulatedPodState.STATUS_RUNNING_BELOW_MIN_VOLUME
            else EmulatedPodState.STATUS_RUNNING_ABOVE_MIN_VOLUME
        }
    }

    /**
     * The basal and the temp basal from the last settle until [until]. UNVERIFIED: the times of the single pulses. A
     * real pod spreads the pulses of a half hour over it; here the rate is summed up and each whole pulse is given.
     */
    private fun settleBasal(until: Long) {
        val s = state
        val slots = s.basalPulsesPerSlot
        var at = s.settledAt
        if (slots == null || s.faulted || s.podStatus == EmulatedPodState.STATUS_DEACTIVATED || at >= until) {
            if (until > at) s.settledAt = until
            s.tempBasal?.let { if (until >= it.endsAt) s.tempBasal = null }
            return
        }
        while (at < until) {
            val intoDay = Math.floorMod(at - s.basalDayStart, DAY_MS)
            var end = min(until, at + (SLOT_MS - intoDay % SLOT_MS))
            val temp = s.tempBasal?.takeIf { at < it.endsAt }
            if (temp != null) end = min(end, temp.endsAt)
            val pulsesPerHour = when {
                temp != null -> temp.pulsesPerHour
                s.suspended  -> 0.0
                else         -> slots[(intoDay / SLOT_MS).toInt()] * 2.0
            }
            s.basalFraction += pulsesPerHour * (end - at) / HOUR_MS
            at = end
        }
        s.tempBasal?.let { if (until >= it.endsAt) s.tempBasal = null }
        val whole = floor(s.basalFraction).toInt()
        s.basalFraction -= whole
        give(whole)
        s.settledAt = until
    }

    private fun give(pulses: Int) {
        if (pulses <= 0) return
        val s = state
        val given = min(pulses, s.reservoirPulses)
        s.reservoirPulses -= given
        s.totalPulses += given
    }

    private fun endBolus(bolus: RunningPodBolus) {
        val s = state
        s.bolus = null
        if (bolus.priming) {
            if (bolus.givenPulses >= bolus.pulses) {
                s.podStatus = if (s.podStatus == EmulatedPodState.STATUS_ENGAGING_CLUTCH_DRIVE) EmulatedPodState.STATUS_CLUTCH_DRIVE_ENGAGED
                else EmulatedPodState.STATUS_RUNNING_ABOVE_MIN_VOLUME
            }
            return
        }
        s.bolusPulsesNotDelivered = bolus.pulses - bolus.givenPulses
        if (bolus.givenPulses > 0) s.deliveredBoluses += bolus.givenPulses * PULSE_UNITS
    }

    private fun fault(alarmType: Int, at: Long) {
        val s = state
        if (s.faulted) return
        s.bolus?.let { endBolus(it) }
        s.tempBasal = null
        s.podStatusAtAlarm = s.podStatus
        s.alarmType = alarmType
        s.alarmMinutes = minutesSince(at)
        s.podStatus = EmulatedPodState.STATUS_ALARM
        aapsLogger?.debug(LTag.PUMPEMULATOR, "Pod: fault $alarmType")
    }

    private fun minutesSince(at: Long): Int =
        if (state.activatedAt == 0L) 0 else ((at - state.activatedAt) / MINUTE_MS).toInt().coerceIn(0, MAX_MINUTES)

    // ---- Answers -------------------------------------------------------------------------------------------------

    private fun deliveryStatus(): Int {
        val s = state
        val bolus = s.bolus
        return when {
            s.faulted || s.podStatus == EmulatedPodState.STATUS_DEACTIVATED -> DELIVERY_SUSPENDED
            bolus != null && bolus.priming                                   -> DELIVERY_PRIMING
            bolus != null && s.tempBasal != null                             -> DELIVERY_BOLUS_AND_TEMP_BASAL
            bolus != null                                                    -> DELIVERY_BOLUS_AND_BASAL
            s.tempBasal != null                                              -> DELIVERY_TEMP_BASAL
            s.basalPulsesPerSlot == null || s.suspended                      -> DELIVERY_SUSPENDED
            else                                                             -> DELIVERY_BASAL
        }
    }

    private fun bolusPulsesLeft(): Int = state.bolus?.takeIf { !it.priming }?.let { it.pulses - it.givenPulses } ?: state.bolusPulsesNotDelivered

    /** Above 50 U the pod only says "more than 50 U": all bits set. */
    private fun reportedReservoir(): Int =
        if (state.reservoirPulses > EmulatedPodState.REPORTED_RESERVOIR_PULSES) RESERVOIR_ABOVE_50 else state.reservoirPulses

    /** The 10 byte status, as `DefaultStatusResponse` reads it. */
    private fun defaultStatus(): ByteArray {
        val s = state
        val first = ((s.totalPulses and 0x1fff) shl 15) or ((s.lastProgrammingSequence and 0x0f) shl 11) or (bolusPulsesLeft() and 0x7ff)
        val last = ((s.activeAlerts and 0xff) shl 23) or ((minutesSince(now()) and 0x1fff) shl 10) or (reportedReservoir() and 0x3ff)
        return ByteBuffer.allocate(DEFAULT_STATUS_SIZE)
            .put(0x1d.toByte())
            .put(((deliveryStatus() shl 4) or (s.podStatus and 0x0f)).toByte())
            .putInt(first)
            .putInt(last)
            .array()
    }

    /** The 24 byte status with the fault, as `AlarmStatusResponse` reads it. */
    private fun alarmStatus(): ByteArray {
        val s = state
        return ByteBuffer.allocate(ALARM_STATUS_SIZE)
            .put(0x02.toByte()).put((ALARM_STATUS_SIZE - 2).toByte()).put(STATUS_PAGE_ALARM.toByte())
            .put(s.podStatus.toByte())
            .put(deliveryStatus().toByte())
            .putShort(bolusPulsesLeft().toShort())
            .put(s.lastProgrammingSequence.toByte())
            .putShort(s.totalPulses.toShort())
            .put(s.alarmType.toByte())
            .putShort(s.alarmMinutes.toShort())
            .putShort(reportedReservoir().toShort())
            .putShort(minutesSince(now()).toShort())
            .put(s.activeAlerts.toByte())
            .put((if (s.alarmType == ALARM_OCCLUDED) 1 else 0).toByte())
            .put(s.podStatusAtAlarm.toByte())
            .put(0.toByte()) // rssi and gain
            .put(s.podStatusAtAlarm.toByte())
            .putShort(0.toShort())
            .array()
    }

    /**
     * Status page 1, as `PodInfoTriggeredAlertsResponse` reads it: for each of the 8 alert slots the pod minute at which
     * it fired, or 0. UNVERIFIED: the word before the slots. Only the Omnipod 5 driver asks for this page.
     */
    private fun triggeredAlerts(): ByteArray {
        val buffer = ByteBuffer.allocate(TRIGGERED_ALERTS_SIZE)
            .put(0x02.toByte()).put((TRIGGERED_ALERTS_SIZE - 2).toByte()).put(STATUS_PAGE_ALERTS.toByte())
            .putShort(0.toShort())
        state.alertMinutes.forEach { buffer.putShort(it.toShort()) }
        return buffer.array()
    }

    /**
     * Status page 5, as `PodInfoActivationTimeResponse` reads it: the fault and its minute, and the date and time of
     * the activation. UNVERIFIED: the 8 bytes between them. Only the Omnipod 5 driver asks for this page.
     */
    private fun activationTime(): ByteArray {
        val s = state
        val time = Calendar.getInstance().apply { timeInMillis = s.activatedAt }
        return ByteBuffer.allocate(ACTIVATION_TIME_SIZE)
            .put(0x02.toByte()).put((ACTIVATION_TIME_SIZE - 2).toByte()).put(STATUS_PAGE_TIME.toByte())
            .put(s.alarmType.toByte())
            .putShort(s.alarmMinutes.toShort())
            .put(ByteArray(8))
            .put((time[Calendar.MONTH] + 1).toByte()).put(time[Calendar.DATE].toByte()).put((time[Calendar.YEAR] % 100).toByte())
            .put(time[Calendar.HOUR_OF_DAY].toByte()).put(time[Calendar.MINUTE].toByte())
            .array()
    }

    /** UNVERIFIED: the two bytes after the error code. A real pod puts its alarm type and status there. */
    private fun nak(error: Int): ByteArray =
        byteArrayOf(0x06, 0x03, error.toByte(), state.alarmType.toByte(), state.podStatus.toByte())

    /**
     * The answer with the pod id, the sequence number and length, and a CRC. The number is that of the command plus one,
     * as seen on real pods (see `Session.validateSequenceNumber`). UNVERIFIED: the CRC. The CRC of a real Dash pod is
     * not the one of the driver (see `Session.validateCrc`), and the Dash driver does not check it.
     */
    private fun envelope(podId: Int, sequence: Int, answer: ByteArray): ByteArray {
        val body = ByteBuffer.allocate(HEADER_SIZE + answer.size)
            .putInt(podId)
            .putShort(((((sequence + 1) and 0x0f) shl 10) or answer.size).toShort())
            .put(answer)
            .array()
        return ByteBuffer.allocate(body.size + CRC_SIZE).put(body).putShort(MessageUtil.createCrc(body)).array()
    }

    private class Block(val type: Int, val body: ByteArray)

    private fun parseBlocks(encoded: ByteArray, from: Int, to: Int): List<Block> {
        val blocks = ArrayList<Block>()
        var at = from
        while (at + 2 <= to) {
            val length = encoded[at + 1].toInt() and 0xff
            if (at + 2 + length > to) break
            blocks += Block(encoded[at].toInt() and 0xff, encoded.copyOfRange(at + 2, at + 2 + length))
            at += 2 + length
        }
        return blocks
    }

    companion object {

        val KEY = PumpEmulators.Key<PodEmulator>("omnipod-dash")
        val KEY_OMNIPOD_5 = PumpEmulators.Key<PodEmulator>("omnipod-5")

        /** The emulator a test saved with [saveState], for the app start after a kill. */
        fun restore(saved: ByteArray, aapsLogger: AAPSLogger? = null, productId: Int = PRODUCT_ID_DASH): PodEmulator =
            PodEmulator(aapsLogger, ObjectInputStream(ByteArrayInputStream(saved)).use { it.readObject() as EmulatedPodState }, productId = productId)

        /** A pod with a random address and serial, so it is never taken for a real pod or for the pod before it. */
        fun newPod(): EmulatedPodState = EmulatedPodState().apply {
            address = "EE:" + (1..5).joinToString(":") { "%02X".format(Random.nextInt(256)) }
            lotNumber = Random.nextInt(1, Int.MAX_VALUE)
            podSequenceNumber = Random.nextInt(1, Int.MAX_VALUE)
        }

        private val KNOWN_COMMANDS = setOf(
            EmulatedCommand.BOLUS_START, EmulatedCommand.BOLUS_STOP, EmulatedCommand.TEMP_BASAL_START,
            EmulatedCommand.TEMP_BASAL_STOP, EmulatedCommand.BASAL_PROFILE, EmulatedCommand.STATUS
        )

        // Command blocks, see `CommandType` in the driver.
        private const val CMD_SET_UNIQUE_ID = 0x03
        private const val CMD_GET_VERSION = 0x07
        private const val CMD_GET_STATUS = 0x0e
        private const val CMD_SILENCE_ALERTS = 0x11
        private const val CMD_PROGRAM_BASAL = 0x13
        private const val CMD_PROGRAM_TEMP_BASAL = 0x16
        private const val CMD_PROGRAM_BOLUS = 0x17
        private const val CMD_PROGRAM_ALERTS = 0x19
        private const val CMD_PROGRAM_INSULIN = 0x1a
        private const val CMD_DEACTIVATE = 0x1c
        private const val CMD_PROGRAM_BEEPS = 0x1e
        private const val CMD_STOP_DELIVERY = 0x1f

        private const val STATUS_PAGE_DEFAULT = 0x00
        private const val STATUS_PAGE_ALERTS = 0x01
        private const val STATUS_PAGE_ALARM = 0x02
        private const val STATUS_PAGE_TIME = 0x05

        private const val STOP_BASAL = 0x01
        private const val STOP_TEMP_BASAL = 0x02
        private const val STOP_BOLUS = 0x04
        private const val STOP_ALL = 0x07

        // `DeliveryStatus` in the driver.
        private const val DELIVERY_SUSPENDED = 0x00
        private const val DELIVERY_BASAL = 0x01
        private const val DELIVERY_TEMP_BASAL = 0x02
        private const val DELIVERY_PRIMING = 0x04
        private const val DELIVERY_BOLUS_AND_BASAL = 0x05
        private const val DELIVERY_BOLUS_AND_TEMP_BASAL = 0x06

        // `AlarmType`, `AlertType` and `NakErrorType` in the driver.
        private const val ALARM_OCCLUDED = 0x14
        private const val ALARM_EMPTY_RESERVOIR = 0x18
        private const val ALERT_LOW_RESERVOIR = 0x10
        private const val ALERT_LOW_RESERVOIR_SLOT = 4
        private const val NAK_ILLEGAL_PARAM = 0x07
        private const val NAK_ILLEGAL_COMMAND_STATE = 0x13

        // What a new pod says of itself, from the real answers in the driver's tests.
        private val FIRMWARE = byteArrayOf(0x04, 0x0a, 0x00)
        private val BLE_FIRMWARE = byteArrayOf(0x01, 0x03, 0x00)
        const val PRODUCT_ID_DASH = 0x04
        const val PRODUCT_ID_OMNIPOD_5 = 0x05
        private const val PULSE_VOLUME: Short = 0x1388
        /** Eighths of a second between two pulses: 2 s for a bolus, 1 s for the priming. */
        private const val PULSE_RATE: Byte = 0x10
        private const val PRIME_PULSE_RATE: Byte = 0x08
        private const val POD_LIFE_HOURS: Byte = 0x50
        private const val NO_ID = -1

        private const val HEADER_SIZE = 6
        private const val CRC_SIZE = 2
        private const val ENVELOPE_SIZE = HEADER_SIZE + CRC_SIZE
        private const val NONCE_SIZE = 4
        private const val ALERT_SIZE = 6
        private const val VERSION_ANSWER_SIZE = 23
        private const val SET_UNIQUE_ID_ANSWER_SIZE = 29
        private const val DEFAULT_STATUS_SIZE = 10
        private const val ALARM_STATUS_SIZE = 24
        private const val TRIGGERED_ALERTS_SIZE = 21
        private const val ACTIVATION_TIME_SIZE = 19
        private const val RESERVOIR_ABOVE_50 = 0x3ff
        private const val MAX_MINUTES = 0x1fff

        /** In the body of a 0x1a block: after the nonce (4), the delivery type (1) and the checksum (2). */
        private const val SLOT_COUNT_OFFSET = 7
        private const val BASAL_TABLE_OFFSET = 12
        private const val BOLUS_BODY_MIN = 7
        /** In the body of a 0x16 block: after the reminder (1), the slot index (1), the tenth pulses (2) and the delay (4). */
        private const val TEMP_BASAL_FIRST_ELEMENT = 8
        private const val TEMP_BASAL_BODY_MIN = TEMP_BASAL_FIRST_ELEMENT + 6

        private const val TENTHS = 10
        /** Microseconds between two tenth pulses, to milliseconds between two pulses. */
        private const val USEC_OF_TENTH_PULSE_TO_MS_OF_PULSE = 100
        private const val USEC_PER_HOUR = 3_600_000_000.0
        private const val EIGHTH_SECOND_MS = 125L
        private const val MINUTE_MS = 60_000L
        private const val SLOT_MS = 30 * MINUTE_MS
        private const val HOUR_MS = 60.0 * MINUTE_MS
        private const val DAY_MS = 24 * 60 * MINUTE_MS
        private const val SLOTS_PER_DAY = 48
        private const val HOURS_PER_DAY = 24
    }
}
