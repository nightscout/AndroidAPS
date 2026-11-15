package app.aaps.pump.diaconn.emulator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.emulator.EmulatedAlarm
import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import app.aaps.core.interfaces.pump.emulator.EmulatedExtendedBolus
import app.aaps.core.interfaces.pump.emulator.EmulatedPumpState
import app.aaps.core.interfaces.pump.emulator.EmulatedTempBasal
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Random
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A Diaconn G8 insulin pump: it answers the requests of the driver, gives insulin and keeps the pump log.
 *
 * Every setting takes two steps, as on the real pump: the setting is answered with an OTP and is done only when the
 * confirm (0x37) with that OTP comes. A bolus is given at the bolus speed, and its end is sent by the pump itself as a
 * report (0xE4). Bolus progress (0xEA) is sent from fw 3.53.
 *
 * Time: the pump clock is the phone clock plus [DiaconnPumpState.clockOffsetMs]. Nothing runs faster than the phone
 * clock. The basal, the end of a temp basal or an extended bolus, and the hourly and daily log records are counted up to
 * now when something looks at the pump (`settle`). Only a running bolus has a timer, for its progress and end reports.
 *
 * Each behaviour copies a real G8 where it says so; `UNVERIFIED` marks a guess.
 */
class DiaconnPumpEmulator(val state: DiaconnPumpState = DiaconnPumpState(), private val aapsLogger: AAPSLogger? = null) {

    /** How long a bolus takes, see `PumpEmulatorControl.speed`. Only the bolus gets shorter, never the clock. */
    @Volatile var speed: EmulatorSpeed = EmulatorSpeed.REAL

    /** Gets the reports the pump sends by itself: bolus progress and end. The transport sends them while a link is up. */
    @Volatile internal var reportSink: ((ByteArray) -> Unit)? = null

    /**
     * What the pump sends for one request: reports that were due before it, the answer or null, and reports it caused.
     * [heldUntil] is the time before which none of it goes out, or 0: the pump first carries out a temp basal it confirmed.
     */
    internal class Exchange(val reportsBefore: List<ByteArray>, val answer: ByteArray?, val reportsAfter: List<ByteArray>, val heldUntil: Long = 0L)

    /**
     * A setting the pump confirmed at [confirmedAt] and carries out at [dueAt]. With [holdsRequests], a request that comes
     * before then waits for it.
     */
    private class DelayedSetting(val dueAt: Long, val confirmedAt: Long, val holdsRequests: Boolean, val apply: (Long) -> Unit)

    /**
     * A setting that waits for its confirm. [refusal] is checked at the setting and again at the confirm,
     * [confirmRefusal] only at the confirm.
     */
    private class PendingSetting(
        val otp: Int,
        val command: EmulatedCommand?,
        val refusal: () -> Int,
        val confirmRefusal: () -> Int,
        val apply: (Long) -> Unit
    )

    private enum class BolusEnd { DONE, USER_STOP, OCCLUSION }

    // All below under the lock of state.
    private val pending = HashMap<Int, PendingSetting>()

    // Settings the pump carries out a moment after the answer to their confirm.
    private val delayed = ArrayList<DelayedSetting>()

    /** The 4 groups of a basal write (6 hours each, in 0.01 U/h) until its confirm. */
    private val basalGroups = arrayOfNulls<IntArray>(4)

    /** Before this time the pump refuses the start of the basal it was written, see `basalWritten`. */
    private var basalReadyAt = 0L
    private val outbox = ArrayList<ByteArray>()

    /**
     * The number of the messages the pump sends of its own: reports and big log answers. Real G8 fw 2.50 (2026-11-11):
     * reports 0x24 and 0x25, then big log answers 0x27 and 0x29 to requests 54 and 55. UNVERIFIED: that it counts up by
     * one, and from where.
     */
    private var ownSeq = 0x20
    private var bolusTimer: ScheduledFuture<*>? = null
    private val random = Random()

    private val lostCommands = ArrayList<EmulatedCommand>()
    private val lostAnswers = ArrayList<EmulatedCommand>()

    init {
        // The bolus of a restored pump goes on.
        synchronized(state) { armBolusTimer() }
    }

    // --- Requests ---

    /** The pump gets [request] from the driver. */
    internal fun receive(request: ByteArray): Exchange = synchronized(state) {
        val now = System.currentTimeMillis()
        // Real G8 fw 2.50 (2026-11-11): a request that comes while the pump has yet to carry out a setting it confirmed
        // mostly waits until it is carried out, and is answered after its reports. A log status sent 0.2 s after the
        // confirm of a temp basal start was answered 1.9 s later, and a start sent right after the confirm of a stop was
        // answered when the stop was done, and was taken. After the start of an extended bolus a log status was answered
        // at once, without its record.
        val holding = delayed.filter { it.holdsRequests }
        val heldUntil = holding.maxOfOrNull { it.dueAt }?.takeIf { it > now } ?: 0L
        carryOut(delayed.filter { it.holdsRequests || it.dueAt <= now || it.dueAt <= heldUntil })
        settle(now)
        val before = drainOutbox()
        val answer = answer(request, now)
        Exchange(before, answer, drainOutbox(), heldUntil)
    }

    /** Carries out [settings] of [delayed], in their order, each with the time of its confirm. */
    private fun carryOut(settings: List<DelayedSetting>) {
        delayed.removeAll(settings)
        settings.sortedBy { it.dueAt }.forEach { it.apply(it.confirmedAt) }
    }

    /** A new link: a setting that was not confirmed on the old one is gone. UNVERIFIED: a real pump drops it. */
    internal fun newLink() = synchronized(state) {
        pending.clear()
        basalGroups.fill(null)
    }

    private fun answer(request: ByteArray, now: Long): ByteArray? {
        // UNVERIFIED: a real pump ignores what is no 20-byte request.
        if (request.size != DiaconnPacket.SIZE || request.u8(0) != DiaconnPacket.SOP) {
            log("not a request: ${hex(request)}")
            return null
        }
        val msgType = request.u8(1)
        val seq = request.u8(2)
        val type = DiaconnPacket.type(msgType)
        if (type != DiaconnPacket.TYPE_SETTING && type != DiaconnPacket.TYPE_INQUIRY) return null
        if (request[DiaconnPacket.SIZE - 1] != DiaconnPacket.crc(request, DiaconnPacket.SIZE - 1)) {
            // UNVERIFIED: a real pump answers a request with a wrong CRC, with the CRC error code of its kind.
            log("wrong CRC: ${hex(request)}")
            return result(msgType, seq, if (type == DiaconnPacket.TYPE_SETTING) CRC_ERROR else INQUIRY_CRC_ERROR)
        }
        val command = commandOf(request)
        if (take(lostCommands, command)) {
            log("$command lost before the pump: ${hex(request)}")
            return null
        }
        state.commands++
        return if (type == DiaconnPacket.TYPE_SETTING) setting(request, now) else inquiry(request, now, command)
    }

    /** The command a test can name for [request], or null. */
    private fun commandOf(request: ByteArray): EmulatedCommand? = when (request.u8(1)) {
        SNACK_BOLUS_SETTING                                                   -> EmulatedCommand.BOLUS_START
        EXTENDED_BOLUS_SETTING                                                -> EmulatedCommand.EXTENDED_BOLUS_START
        BASAL_SETTING                                                         -> EmulatedCommand.BASAL_PROFILE

        INJECTION_CANCEL_SETTING                                              -> when (request.u8(4)) {
            SNACK_BOLUS_SETTING    -> EmulatedCommand.BOLUS_STOP
            EXTENDED_BOLUS_SETTING -> EmulatedCommand.EXTENDED_BOLUS_STOP
            else                   -> null
        }

        TEMP_BASAL_SETTING                                                    -> when (request.u8(4)) {
            TEMP_BASAL_START, TEMP_BASAL_REPLACE -> EmulatedCommand.TEMP_BASAL_START
            TEMP_BASAL_STOP                      -> EmulatedCommand.TEMP_BASAL_STOP
            else                                 -> null
        }

        // Every read of what the pump runs now.
        BIG_MAIN_INFO_INQUIRE, BIG_APS_MAIN_INFO_INQUIRE, TEMP_BASAL_INQUIRE -> EmulatedCommand.STATUS
        else                                                                  -> null
    }

    // --- Inquiries ---

    private fun inquiry(request: ByteArray, now: Long, command: EmulatedCommand?): ByteArray? {
        val msgType = request.u8(1)
        val seq = request.u8(2)
        val answer = when (msgType) {
            SERIAL_NUM_INQUIRE        -> inquiryAnswer(msgType, seq) {
                u8(state.country).u8(state.productType).u8(state.makeYear).u8(state.makeMonth).u8(state.makeDay)
                    .u8(state.lotNumber).u16(state.serialNumber).u8(state.firmwareMajor).u8(state.firmwareMinor)
            }

            BASAL_LIMIT_INQUIRE       -> inquiryAnswer(msgType, seq) { u16(hundredths(state.basalLimit)) }
            SNACK_LIMIT_INQUIRE       -> inquiryAnswer(msgType, seq) { u16(hundredths(state.maxBolus)).u16(hundredths(state.maxBolusPerDay)) }
            TIME_INQUIRE              -> inquiryAnswer(msgType, seq) { clock(now) }
            LOG_STATUS_INQUIRE        -> inquiryAnswer(msgType, seq) { u16(state.nextLogNumber).u8(state.wrappingCount) }
            INCARNATION_INQUIRE       -> inquiryAnswer(msgType, seq) { u16(state.incarnation) }
            BOLUS_SPEED_INQUIRE       -> inquiryAnswer(msgType, seq) { u8(state.bolusSpeed) }
            SNACK_INQUIRE             -> inquiryAnswer(msgType, seq) { snack() }
            TEMP_BASAL_INQUIRE        -> inquiryAnswer(msgType, seq) { tempBasal(now) }
            SOUND_INQUIRE             -> inquiryAnswer(msgType, seq) { u8(state.beepAndAlarm).u8(state.alarmIntensity) }
            DISPLAY_TIME_INQUIRE      -> inquiryAnswer(msgType, seq) { u8(state.lcdOnTime) }
            LANGUAGE_INQUIRE          -> inquiryAnswer(msgType, seq) { u8(state.language) }
            BIG_MAIN_INFO_INQUIRE     -> inquiryAnswer(msgType, seq, big = true) { bigMainInfo(now) }
            BIG_APS_MAIN_INFO_INQUIRE -> inquiryAnswer(msgType, seq, big = true) { bigApsMainInfo(now) }
            // Real G8: not the number of the request, see ownSeq.
            BIG_LOG_INQUIRE           -> inquiryAnswer(msgType, nextOwnSeq(), big = true) { bigLog(start = request.u16(4), end = request.u16(6)) }
            // UNVERIFIED: the answer of a real pump to an inquiry it does not know.
            else                      -> result(msgType, seq, INQUIRY_PROTOCOL_ERROR)
        }
        if (take(lostAnswers, command)) {
            log("$command done, its answer is lost")
            return null
        }
        return answer
    }

    /** UNVERIFIED: the bolus fields while no bolus runs: 0, as the snack fields of the real main info answer. */
    private fun PacketData.snack(): PacketData {
        val bolus = state.runningBolus
        return u8(status(bolus != null)).u16(bolus?.setHundredths ?: 0).u16(bolus?.givenHundredths ?: 0).u8(state.bolusSpeed)
    }

    /**
     * Status, time, rate and elapsed minutes. Real G8 (2026-11-11): time and rate stay in the answer after the temp basal
     * ended, and the elapsed minutes count the time the insulin was stopped. UNVERIFIED: that the elapsed minutes go on
     * after the end; a real G8 answered 238 and later 896 while none ran.
     */
    private fun PacketData.tempBasal(now: Long): PacketData {
        val elapsed = if (state.tempBasalStartedAt == 0L) 0 else ((now - state.tempBasalStartedAt) / MS_PER_MINUTE).toInt().coerceIn(0, 0xFFFF)
        return u8(status(state.tempBasalRunning)).u8(state.tempBasalTime).u16(state.tempBasalRateRatio).u16(elapsed)
    }

    /**
     * yy MM dd hh mm ss of the pump clock, in whole seconds. A clock that was never set: real G8 (2026-11-11) answered
     * 2025-03-19 00:65:21, the minutes since it was switched on, never carried into the hour. UNVERIFIED: that every such
     * pump has 2025-03-19, and what comes after minute 255.
     */
    private fun PacketData.clock(now: Long): PacketData {
        if (state.clockNeverSet) {
            val seconds = ((now - state.poweredOnAt) / 1000).coerceAtLeast(0)
            return u8(NEVER_SET_DATE.year - 2000).u8(NEVER_SET_DATE.monthValue).u8(NEVER_SET_DATE.dayOfMonth).u8(0)
                .u8((seconds / 60).toInt()).u8((seconds % 60).toInt())
        }
        val time = pumpLocal(now)
        return u8(time.year - 2000).u8(time.monthValue).u8(time.dayOfMonth).u8(time.hour).u8(time.minute).u8(time.second)
    }

    /** Bytes 5..46 of both main info answers. */
    private fun PacketData.mainInfoHead(now: Long): PacketData {
        u16(hundredths(state.reservoir)).u8(state.battery).u8(state.basalPattern)
        u8(status(state.tempBasalRunning)).u8(NOT_RUNNING).u8(status(state.runningBolus != null)).u8(status(state.extendedBolus != null)).u8(NOT_RUNNING)
        u8(pauseStatus())
        clock(now)
        u8(state.country).u8(state.productType).u8(state.makeYear).u8(state.makeMonth).u8(state.makeDay).u8(state.lotNumber)
        u16(state.serialNumber).u8(state.firmwareMajor).u8(state.firmwareMinor)
        u16(state.nextLogNumber).u8(state.wrappingCount)
        u8(state.bolusSpeed)
        tempBasal(now)
        // UNVERIFIED: the base status byte; a real G8 answered 2 while it gave its basal, the emulator answers the pause status.
        // UNVERIFIED: the amount given in this hour; a real G8 answered 0 at 18:22 with 1.20 U/h.
        u8(pauseStatus()).u8(pumpLocal(now).hour).u16(hundredths(patternRate(now))).u16(hundredths(state.hourBasalAfter))
        return this
    }

    /** UNVERIFIED: a real G8 left the amount and the time of an old extended bolus in these fields; the emulator answers 0. */
    private fun PacketData.square(now: Long): PacketData {
        val extended = state.extendedBolus
        val elapsed = extended?.let { ((now - it.startedAt) / MS_PER_MINUTE).toInt().coerceIn(0, it.minutes) } ?: 0
        return u8(status(extended != null)).u16(extended?.minutes ?: 0).u16(elapsed).u16(extended?.amountHundredths ?: 0).u16(extended?.givenHundredths(now) ?: 0)
    }

    /** No dual bolus runs on this pump: the driver cannot start one, and no hook does. */
    private fun PacketData.dual(): PacketData = u8(NOT_RUNNING).u16(0).u16(0).u16(0).u16(0).u16(0).u16(0)

    private fun PacketData.recent(): PacketData {
        for (i in 0..1) {
            val bolus = state.recentBoluses.getOrNull(i)
            u8(bolus?.kind ?: 0).u32(bolus?.logSeconds ?: 0L).u16(bolus?.amountHundredths ?: 0)
        }
        return this
    }

    private fun PacketData.today(): PacketData = u16(hundredths(state.todayBasal)).u16(0).u16(hundredths(state.todayBolus))

    private fun PacketData.currentBasal(now: Long): PacketData =
        u8(state.basalPattern).u8(pumpLocal(now).hour).u16(hundredths(state.hourBasalBefore)).u16(hundredths(state.hourBasalAfter))

    private fun PacketData.rates(): PacketData {
        state.basalRates.forEach { u16(hundredths(it)) }
        return this
    }

    /** 0xB3, for fw below 3.0. Layout checked against a real G8 answer (2026-11-11). */
    private fun PacketData.bigMainInfo(now: Long) {
        mainInfoHead(now)
        // No meal bolus runs: kind, start, status, set, given, speed.
        u8(0).u32(0).u8(NOT_RUNNING).u16(0).u16(0).u8(state.bolusSpeed)
        snack().square(now).dual().recent().today()
        // Real G8 (2026-11-11): the meal hours 0, 10 and 15, each with 0 U.
        u8(0).u16(0).u8(10).u16(0).u8(15).u16(0)
        currentBasal(now).rates()
    }

    /** 0x94, for fw 3.0 and up: without the meal blocks, with the limits and the settings at the end. */
    private fun PacketData.bigApsMainInfo(now: Long) {
        mainInfoHead(now)
        snack().square(now).dual().recent().today().currentBasal(now).rates()
        u16(hundredths(state.basalLimit)).u8(state.mealLimitTime).u16(hundredths(state.maxBolus)).u16(hundredths(state.maxBolusPerDay))
        u8(state.beepAndAlarm).u8(state.alarmIntensity).u8(state.lcdOnTime).u8(state.language)
    }

    /**
     * The records with numbers from [start] up to, not including, [end], each as wrapping count, number and 12 bytes.
     * Real G8 (2026-11-11): one record 4054 for a read after LogStatus 4055. UNVERIFIED: a range of more than 11 records
     * (more do not fit) gets the first 11.
     */
    private fun PacketData.bigLog(start: Int, end: Int) {
        val records = state.log.filter { it.number in start until end }.sortedBy { it.number }.take(MAX_LOG_RECORDS_PER_ANSWER)
        u8(records.size)
        records.forEach { record ->
            u8(record.wrappingCount).u16(record.number)
            record.data.forEach { u8(it.toInt()) }
        }
    }

    // --- Settings ---

    private fun setting(request: ByteArray, now: Long): ByteArray? {
        val msgType = request.u8(1)
        val seq = request.u8(2)
        when (msgType) {
            APP_CONFIRM_SETTING -> return confirm(request, seq, now)
            // UNVERIFIED: the answer to a cancel with no setting waiting. The driver never sends this one.
            APP_CANCEL_SETTING  -> {
                pending.remove(request.u8(4))
                return result(msgType, seq, OK)
            }
        }
        // UNVERIFIED: the answer to a setting the emulator does not know. Among them is the meal bolus (0x06), which the
        // driver never sends.
        val setting = parseSetting(request, now) ?: return result(msgType, seq, PROTOCOL_ERROR)
        val refusal = setting.refusal()
        if (refusal != OK) {
            log("setting 0x%02x refused with %d".format(msgType, refusal))
            return result(msgType, seq, refusal)
        }
        pending[msgType] = setting
        return DiaconnPacket.build(DiaconnPacket.answerType(msgType), seq, PacketData().u8(OK).u32(setting.otp.toLong()).bytes())
    }

    /**
     * The confirm of a setting: 0x07 for a bolus, with the OTP of its answer. Only now is the setting done. A wrong OTP
     * drops the setting ("canceled, the OTP did not match").
     */
    private fun confirm(request: ByteArray, seq: Int, now: Long): ByteArray? {
        val target = request.u8(4)
        val otp = request.i32(5)
        val setting = pending.remove(target)
        if (setting == null || setting.otp != otp) {
            // UNVERIFIED: the code for a confirm with no setting waiting; 10 is the code for an OTP that does not match.
            log("confirm of 0x%02x with OTP %d: no setting waits for it".format(target, otp))
            return result(APP_CONFIRM_SETTING, seq, OTP_MISMATCH)
        }
        val refusal = setting.refusal().takeIf { it != OK } ?: setting.confirmRefusal()
        if (refusal != OK) {
            log("confirm of 0x%02x refused with %d".format(target, refusal))
            return result(APP_CONFIRM_SETTING, seq, refusal)
        }
        if (setting.command in CARRIED_OUT_LATER) {
            // Real G8 fw 2.50 (2026-11-11): a temp basal or an extended bolus is carried out 1.2 to 1.9 s after the answer
            // to its confirm, with a confirm report. Its log record has the time of the confirm. See receive for a request
            // in between.
            val delay = if (speed == EmulatorSpeed.FAST) FAST_CARRY_OUT_MS else REAL_CARRY_OUT_MS
            val extendedStart = setting.command == EmulatedCommand.EXTENDED_BOLUS_START
            delayed += DelayedSetting(now + delay, now, holdsRequests = !extendedStart) { at ->
                outbox += report(CONFIRM_REPORT) { u8(APP_CONFIRM_SETTING) }
                setting.apply(at)
            }
            // With no request in between, the pump carries it out by itself and sends its reports.
            timer.schedule({ atNow {} }, delay, TimeUnit.MILLISECONDS)
        } else {
            setting.apply(now)
        }
        if (setting.command == EmulatedCommand.BASAL_PROFILE) basalWritten(now)
        // A lost answer of a setting is the lost answer of its confirm: the setting is done then.
        if (take(lostAnswers, setting.command)) {
            log("${setting.command} done, its answer is lost")
            return null
        }
        return result(APP_CONFIRM_SETTING, seq, OK)
    }

    /**
     * After the confirm of a basal write. Real G8 fw 2.50 (2026-11-11/12): a basal setting report with result 0 came 23.2
     * to 23.7 s after the confirm (5 times). The start of the basal sent right after the report was refused with 15, one
     * sent 30.5 s after the confirm was taken. The new rates run from the confirm on, also when the start is refused.
     * UNVERIFIED: what the pump does with other requests before the report; the driver sends none.
     */
    private fun basalWritten(now: Long) {
        val fast = speed == EmulatorSpeed.FAST
        basalReadyAt = now + if (fast) FAST_BASAL_READY_MS else REAL_BASAL_READY_MS
        val delay = if (fast) FAST_BASAL_REPORT_MS else REAL_BASAL_REPORT_MS
        delayed += DelayedSetting(now + delay, now, holdsRequests = false) { outbox += report(BASAL_SETTING_REPORT) { u8(OK) } }
        timer.schedule({ atNow {} }, delay, TimeUnit.MILLISECONDS)
    }

    private fun pendingOf(command: EmulatedCommand?, refusal: () -> Int, confirmRefusal: () -> Int = { OK }, apply: (Long) -> Unit) =
        PendingSetting(otp = MIN_OTP + random.nextInt(MAX_OTP - MIN_OTP + 1), command, refusal, confirmRefusal, apply)

    /**
     * UNVERIFIED for all settings: the checks and their codes, and that the pump checks at the setting and again at the
     * confirm. The codes are those the driver knows.
     */
    private fun parseSetting(request: ByteArray, now: Long): PendingSetting? = when (request.u8(1)) {
        BASAL_PAUSE_SETTING      -> {
            val status = request.u8(4)
            pendingOf(null, { if (status == PAUSE || status == RELEASE) OK else PARAMETER_ERROR }) { at -> setSuspended(status == PAUSE, at) }
        }

        BOLUS_SPEED_SETTING      -> {
            val speed = request.u8(4)
            pendingOf(null, { if (speed in 1..8) OK else PARAMETER_ERROR }) { state.bolusSpeed = speed }
        }

        SNACK_BOLUS_SETTING      -> {
            val amount = request.u16(4)
            pendingOf(EmulatedCommand.BOLUS_START, { bolusRefusal(amount) }) { at -> startBolus(amount, at) }
        }

        EXTENDED_BOLUS_SETTING   -> {
            val minutes = request.u16(4)
            val amount = request.u16(6)
            pendingOf(EmulatedCommand.EXTENDED_BOLUS_START, { extendedBolusRefusal(amount, minutes) }) { at -> startExtendedBolus(amount, minutes, at) }
        }

        TEMP_BASAL_SETTING       -> tempBasalSetting(request)
        BASAL_SETTING            -> basalSetting(request)

        INJECTION_BASAL_SETTING  -> {
            val pattern = request.u8(4)
            // Only the basic pattern is kept; the driver sends only it.
            val refusal = when {
                pattern != BASIC_PATTERN -> PARAMETER_ERROR
                now < basalReadyAt       -> BASAL_NOT_SAVED
                else                     -> OK
            }
            pendingOf(null, { refusal }) { state.basalPattern = pattern }
        }

        SOUND_SETTING            -> {
            val beep = request.u8(4)
            val intensity = request.u8(5)
            pendingOf(null, { if (beep in 1..3 && intensity in 1..3) OK else PARAMETER_ERROR }) {
                state.beepAndAlarm = beep
                state.alarmIntensity = intensity
            }
        }

        DISPLAY_TIMEOUT_SETTING  -> {
            val lcd = request.u8(4)
            pendingOf(null, { if (lcd in 1..3) OK else PARAMETER_ERROR }) { state.lcdOnTime = lcd }
        }

        LANGUAGE_SETTING         -> {
            val language = request.u8(4)
            pendingOf(null, { if (language in 1..3) OK else PARAMETER_ERROR }) { state.language = language }
        }

        TIME_SETTING             -> timeSetting(request, now)
        INJECTION_CANCEL_SETTING -> injectionCancel(request)
        else                     -> null
    }

    /**
     * Status 1 starts, 2 stops. The TB_DTTM the app sends is copied into the records, as a real G8 did (2026-11-11).
     * Status 3 is what the driver sends on fw 3.53 and up in place of a stop and a start. UNVERIFIED: that the pump then
     * stops a running temp basal as the app would, and starts the new one.
     */
    private fun tempBasalSetting(request: ByteArray): PendingSetting {
        val status = request.u8(4)
        val time = request.u8(5)
        val ratio = request.u16(6)
        val dttm = request.i32(8)
        return when (status) {
            TEMP_BASAL_START   ->
                pendingOf(EmulatedCommand.TEMP_BASAL_START, { tempBasalStartRefusal(time, ratio, replace = false) }) { at -> startTempBasal(time, ratio, dttm, at) }

            TEMP_BASAL_STOP    ->
                pendingOf(EmulatedCommand.TEMP_BASAL_STOP, { if (state.tempBasalRunning) OK else NO_TEMP_BASAL }) { at -> stopTempBasal(dttm, at) }

            TEMP_BASAL_REPLACE ->
                pendingOf(EmulatedCommand.TEMP_BASAL_START, { if (firmwareAtLeast(3, 53)) tempBasalStartRefusal(time, ratio, replace = true) else PARAMETER_ERROR }) { at ->
                    if (state.tempBasalRunning) stopTempBasal(dttm, at)
                    startTempBasal(time, ratio, dttm, at)
                }

            else               -> pendingOf(null, { PARAMETER_ERROR }) {}
        }
    }

    /**
     * Four packets of 6 hours each, each answered with its own OTP, and one confirm for all four with the OTP of the last
     * answer, as the driver sends it. UNVERIFIED: that the pump takes the write only with all four groups.
     */
    private fun basalSetting(request: ByteArray): PendingSetting {
        val pattern = request.u8(4)
        val group = request.u8(5)
        val valid = pattern == BASIC_PATTERN && group in 1..4
        if (valid) {
            if (group == 1) basalGroups.fill(null)
            basalGroups[group - 1] = IntArray(6) { request.u16(6 + 2 * it) }
        }
        return pendingOf(
            EmulatedCommand.BASAL_PROFILE,
            refusal = { if (valid) OK else PARAMETER_ERROR },
            confirmRefusal = { if (basalGroups.any { it == null }) PARAMETER_ERROR else OK }
        ) {
            basalGroups.forEachIndexed { index, rates -> rates?.forEachIndexed { hour, rate -> state.basalRates[index * 6 + hour] = rate / 100.0 } }
            basalGroups.fill(null)
        }
    }

    /**
     * The local wall clock of the phone. UNVERIFIED: the pump counts the new time from when the setting came, and takes it
     * at the confirm.
     */
    private fun timeSetting(request: ByteArray, now: Long): PendingSetting {
        val time = runCatching { LocalDateTime.of(2000 + request.u8(4), request.u8(5), request.u8(6), request.u8(7), request.u8(8), request.u8(9)) }.getOrNull()
        val offset = time?.let { it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - now }
        return pendingOf(null, { if (offset == null) PARAMETER_ERROR else OK }) {
            state.clockOffsetMs = offset ?: state.clockOffsetMs
            state.clockNeverSet = false
        }
    }

    /** UNVERIFIED: a cancel of something that does not run is taken and does nothing. */
    private fun injectionCancel(request: ByteArray): PendingSetting = when (request.u8(4)) {
        SNACK_BOLUS_SETTING    -> pendingOf(EmulatedCommand.BOLUS_STOP, { OK }) { at -> state.runningBolus?.let { finishBolus(it, BolusEnd.USER_STOP, at) } }
        EXTENDED_BOLUS_SETTING -> pendingOf(EmulatedCommand.EXTENDED_BOLUS_STOP, { OK }) { at -> state.extendedBolus?.let { stopExtendedBolus(it, at) } }
        DUAL_BOLUS             -> pendingOf(null, { OK }) {}
        else                   -> pendingOf(null, { PARAMETER_ERROR }) {}
    }

    // --- Bolus ---

    /** UNVERIFIED: a stopped insulin refuses a bolus with 9 ("basal release is required"); an amount of 0 with 2. */
    private fun bolusRefusal(amount: Int): Int = when {
        state.suspended                                                          -> BASAL_PAUSED
        state.runningBolus != null                                               -> BOLUS_RUNNING
        amount <= 0                                                              -> PARAMETER_ERROR
        amount > hundredths(state.maxBolus)                                      -> ONE_TIME_LIMIT
        amount + hundredths(state.todayBolus) > hundredths(state.maxBolusPerDay) -> DAILY_LIMIT
        amount > hundredths(state.reservoir)                                     -> LOW_INSULIN
        else                                                                     -> OK
    }

    /**
     * Real G8 fw 2.50: a 0.1 U bolus at speed 5 ended about 7 s after the confirm, and a 0.29 U one 9.5 s after it. At
     * 12 s per unit that is 1.2 s and 3.5 s of insulin, so both waited about 6 s before the motor started. At
     * [EmulatorSpeed.FAST] both are much shorter.
     */
    private fun startBolus(amount: Int, at: Long) {
        val fast = speed == EmulatorSpeed.FAST
        val msPerHundredth = MS_PER_MINUTE / state.bolusSpeed / 100 / (if (fast) FAST_DIVISOR else 1)
        val stopAt = state.stopNextBolusAt?.let { hundredths(it) }?.takeIf { it < amount }
        state.stopNextBolusAt = null
        state.runningBolus = RunningBolus(amount, at, if (fast) FAST_START_DELAY_MS else REAL_START_DELAY_MS, msPerHundredth, stopAt, logSeconds(at))
        log("bolus of ${amount / 100.0} U started" + (stopAt?.let { ", it stops at ${it / 100.0} U" } ?: ""))
        armBolusTimer()
    }

    /** Gives the insulin of the running bolus due by [now], and ends it when it is given. */
    private fun settleBolus(now: Long) {
        val bolus = state.runningBolus ?: return
        val due = bolus.dueHundredths(now)
        if (due > bolus.givenHundredths) {
            takeInsulin((due - bolus.givenHundredths) / 100.0)
            bolus.givenHundredths = due
        }
        if (bolus.givenHundredths >= bolus.targetHundredths)
            finishBolus(bolus, if (bolus.stopAtHundredths != null) BolusEnd.OCCLUSION else BolusEnd.DONE, bolus.endsAt)
    }

    /**
     * The bolus ends: its log record, and the result report 0xE4 with the amount given. The driver knows the end of a
     * bolus only from this report. Result 1 is a stop during delivery, as the driver reads it. Real G8 fw 2.50
     * (2026-11-11): result 0 and set = given for a bolus of 0.29 U given in full. UNVERIFIED: 2 for one the pump stopped
     * itself; the driver decides from the amounts, not from the result.
     *
     * The record has the time the bolus ended, and its minutes are counted up. Real G8 fw 2.50 (2026-11-11): a 1 U bolus
     * confirmed 21:10:58 and ended 21:11:22 was logged 21:11:20 with minutes 1, and a 1 U one the user stopped at
     * 0.61 U, 7 s into the insulin, was logged at its stop with minutes 1 and reason 4.
     */
    private fun finishBolus(bolus: RunningBolus, end: BolusEnd, at: Long) {
        state.runningBolus = null
        armBolusTimer()
        val given = bolus.givenHundredths
        val minutes = ceil((at - bolus.confirmedAt - bolus.startDelayMs).coerceAtLeast(0) / MS_PER_MINUTE.toDouble()).toInt()
        val endSeconds = logSeconds(at)
        when (end) {
            BolusEnd.DONE      -> record(DiaconnLog.NORMAL_SUCCESS, endSeconds) { u16(bolus.setHundredths).u16(given).u8(minutes).u8(state.battery) }
            BolusEnd.USER_STOP -> record(DiaconnLog.NORMAL_FAIL, endSeconds) { u16(bolus.setHundredths).u16(given).u8(minutes).u8(REASON_USER_STOP).u8(state.battery) }
            BolusEnd.OCCLUSION -> record(DiaconnLog.NORMAL_FAIL, endSeconds) { u16(bolus.setHundredths).u16(given).u8(minutes).u8(REASON_OCCLUSION).u8(state.battery) }
        }
        val result = when (end) {
            BolusEnd.DONE      -> BOLUS_RESULT_DONE
            BolusEnd.USER_STOP -> BOLUS_RESULT_STOPPED
            BolusEnd.OCCLUSION -> BOLUS_RESULT_FAILED
        }
        outbox += report(SNACK_RESULT_REPORT) { u8(result).u16(bolus.setHundredths).u16(given) }
        bolusGiven(given, BOLUS_KIND_SNACK, bolus.logSeconds)
        state.deliveredBoluses.add(given / 100.0)
        log("bolus of ${bolus.setHundredths / 100.0} U ended ($end), ${given / 100.0} U given")
    }

    /** UNVERIFIED: progress as a percent of the set amount, and one report a second (fw 3.53 and up only). */
    private fun progressReport(bolus: RunningBolus): ByteArray = report(INJECTION_PROGRESS_REPORT) {
        u16(bolus.setHundredths).u16(bolus.givenHundredths).u8(state.bolusSpeed).u8(bolus.givenHundredths * 100 / bolus.setHundredths.coerceAtLeast(1))
    }

    /** Under the lock of state. The next look at the running bolus: its end, or the next progress report. */
    private fun armBolusTimer() {
        bolusTimer?.cancel(false)
        bolusTimer = null
        val bolus = state.runningBolus ?: return
        val now = System.currentTimeMillis()
        var next = bolus.endsAt
        if (firmwareAtLeast(3, 53)) next = minOf(next, now + if (speed == EmulatorSpeed.FAST) FAST_PROGRESS_MS else REAL_PROGRESS_MS)
        bolusTimer = timer.schedule({ onBolusTimer() }, (next - now).coerceAtLeast(1), TimeUnit.MILLISECONDS)
    }

    private fun onBolusTimer() = atNow {
        val bolus = state.runningBolus
        if (bolus != null && firmwareAtLeast(3, 53)) outbox.add(progressReport(bolus))
        armBolusTimer()
    }

    private fun bolusGiven(hundredths: Int, kind: Int, logSeconds: Long) {
        state.todayBolus += hundredths / 100.0
        state.recentBoluses.add(0, RecentBolus(kind, logSeconds, hundredths))
        while (state.recentBoluses.size > 2) state.recentBoluses.removeAt(state.recentBoluses.size - 1)
    }

    // --- Extended (square) bolus ---

    /** UNVERIFIED: all checks; the pump logs the time in 10-minute steps of 1..30, so 10..300 minutes in steps of 10. */
    private fun extendedBolusRefusal(amount: Int, minutes: Int): Int = when {
        state.suspended                                                          -> BASAL_PAUSED
        state.extendedBolus != null                                              -> BOLUS_RUNNING
        minutes !in 10..300 || minutes % 10 != 0 || amount <= 0                  -> PARAMETER_ERROR
        amount > hundredths(state.maxBolus)                                      -> ONE_TIME_LIMIT
        amount + hundredths(state.todayBolus) > hundredths(state.maxBolusPerDay) -> DAILY_LIMIT
        amount > hundredths(state.reservoir)                                     -> LOW_INSULIN
        else                                                                     -> OK
    }

    private fun startExtendedBolus(amount: Int, minutes: Int, at: Long) {
        state.extendedBolus = RunningExtendedBolus(amount, minutes, at)
        record(DiaconnLog.SET_SQUARE, logSeconds(at)) { u16(amount).u8(minutes / 10).u8(state.battery) }
    }

    /** UNVERIFIED: the minutes in the record; it has one byte for them, so 300 minutes are written as 255. */
    private fun finishExtendedBolus(extended: RunningExtendedBolus) {
        state.extendedBolus = null
        record(DiaconnLog.SQUARE_SUCCESS, logSeconds(extended.endsAt)) { u16(extended.amountHundredths).u8(extended.minutes.coerceAtMost(0xFF)).u8(state.battery) }
        bolusGiven(extended.amountHundredths, BOLUS_KIND_SQUARE, logSeconds(extended.startedAt))
    }

    /**
     * Real G8 fw 2.50 (2026-11-11), 0.50 U over 30 min stopped after 3 min: the record `8e 0500 03 04 45` (0.05 U given,
     * 3 min, reason 4) and the result report `01 1e00 0300 3200 0500` (stopped, 30 min, 3 min, 0.50 U set, 0.05 U given).
     */
    private fun stopExtendedBolus(extended: RunningExtendedBolus, at: Long) {
        state.extendedBolus = null
        val given = extended.givenHundredths(at)
        val minutes = ((at - extended.startedAt) / MS_PER_MINUTE).toInt().coerceIn(0, 0xFF)
        record(DiaconnLog.SQUARE_FAIL, logSeconds(at)) { u16(given).u8(minutes).u8(REASON_USER_STOP).u8(state.battery) }
        bolusGiven(given, BOLUS_KIND_SQUARE, logSeconds(extended.startedAt))
        outbox += report(EXTENDED_RESULT_REPORT) { u8(EXTENDED_STOPPED).u16(extended.minutes).u16(minutes).u16(extended.amountHundredths).u16(given) }
    }

    // --- Temp basal ---

    /** UNVERIFIED: the bounds of the rate (the driver's log reader takes 1000..2500 and 50000 and up), and code 9 while stopped. */
    private fun tempBasalStartRefusal(time: Int, ratio: Int, replace: Boolean): Int = when {
        state.suspended                                                       -> BASAL_PAUSED
        state.tempBasalRunning && !replace                                    -> TEMP_BASAL_RUNNING
        time !in 2..96                                                        -> PARAMETER_ERROR
        ratio !in ABSOLUTE_RATIO_MIN..ABSOLUTE_RATIO_MAX && ratio !in PERCENT_RATIO_MIN..PERCENT_RATIO_MAX -> PARAMETER_ERROR
        else                                                                  -> OK
    }

    /**
     * Real G8 fw 2.50 (2026-11-11), 1.15 U/h for 30 min: the report `01 02 5b 04` (running, 2, 1115) and the record
     * `92 02 5b04 f0c46c38`.
     */
    private fun startTempBasal(time: Int, ratio: Int, dttm: Int, at: Long) {
        state.tempBasalRunning = true
        state.tempBasalTime = time
        state.tempBasalRateRatio = ratio
        state.tempBasalStartedAt = at
        state.tempBasalDttm = dttm
        record(DiaconnLog.TB_START, logSeconds(at)) { u8(time).u16(ratio).u32(dttm.toLong() and 0xFFFFFFFFL) }
        outbox += report(TEMP_BASAL_REPORT) { u8(TEMP_BASAL_START).u8(time).u16(ratio) }
    }

    /**
     * Real G8 fw 2.50 (2026-11-11), a stop of 1.15 U/h after 4 min: the report `02 02 5b 04` (stopped, the time and rate
     * of the stopped one) and the record `93 5b04 04 f0c46c38`, reason 4.
     */
    private fun stopTempBasal(dttm: Int, at: Long) {
        state.tempBasalRunning = false
        record(DiaconnLog.TB_STOP, logSeconds(at)) { u16(state.tempBasalRateRatio).u8(REASON_USER_STOP).u32(dttm.toLong() and 0xFFFFFFFFL) }
        outbox += report(TEMP_BASAL_REPORT) { u8(TEMP_BASAL_STOP).u8(state.tempBasalTime).u16(state.tempBasalRateRatio) }
    }

    /**
     * The temp basal time is over. Real G8 (2026-11-11): `93 3804 00 f0c46c38`, reason 0 (complete). UNVERIFIED: it is
     * written at the end also while the insulin is stopped.
     */
    private fun endTempBasal(at: Long) {
        state.tempBasalRunning = false
        record(DiaconnLog.TB_STOP, logSeconds(at)) { u16(state.tempBasalRateRatio).u8(REASON_COMPLETE).u32(state.tempBasalDttm.toLong() and 0xFFFFFFFFL) }
    }

    private fun tempBasalEnd(): Long = state.tempBasalStartedAt + state.tempBasalTime * TEMP_BASAL_STEP_MS

    /** U/h of the running temp basal at [at]: a rate, or a percent of the pattern. */
    private fun tempBasalRate(at: Long): Double {
        val ratio = state.tempBasalRateRatio
        return if (ratio >= PERCENT_RATIO_MIN) patternRate(at) * (ratio - PERCENT_RATIO_MIN) / 100.0 else (ratio - ABSOLUTE_RATIO_MIN) / 100.0
    }

    // --- Stop on the pump ---

    /**
     * The insulin stops or starts again. Real G8 (2026-11-11): `43 55 01` (battery 85, pattern 1). A temp basal that runs
     * gets no stop record and runs again after the start, with the end it had; its elapsed minutes count the stop.
     * UNVERIFIED: a bolus or an extended bolus that runs goes on.
     */
    private fun setSuspended(suspended: Boolean, at: Long) {
        if (state.suspended == suspended) return
        state.suspended = suspended
        record(if (suspended) DiaconnLog.SUSPEND else DiaconnLog.SUSPEND_RELEASE, logSeconds(at)) { u8(state.battery).u8(state.basalPattern) }
    }

    // --- Time ---

    /**
     * Brings the pump up to [now]: the basal given, the temp basal and the extended bolus whose time is over (with their
     * records at their end), the hourly and daily records, and the running bolus.
     */
    private fun settle(now: Long) {
        carryOut(delayed.filter { it.dueAt <= now })
        val from = if (state.lastSettleAt == 0L) now else maxOf(state.lastSettleAt, now - MAX_SETTLE_MS)
        runTime(from, maxOf(from, now))
        state.lastSettleAt = maxOf(state.lastSettleAt, now)
        settleBolus(now)
    }

    /** In parts in which nothing changes: up to the next hour, the end of the temp basal or of the extended bolus. */
    private fun runTime(from: Long, to: Long) {
        var t = from
        while (true) {
            if (state.tempBasalRunning && tempBasalEnd() <= t) endTempBasal(tempBasalEnd())
            state.extendedBolus?.let { if (it.endsAt <= t) finishExtendedBolus(it) }
            if (t >= to) return
            val hourEnd = nextHourStart(t)
            val tempBasalEnd = if (state.tempBasalRunning) tempBasalEnd() else Long.MAX_VALUE
            val end = minOf(to, hourEnd, tempBasalEnd, state.extendedBolus?.endsAt ?: Long.MAX_VALUE)
            giveBasal(t, end)
            t = end
            if (t == hourEnd) hourEnded(hourEnd)
        }
    }

    /** The basal from [from] to [to], at the rate of [from]: the rate does not change inside such a part. */
    private fun giveBasal(from: Long, to: Long) {
        if (to <= from) return
        val hours = (to - from) / MS_PER_HOUR
        val given = basalRateAt(from) * hours
        state.hourBasalBefore += if (state.suspended) 0.0 else patternRate(from) * hours
        state.hourBasalAfter += given
        state.todayBasal += given
        val extended = state.extendedBolus?.let { if (from >= it.startedAt) it.amountHundredths / 100.0 * (to - from) / (it.minutes * MS_PER_MINUTE) else 0.0 } ?: 0.0
        takeInsulin(given + extended)
    }

    /**
     * The hour that ended at [at]: the hourly basal record, and at midnight the daily records. UNVERIFIED: their layout
     * values and their time (here the last second of the hour or the day they count).
     */
    private fun hourEnded(at: Long) {
        val seconds = logSeconds(at) - 1
        record(DiaconnLog.HOUR_BASAL, seconds) {
            u16(hundredths(state.hourBasalBefore)).u16(hundredths(state.hourBasalAfter)).u8(state.battery).u16(hundredths(state.reservoir))
        }
        state.hourBasalBefore = 0.0
        state.hourBasalAfter = 0.0
        if (pumpLocal(at).hour == 0) {
            record(DiaconnLog.DAY_BASAL, seconds) { u16(hundredths(state.todayBasal)).u8(state.battery) }
            record(DiaconnLog.DAY, seconds) { u16(0).u16(hundredths(state.todayBolus)).u8(state.battery) }
            state.todayBasal = 0.0
            state.todayBolus = 0.0
        }
    }

    private fun basalRateAt(at: Long): Double = when {
        state.suspended                                     -> 0.0
        state.tempBasalRunning && at < tempBasalEnd()       -> tempBasalRate(at)
        else                                                -> patternRate(at)
    }

    private fun patternRate(at: Long): Double = state.basalRates[pumpLocal(at).hour]

    /** The wall clock of the pump at phone time [at], in whole seconds. */
    private fun pumpLocal(at: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(Math.floorDiv(at + state.clockOffsetMs, 1000L) * 1000L), ZoneId.systemDefault())

    private fun nextHourStart(at: Long): Long {
        val zone = ZoneId.systemDefault()
        val next = LocalDateTime.ofInstant(Instant.ofEpochMilli(at + state.clockOffsetMs), zone).truncatedTo(ChronoUnit.HOURS).plusHours(1)
        return maxOf(next.atZone(zone).toInstant().toEpochMilli() - state.clockOffsetMs, at + 1)
    }

    /**
     * The time of a log record at phone time [at], see [DiaconnLog]. UNVERIFIED: a clock that was never set counts the
     * seconds since the power-on from 2025-03-19.
     */
    private fun logSeconds(at: Long): Long =
        if (state.clockNeverSet) NEVER_SET_DATE.toEpochSecond(ZoneOffset.UTC) + (at - state.poweredOnAt).coerceAtLeast(0) / 1000
        else pumpLocal(at).toEpochSecond(ZoneOffset.UTC)

    // --- The log ---

    private fun record(kind: Int, seconds: Long, fields: PacketData.() -> Unit) {
        val number = state.nextLogNumber
        state.log.removeAll { it.number == number }
        state.log.add(LogRecord(number, state.wrappingCount, DiaconnLog.record(seconds, kind, fields)))
        if (number >= LOG_NUMBERS - 1) {
            state.nextLogNumber = 0
            state.wrappingCount = (state.wrappingCount + 1) and 0xFF
        } else state.nextLogNumber = number + 1
    }

    // --- Hooks for tests, see DiaconnEmulatorBleTransport ---

    /** The next [command] never reaches the pump. */
    fun loseCommand(command: EmulatedCommand): Boolean = synchronized(state) { lostCommands.add(command) }

    /** The pump does the next [command] and its answer is lost. For a setting, the answer to its confirm is lost. */
    fun loseAnswer(command: EmulatedCommand): Boolean = synchronized(state) { lostAnswers.add(command) }

    /** Only the first fault can fire, so faults fire in the order the test set them. */
    private fun take(faults: MutableList<EmulatedCommand>, command: EmulatedCommand?): Boolean =
        command != null && faults.firstOrNull() == command && faults.removeAt(0) == command

    /** The next bolus stops after [units], as at an occlusion: a fail record and a result report with what it gave. */
    fun stopNextBolusAt(units: Double): Boolean = atNow {
        state.stopNextBolusAt = units
        true
    }

    /** The pump clock goes [offsetMs] further off the phone clock. */
    fun shiftClock(offsetMs: Long): Boolean = atNow {
        state.clockOffsetMs += offsetMs
        true
    }

    /**
     * A bolus given with the buttons of the pump, without a link. UNVERIFIED: it is given at once and logged as a bolus
     * from the app is, and the pump refuses it while a bolus runs or the insulin is stopped.
     */
    fun bolusOnPump(units: Double): Boolean = atNow { now ->
        val amount = hundredths(units)
        if (state.runningBolus != null || state.suspended || amount <= 0 || amount > hundredths(state.reservoir)) return@atNow false
        takeInsulin(amount / 100.0)
        val minutes = (amount.toLong() * MS_PER_MINUTE / state.bolusSpeed / 100 / MS_PER_MINUTE).toInt()
        val seconds = logSeconds(now)
        record(DiaconnLog.NORMAL_SUCCESS, seconds) { u16(amount).u16(amount).u8(minutes).u8(state.battery) }
        bolusGiven(amount, BOLUS_KIND_SNACK, seconds)
        state.deliveredBoluses.add(amount / 100.0)
        true
    }

    /** The user stops ([suspended] true) or starts the insulin with the buttons of the pump. */
    fun suspendOnPump(suspended: Boolean): Boolean = atNow { now ->
        setSuspended(suspended, now)
        true
    }

    /** A new reservoir with [units]. UNVERIFIED: logged as an injector change, with a prime of 0. */
    fun refill(units: Double) = atNow { now ->
        state.reservoir = units
        record(DiaconnLog.CHANGE_INJECTOR, logSeconds(now)) { u16(0).u16(hundredths(units)).u8(state.battery) }
    }

    /** A new tube, primed with [primeUnits]. */
    fun changeTube(primeUnits: Double) = atNow { now ->
        takeInsulin(primeUnits)
        record(DiaconnLog.CHANGE_TUBE, logSeconds(now)) { u16(hundredths(primeUnits)).u16(hundredths(state.reservoir)).u8(state.battery) }
    }

    /** A new needle, primed with [primeUnits]. */
    fun changeNeedle(primeUnits: Double) = atNow { now ->
        takeInsulin(primeUnits)
        record(DiaconnLog.CHANGE_NEEDLE, logSeconds(now)) { u16(hundredths(primeUnits)).u16(hundredths(state.reservoir)).u8(state.battery) }
    }

    /** A new battery: the pump resets with reason 3, which the driver takes as a battery change. UNVERIFIED: rcon1 and rcon2 are 0. */
    fun replaceBattery(percent: Int = 100) = atNow { now ->
        state.battery = percent
        record(DiaconnLog.RESET_SYS, logSeconds(now)) { u8(percent).u8(RESET_BATTERY_REPLACED).u16(0).u16(0) }
    }

    /**
     * The report for [alarm], with the alarm record in the log, or null for an alarm this pump does not send.
     * UNVERIFIED: the grade, process and values of each report, and that the pump logs the alarm when it raises it.
     */
    internal fun alarm(alarm: EmulatedAlarm): ByteArray? = atNow { now ->
        when (alarm) {
            EmulatedAlarm.LOW_RESERVOIR -> {
                val remain = state.reservoir.toInt().coerceIn(0, 100)
                record(DiaconnLog.ALARM_SHORTAGE, logSeconds(now)) { u8(GRADE_WARNING).u8(ALARM_OCCURRED).u8(remain).u8(state.battery) }
                report(INSULIN_LACK_REPORT) { u8(GRADE_WARNING).u8(PROCESS_SKIP).u8(remain) }
            }

            EmulatedAlarm.OCCLUSION     -> {
                val blockType = if (state.runningBolus != null) BLOCK_NORMAL_BOLUS else BLOCK_BASAL
                record(DiaconnLog.ALARM_BLOCK, logSeconds(now)) { u8(GRADE_MAJOR).u8(ALARM_OCCURRED).u16(0).u8(blockType).u8(state.battery) }
                report(INJECTION_BLOCK_REPORT) { u8(GRADE_MAJOR).u8(PROCESS_STOP).u16(0).u8(blockType) }
            }

            EmulatedAlarm.LOW_BATTERY   -> {
                record(DiaconnLog.ALARM_BATTERY, logSeconds(now)) { u8(GRADE_WARNING).u8(ALARM_OCCURRED).u8(state.battery) }
                report(BATTERY_WARNING_REPORT) { u8(GRADE_WARNING).u8(PROCESS_SKIP).u8(state.battery) }
            }

            // Never seen on a real G8, and the report for it is not known.
            EmulatedAlarm.EMPTY_RESERVOIR,
            EmulatedAlarm.STOPPED_TOO_LONG -> null
        }
    }

    /** What the pump holds now. @param linkUp a phone is connected, which only the transport knows */
    fun snapshot(linkUp: Boolean): EmulatedPumpState = atNow { now ->
        EmulatedPumpState(
            boluses = state.deliveredBoluses.toList(),
            bolusRunning = state.runningBolus != null,
            // While the insulin is stopped the temp basal is kept but not given.
            tempBasal = if (state.tempBasalRunning && !state.suspended)
                EmulatedTempBasal(absolute = (tempBasalRate(now) * 100).roundToInt() / 100.0, percent = null) else null,
            suspended = state.suspended,
            reservoir = state.reservoir,
            linkUp = linkUp,
            commands = state.commands,
            extendedBolus = state.extendedBolus?.let { EmulatedExtendedBolus(units = it.amountHundredths / 100.0, minutes = it.minutes) },
            battery = state.battery,
            basal = state.basalRates.toList()
        )
    }

    /** All the pump holds, with the bolus it gives, for a test that kills the app. Read back with [restore]. */
    fun saveState(): ByteArray = atNow {
        // A setting the pump carries out in a moment is carried out now: it is code, which is not saved.
        carryOut(delayed.toList())
        ByteArrayOutputStream().also { bytes -> ObjectOutputStream(bytes).use { it.writeObject(state) } }.toByteArray()
    }

    // --- Helpers ---

    /** Brings the pump up to now, runs [block] under the lock, then sends what the pump sent meanwhile. */
    private fun <T> atNow(block: (now: Long) -> T): T {
        val (result, reports) = synchronized(state) {
            val now = System.currentTimeMillis()
            settle(now)
            block(now) to drainOutbox()
        }
        reports.forEach { reportSink?.invoke(it) }
        return result
    }

    private fun drainOutbox(): List<ByteArray> = outbox.toList().also { outbox.clear() }

    private fun takeInsulin(units: Double) {
        if (units > 0) state.reservoir = (state.reservoir - units).coerceAtLeast(0.0)
    }

    private fun firmwareAtLeast(major: Int, minor: Int): Boolean =
        state.firmwareMajor > major || (state.firmwareMajor == major && state.firmwareMinor >= minor)

    private fun status(running: Boolean): Int = if (running) RUNNING else NOT_RUNNING
    private fun pauseStatus(): Int = if (state.suspended) PAUSE else RELEASE

    private fun inquiryAnswer(msgType: Int, seq: Int, big: Boolean = false, fields: PacketData.() -> Unit): ByteArray =
        DiaconnPacket.build(DiaconnPacket.answerType(msgType), seq, PacketData().u8(INQUIRY_OK).apply(fields).bytes(), big)

    private fun result(msgType: Int, seq: Int, code: Int): ByteArray =
        DiaconnPacket.build(DiaconnPacket.answerType(msgType), seq, PacketData().u8(code).bytes())

    /** A report, with a number of the pump's own, see [ownSeq]. */
    private fun report(msgType: Int, fields: PacketData.() -> Unit): ByteArray =
        DiaconnPacket.build(msgType, nextOwnSeq(), PacketData().apply(fields).bytes())

    private fun nextOwnSeq(): Int {
        ownSeq = (ownSeq + 1) and 0xFF
        return ownSeq
    }

    private fun log(message: String) = aapsLogger?.debug(LTag.PUMPEMULATOR, "Diaconn emulator: $message")

    private fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02x".format(it) }

    companion object {

        /** The emulated pump of a test run, kept outside the graph, see [PumpEmulators]. */
        val KEY = PumpEmulators.Key<DiaconnPumpEmulator>("diaconn")

        /** The pump of [saveState], in a new process. Its bolus goes on. */
        fun restore(saved: ByteArray, aapsLogger: AAPSLogger? = null): DiaconnPumpEmulator =
            DiaconnPumpEmulator(ObjectInputStream(ByteArrayInputStream(saved)).use { it.readObject() } as DiaconnPumpState, aapsLogger)

        /** The times of the pump: the bolus timer and the advertisements. One daemon thread for all. */
        internal val timer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "DiaconnEmulatorPumpTimer").apply { isDaemon = true }
        }

        /** In 0.01 U, rounded down as the pump fields are. */
        internal fun hundredths(units: Double): Int = (units * 100 + 1e-6).toInt()

        // Requests: settings.
        internal const val BASAL_PAUSE_SETTING = 0x03
        internal const val BOLUS_SPEED_SETTING = 0x05
        internal const val SNACK_BOLUS_SETTING = 0x07
        internal const val EXTENDED_BOLUS_SETTING = 0x08
        internal const val DUAL_BOLUS = 0x09
        internal const val TEMP_BASAL_SETTING = 0x0A
        internal const val BASAL_SETTING = 0x0B
        internal const val INJECTION_BASAL_SETTING = 0x0C
        internal const val SOUND_SETTING = 0x0D
        internal const val DISPLAY_TIMEOUT_SETTING = 0x0E
        internal const val TIME_SETTING = 0x0F
        internal const val LANGUAGE_SETTING = 0x20
        internal const val APP_CANCEL_SETTING = 0x29
        internal const val INJECTION_CANCEL_SETTING = 0x2B
        internal const val APP_CONFIRM_SETTING = 0x37

        // Requests: inquiries.
        internal const val BOLUS_SPEED_INQUIRE = 0x45
        internal const val SNACK_INQUIRE = 0x47
        internal const val TEMP_BASAL_INQUIRE = 0x4A
        internal const val SOUND_INQUIRE = 0x4D
        internal const val DISPLAY_TIME_INQUIRE = 0x4E
        internal const val TIME_INQUIRE = 0x4F
        internal const val SNACK_LIMIT_INQUIRE = 0x50
        internal const val BASAL_LIMIT_INQUIRE = 0x52
        internal const val BIG_APS_MAIN_INFO_INQUIRE = 0x54
        internal const val LOG_STATUS_INQUIRE = 0x56
        internal const val LANGUAGE_INQUIRE = 0x60
        internal const val SERIAL_NUM_INQUIRE = 0x6E
        internal const val BIG_LOG_INQUIRE = 0x72
        internal const val BIG_MAIN_INFO_INQUIRE = 0x73
        internal const val INCARNATION_INQUIRE = 0x7A

        // Reports. UNVERIFIED: 0xD8 for the insulin lack report. The driver gives both it and the injection block report
        // 0xD8, so it reads every 0xD8 as an injection block. Which one a real G8 sends as 0xD8 is not known.
        internal const val BATTERY_WARNING_REPORT = 0xD7
        internal const val INSULIN_LACK_REPORT = 0xD8
        internal const val INJECTION_BLOCK_REPORT = 0xD8
        internal const val SNACK_RESULT_REPORT = 0xE4

        /**
         * Real G8 (2026-11-11): `e8 .. 37` when it carries out a temp basal or an extended bolus it confirmed; 0x37 is the
         * confirm request. Once, for the start of an extended bolus, it sent 0x16 in place of 0x37.
         */
        internal const val CONFIRM_REPORT = 0xE8
        internal const val TEMP_BASAL_REPORT = 0xCA
        internal const val BASAL_SETTING_REPORT = 0xCB
        internal const val EXTENDED_RESULT_REPORT = 0xE5
        private const val EXTENDED_STOPPED = 1
        internal const val INJECTION_PROGRESS_REPORT = 0xEA

        // Result codes. Inquiries answer 16 when fine, settings 0.
        internal const val INQUIRY_OK = 16
        private const val INQUIRY_CRC_ERROR = 17
        internal const val INQUIRY_PROTOCOL_ERROR = 19
        internal const val OK = 0
        private const val CRC_ERROR = 1
        internal const val PARAMETER_ERROR = 2
        internal const val PROTOCOL_ERROR = 3
        internal const val BOLUS_RUNNING = 8
        internal const val BASAL_PAUSED = 9
        internal const val OTP_MISMATCH = 10
        internal const val LOW_INSULIN = 12
        internal const val ONE_TIME_LIMIT = 13
        internal const val DAILY_LIMIT = 14

        /** "After base setting is completed, base injection can be made": see `basalWritten`. */
        internal const val BASAL_NOT_SAVED = 15
        internal const val TEMP_BASAL_RUNNING = 35
        internal const val NO_TEMP_BASAL = 36

        /** The OTP is a 6-digit number, never 0: 0 means "no OTP" in the driver. */
        private const val MIN_OTP = 100_000
        private const val MAX_OTP = 999_999

        // Status bytes.
        private const val RUNNING = 1
        internal const val NOT_RUNNING = 2
        internal const val PAUSE = 1
        internal const val RELEASE = 2
        internal const val TEMP_BASAL_START = 1
        internal const val TEMP_BASAL_STOP = 2
        internal const val TEMP_BASAL_REPLACE = 3
        private const val BASIC_PATTERN = 1

        // Temp basal rate: 1000 + U/h x 100, or 50000 + percent.
        private const val ABSOLUTE_RATIO_MIN = 1000
        private const val ABSOLUTE_RATIO_MAX = 2500
        private const val PERCENT_RATIO_MIN = 50000
        private const val PERCENT_RATIO_MAX = 50200
        private const val TEMP_BASAL_STEP_MS = 15 * 60_000L

        // Log reasons and kinds.
        internal const val REASON_COMPLETE = 0
        internal const val REASON_OCCLUSION = 1
        internal const val REASON_USER_STOP = 4
        private const val RESET_BATTERY_REPLACED = 3
        private const val BOLUS_KIND_SNACK = 2
        private const val BOLUS_KIND_SQUARE = 3
        private const val LOG_NUMBERS = 10_000
        private const val MAX_LOG_RECORDS_PER_ANSWER = 11

        // Alarm values. UNVERIFIED, see alarm().
        private const val GRADE_WARNING = 2
        private const val GRADE_MAJOR = 3
        private const val PROCESS_SKIP = 1
        private const val PROCESS_STOP = 2
        private const val ALARM_OCCURRED = 1
        private const val BLOCK_BASAL = 1
        private const val BLOCK_NORMAL_BOLUS = 3

        // Bolus result report.
        internal const val BOLUS_RESULT_DONE = 0
        internal const val BOLUS_RESULT_STOPPED = 1
        internal const val BOLUS_RESULT_FAILED = 2

        // Times.
        private const val MS_PER_MINUTE = 60_000L
        private const val MS_PER_HOUR = 3_600_000.0
        private const val REAL_START_DELAY_MS = 5_800L
        private const val FAST_START_DELAY_MS = 300L
        private const val FAST_DIVISOR = 10
        private const val REAL_PROGRESS_MS = 1_000L
        private const val FAST_PROGRESS_MS = 200L

        /** The settings the pump carries out a moment after the answer to their confirm. */
        private val CARRIED_OUT_LATER = setOf(
            EmulatedCommand.TEMP_BASAL_START, EmulatedCommand.TEMP_BASAL_STOP,
            EmulatedCommand.EXTENDED_BOLUS_START, EmulatedCommand.EXTENDED_BOLUS_STOP
        )

        /**
         * How long after the answer to its confirm the pump carries out a temp basal or an extended bolus. Real G8
         * (2026-11-11): 1.8 and 1.9 s for a temp basal start, 1.2 s for its stop, 1.8 s for an extended bolus start, 1.9 s
         * for its stop.
         */
        private const val REAL_CARRY_OUT_MS = 1_800L

        /**
         * Not shorter than 1 s. A log record has the time of its confirm in whole seconds, and AAPS takes that time as the
         * id of the record. As the pump takes this long, it never confirms two changes of one kind in one second. With
         * 0.2 s, a zero temp and the high temp after it had one id, and AAPS kept the wrong one; an extended bolus stopped
         * in the second it started could not be ended in AAPS.
         */
        internal const val FAST_CARRY_OUT_MS = 1_000L

        /** How long after the confirm of a basal write the pump sends its basal setting report, see `basalWritten`. */
        private const val REAL_BASAL_REPORT_MS = 23_400L
        internal const val FAST_BASAL_REPORT_MS = 1_000L

        /**
         * How long after the confirm of a basal write the pump takes the start of the basal, see `basalWritten`.
         * UNVERIFIED: the real G8 refused it 23.6 s after the confirm and took it 30.5 s after; this is in between. At FAST
         * it comes after the report, so a driver that starts the basal on the report is refused, as on the real pump.
         */
        private const val REAL_BASAL_READY_MS = 27_000L
        internal const val FAST_BASAL_READY_MS = 2_000L

        /** Longer gaps are not counted, so a pump that slept for weeks does not write thousands of hourly records. */
        private const val MAX_SETTLE_MS = 7 * 24 * 3_600_000L

        /** See [DiaconnPumpState.clockNeverSet]. */
        private val NEVER_SET_DATE: LocalDateTime = LocalDateTime.of(2025, 3, 19, 0, 0)
    }
}
