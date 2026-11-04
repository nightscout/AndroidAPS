package app.aaps.pump.aaruy.emulator

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil
import kotlin.math.min

/**
 * The pump, without hardware: takes frames and answers with frames. [tick] moves time: basal and bolus
 * delivery, temp basal end, and the messages the pump sends by itself.
 *
 * Frame layouts come from the vendor AAR, so they are known. How the firmware behaves is not in the AAR.
 * It is inferred from what the AAR and the driver expect, and each such place says UNVERIFIED. Check those
 * against a capture from a real pump.
 *
 * @param keyProvider the AES key. It is read from the AAR, so it is not stored in this source.
 * @param clock time in ms. Tests give their own.
 */
class AaruyPumpEmulator(
    private val keyProvider: () -> ByteArray,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private class Bolus(
        val startedAt: Long,
        val now: Int,
        val delayed: Int,
        val delayMinutes: Int,
        val resumeStatus: Int,
        var delivered: Int = 0,
        var lastStepAt: Long = startedAt,
        var delayedDelivered: Int = 0
    ) {

        val isExtended: Boolean get() = now == 0
    }

    private class TempBasal(val startedAt: Long, val rate: Int, val minutes: Int)

    private class History(val time: Int, val type: Int, val data: Int, val injectTime: Int = 0, val delayBolus: Int = 0)

    /** Running status. Same numbers as `AaruyPumpState`. */
    var runningStatus = STATUS_BASAL
        private set

    /** Insulin left, in units. */
    var reservoir = DEFAULT_RESERVOIR
    var battery = DEFAULT_BATTERY
    var closedMode = 0
        private set

    /** The frames the emulator did not accept because of the sequence number. */
    var rejectedFrames = 0
        private set

    private var lastSequence: Short = 0
    private var basicParam = defaultBasicParam()
    private var basalSetting: ByteArray? = null
    private var schedule = listOf(0 to DEFAULT_BASAL)
    private var bolusSetting: BolusSetting? = null
    private var bolus: Bolus? = null
    private var tempBasal: TempBasal? = null
    private var transitionEndsAt = 0L
    private var transitionNext = STATUS_NOT_RUNNING
    private var transitionMessage: ByteArray? = null
    private var lastTickAt = clock()
    private var lowReservoirSent = false
    private var noReservoirSent = false
    private val history = ArrayList<History>()

    private class BolusSetting(val total: Int, val now: Int, val delayMinutes: Int)

    /** Handles one frame from the app. @return what the pump sends back, in order. */
    fun handle(frame: ByteArray): List<ByteArray> {
        val plain = try {
            cipher(Cipher.DECRYPT_MODE).doFinal(frame.copyOfRange(OUTER_HEADER, frame.size))
        } catch (_: Exception) {
            // UNVERIFIED: a frame that does not decrypt gets no answer.
            return emptyList()
        }
        val reader = reader(plain)
        reader.short
        val command = reader.get().toInt()
        val paramId = reader.short
        val sequence = reader.short
        val payload = plain.copyOfRange(INNER_HEADER, plain.size)

        // UNVERIFIED: the AAR reads the serial (21) after error 0x801C, so that error looks like a sequence error.
        if (paramId != PARAM_REPORT_DEVICE && paramId != PARAM_SERIAL) {
            if (sequence != (lastSequence + 1).toShort()) {
                rejectedFrames++
                return listOf(message(PARAM_ERROR, errorPayload(ERROR_TYPE_SEQUENCE, ERROR_OPERATION)))
            }
            lastSequence = sequence
        }
        return if (command == COMMAND_READ) handleRead(paramId) else handleWrite(paramId, payload)
    }

    private fun handleRead(paramId: Short): List<ByteArray> = when (paramId) {
        PARAM_BASIC        -> listOf(message(PARAM_BASIC, basicParam))
        PARAM_BASAL        -> listOfNotNull(basalSetting?.let { message(PARAM_BASAL, it) })
        PARAM_STATUS       -> listOf(statusMessage())
        PARAM_REAL_DATA    -> listOf(message(PARAM_REAL_DATA, byteArrayOf(battery.toByte(), BLE_SIGNAL)))
        PARAM_SERIAL       -> listOf(message(PARAM_SERIAL, buffer(2).putShort(lastSequence).array()))
        else               -> emptyList()
    }

    private fun handleWrite(paramId: Short, payload: ByteArray): List<ByteArray> = when (paramId) {
        PARAM_REPORT_DEVICE -> handleReportDevice()
        PARAM_BASIC         -> {
            basicParam = payload.copyOf()
            listOf(message(PARAM_BASIC, basicParam))
        }

        PARAM_BASAL         -> handleBasal(payload)
        PARAM_BOLUS_SETTING -> {
            val reader = reader(payload)
            reader.int
            val total = reader.short.unsigned()
            repeat(3) { reader.short }
            val now = reader.short.unsigned()
            bolusSetting = BolusSetting(total, now, reader.short.unsigned())
            emptyList()
        }

        PARAM_SYNC_TIME     -> emptyList()
        PARAM_BASAL_CONTROL -> handleBasalControl(payload)
        PARAM_TEMP_BASAL    -> handleTempBasal(payload)
        PARAM_BOLUS_CONTROL -> handleBolusControl(payload)
        PARAM_TOTAL_CONTROL -> handleTotalControl(payload)
        PARAM_HISTORY       -> handleHistory(payload)
        PARAM_CONFIRM       -> listOf(message(PARAM_CONFIRM, byteArrayOf(payload.firstOrNull() ?: 0)))
        PARAM_CLOSED_MODE   -> {
            closedMode = payload.firstOrNull()?.toInt() ?: 0
            listOf(message(PARAM_CLOSED_MODE, byteArrayOf(closedMode.toByte())))
        }

        else                -> emptyList()
    }

    /** UNVERIFIED: 6 means accepted, as the AAR treats any other value as "unbind". If the pump refuses a second phone is not known, so it always accepts. */
    private fun handleReportDevice(): List<ByteArray> {
        val answer = buffer(8).putInt(pumpTime()).put(REPORT_ACCEPTED).put(PROTOCOL_VERSION).putShort(lastSequence).array()
        return listOf(message(PARAM_REPORT_DEVICE, answer))
    }

    /** UNVERIFIED: the pump echoes the setting. The driver waits 500 ms for a reply before it calls the write failed. */
    private fun handleBasal(payload: ByteArray): List<ByteArray> {
        basalSetting = payload.copyOf()
        val areas = ArrayList<Pair<Int, Int>>()
        var position = BASAL_AREAS_OFFSET
        while (position + BASAL_AREA_SIZE <= payload.size) {
            val reader = reader(payload.copyOfRange(position, position + BASAL_AREA_SIZE))
            areas.add((reader.get().toInt() and 0xFF) to reader.short.unsigned())
            position += BASAL_AREA_SIZE
        }
        if (areas.isNotEmpty()) schedule = areas
        return listOf(message(PARAM_BASAL, payload))
    }

    private fun handleBasalControl(payload: ByteArray): List<ByteArray> {
        val action = payload.getOrNull(ACTION_OFFSET)?.toInt()
        if (action != ACTION_START || runningStatus != STATUS_NOT_RUNNING) return listOf(error(ALARM_RUN_BASAL))
        runningStatus = STATUS_BASAL
        addHistory(HISTORY_BASAL, currentBasal())
        return listOf(statusMessage())
    }

    private fun handleTempBasal(payload: ByteArray): List<ByteArray> {
        val reader = reader(payload)
        reader.int
        val action = reader.get().toInt()
        val rate = reader.short.unsigned()
        val minutes = reader.short.unsigned()
        if (action == ACTION_START) {
            if (runningStatus != STATUS_BASAL) return listOf(error(ALARM_RUN_TEMP_BASAL))
            tempBasal = TempBasal(clock(), rate, minutes)
            runningStatus = STATUS_TEMP_BASAL
            addHistory(HISTORY_TEMP_BASAL, rate, injectTime = minutes)
        } else {
            if (runningStatus != STATUS_TEMP_BASAL) return emptyList()
            tempBasal = null
            runningStatus = STATUS_BASAL
        }
        return listOf(statusMessage())
    }

    private fun handleBolusControl(payload: ByteArray): List<ByteArray> {
        val action = payload.getOrNull(ACTION_OFFSET)?.toInt()
        if (action == ACTION_START) {
            val setting = bolusSetting
            if (setting == null || (runningStatus != STATUS_BASAL && runningStatus != STATUS_TEMP_BASAL)) return listOf(error(ALARM_RUN_BOLUS))
            bolus = Bolus(clock(), setting.now, setting.total - setting.now, setting.delayMinutes, runningStatus)
            bolusSetting = null
            runningStatus = STATUS_BOLUS
            return listOf(statusMessage())
        }
        // UNVERIFIED: a stopped bolus leaves the pump paused. The driver waits for status 7 and then asks to continue.
        val running = bolus ?: return emptyList()
        addBolusHistory(running)
        bolus = null
        runningStatus = STATUS_PAUSED
        addHistory(HISTORY_PAUSE, 0)
        return listOf(statusMessage())
    }

    private fun handleTotalControl(payload: ByteArray): List<ByteArray> {
        return when (payload.getOrNull(ACTION_OFFSET)?.toInt()) {
            CONTROL_CONTINUE      -> {
                if (runningStatus != STATUS_PAUSED && runningStatus != STATUS_NOT_RUNNING) return emptyList()
                runningStatus = STATUS_BASAL
                listOf(statusMessage())
            }

            CONTROL_REPLACE       -> startTransition(STATUS_REWIND, STATUS_STOPPED, HISTORY_REWIND, errorPayload(EVENT_REWIND_DONE, 0))

            CONTROL_PUSH_AIR      -> startTransition(STATUS_DRAIN, STATUS_NOT_RUNNING, null, errorPayload(EVENT_LIQUID_SHOW, 0))

            CONTROL_PUSH_POSITION -> {
                reservoir = FULL_RESERVOIR
                lowReservoirSent = false
                noReservoirSent = false
                startTransition(STATUS_POSITION, STATUS_NOT_RUNNING, HISTORY_POSITION, errorPayload(EVENT_POSITION_DONE, 0))
            }

            CONTROL_RESTORE       -> {
                if (runningStatus != STATUS_STOPPED) return emptyList()
                runningStatus = STATUS_NOT_RUNNING
                listOf(statusMessage())
            }

            else                  -> emptyList()
        }
    }

    /** UNVERIFIED: how long these take. The real motor is likely slower. */
    private fun startTransition(during: Int, next: Int, historyType: Int?, doneEvent: ByteArray): List<ByteArray> {
        bolus = null
        tempBasal = null
        runningStatus = during
        transitionNext = next
        transitionEndsAt = clock() + TRANSITION_MS
        transitionMessage = message(PARAM_ERROR, doneEvent)
        historyType?.let { addHistory(it, 0) }
        return listOf(statusMessage())
    }

    private fun handleHistory(payload: ByteArray): List<ByteArray> {
        val reader = reader(payload)
        val start = reader.int
        reader.get()
        return if (reader.get().toInt() == HISTORY_REQUEST_DAILY) dailyMessages(start) else logMessages(start)
    }

    /** UNVERIFIED: the page size. An empty answer has page 0 of 0, which the driver has its own branch for. */
    private fun logMessages(start: Int): List<ByteArray> {
        val items = history.filter { it.time.toLong() and 0xFFFFFFFFL > start.toLong() and 0xFFFFFFFFL }
        val pages = ceil(items.size / HISTORY_PAGE_SIZE.toDouble()).toInt()
        if (pages == 0) return listOf(message(PARAM_HISTORY_LOG, buffer(HISTORY_HEADER).putShort(0).put(0).put(0).put(0).array()))
        return items.chunked(HISTORY_PAGE_SIZE).mapIndexed { page, chunk ->
            val out = buffer(HISTORY_HEADER + chunk.size * HISTORY_ITEM_SIZE)
                .putShort(items.size.toShort()).put(pages.toByte()).put(page.toByte()).put(chunk.size.toByte())
            chunk.forEach {
                out.putInt(it.time).put(it.type.toByte()).putShort(it.data.toShort()).putShort(it.injectTime.toShort()).putShort(it.delayBolus.toShort()).put(0)
            }
            message(PARAM_HISTORY_LOG, out.array())
        }
    }

    /** UNVERIFIED: the list always has today, because the driver reads the last item without a check. */
    private fun dailyMessages(startDay: Int): List<ByteArray> {
        val today = (pumpTime().toLong() / DAY_SECONDS).toInt()
        val first = min(startDay, today)
        val days = (first..today).map { day ->
            val inDay = history.filter { (it.time.toLong() and 0xFFFFFFFFL) / DAY_SECONDS == day.toLong() && it.type == HISTORY_BOLUS }
            val bolusTenths = inDay.sumOf { it.data } / 100
            Triple(day, bolusTenths, bolusTenths)
        }
        val out = buffer(1 + days.size * 6).put(days.size.toByte())
        days.forEach { out.putShort(it.first.toShort()).putShort(it.second.toShort()).putShort(it.third.toShort()) }
        return listOf(message(PARAM_HISTORY_DAILY, out.array()))
    }

    /** Moves time forward. @return what the pump sends by itself. */
    fun tick(): List<ByteArray> {
        val now = clock()
        val elapsed = now - lastTickAt
        lastTickAt = now
        val out = ArrayList<ByteArray>()
        deliverBasal(elapsed)
        tempBasal?.let {
            if (runningStatus == STATUS_TEMP_BASAL && now >= it.startedAt + it.minutes * MINUTE_MS) {
                tempBasal = null
                runningStatus = STATUS_BASAL
                out.add(statusMessage())
            }
        }
        bolus?.let { out.addAll(deliverBolus(it, now)) }
        if (transitionEndsAt != 0L && now >= transitionEndsAt) {
            transitionEndsAt = 0L
            runningStatus = transitionNext
            transitionMessage?.let { out.add(it) }
            transitionMessage = null
            out.add(statusMessage())
        }
        out.addAll(checkReservoir())
        return out
    }

    private fun deliverBasal(elapsedMs: Long) {
        val rate = when (runningStatus) {
            STATUS_BASAL      -> currentBasal()
            STATUS_TEMP_BASAL -> tempBasal?.rate ?: 0
            STATUS_BOLUS      -> if (bolus?.resumeStatus == STATUS_TEMP_BASAL) tempBasal?.rate ?: 0 else currentBasal()
            else              -> 0
        }
        reservoir = (reservoir - rate / 1000.0 * elapsedMs / HOUR_MS).coerceAtLeast(0.0)
    }

    private fun deliverBolus(running: Bolus, now: Long): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        if (running.isExtended) {
            val end = running.startedAt + running.delayMinutes * MINUTE_MS
            running.delayedDelivered = (running.delayed * min(1.0, (now - running.startedAt).toDouble() / (end - running.startedAt).coerceAtLeast(1))).toInt()
            if (now >= end) out.addAll(finishBolus(running))
            return out
        }
        // UNVERIFIED: the speed. It is from the wait the driver computes: one step every 2 seconds.
        val stepUnits = bolusStepThousandths()
        while (running.delivered < running.now && now - running.lastStepAt >= BOLUS_STEP_MS) {
            running.lastStepAt += BOLUS_STEP_MS
            running.delivered = min(running.delivered + stepUnits, running.now)
            reservoir = (reservoir - stepUnits / 1000.0).coerceAtLeast(0.0)
            out.add(statusMessage())
        }
        if (running.delivered >= running.now) out.addAll(finishBolus(running))
        return out
    }

    private fun finishBolus(running: Bolus): List<ByteArray> {
        addBolusHistory(running)
        // UNVERIFIED: a status with state 2 says "done", and then the pump returns to basal. The driver reads state 2 as complete.
        val done = statusMessage(bolusStateOverride = BOLUS_STATE_DONE)
        bolus = null
        runningStatus = running.resumeStatus
        return listOf(done, statusMessage())
    }

    private fun addBolusHistory(running: Bolus) {
        val time = ((running.startedAt - BASE_TIME_MS) / 1000).toInt()
        history.add(
            History(
                time = time,
                type = HISTORY_BOLUS,
                data = if (running.isExtended) running.delayedDelivered else running.delivered,
                injectTime = if (running.isExtended) running.delayMinutes else 0,
                delayBolus = if (running.isExtended) running.delayed else 0
            )
        )
    }

    private fun checkReservoir(): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        val low = reader(basicParam).let {
            it.position(LOW_ALERT_OFFSET)
            it.short.unsigned()
        }
        if (reservoir <= low && !lowReservoirSent) {
            lowReservoirSent = true
            out.add(error(ALARM_LOW_RESERVOIR))
        }
        if (reservoir <= 0.0 && !noReservoirSent) {
            noReservoirSent = true
            bolus?.let { addBolusHistory(it) }
            bolus = null
            tempBasal = null
            if (runningStatus in STATUS_BASAL..STATUS_BOLUS) runningStatus = STATUS_PAUSED
            out.add(error(ALARM_NO_RESERVOIR))
            out.add(statusMessage())
        }
        return out
    }

    /** Raises a pump alarm from a test or a scenario. @param code an `AlarmState` value */
    fun raiseAlarm(code: Int): ByteArray {
        addHistory(HISTORY_ALARM, code)
        return error(code)
    }

    private fun addHistory(type: Int, data: Int, injectTime: Int = 0) {
        history.add(History(pumpTime(), type, data, injectTime))
    }

    private fun bolusStepThousandths(): Int {
        val step = basicParam.getOrNull(BOLUS_STEP_OFFSET)?.toInt() ?: 1
        return PRECISION_THOUSANDTHS * step.coerceAtLeast(1)
    }

    private fun currentBasal(): Int {
        val minutes = (pumpLocalMinutes() / 30)
        return schedule.lastOrNull { it.first <= minutes }?.second ?: schedule.first().second
    }

    private fun pumpLocalMinutes(): Int {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = clock()
        return calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
    }

    private fun statusMessage(bolusStateOverride: Int? = null): ByteArray {
        val out = buffer(STATUS_SIZE)
            .putInt(pumpTime())
            .putShort(reservoir.toInt().toShort())
            .put(runningStatus.toByte())
        val running = bolus
        when (runningStatus) {
            STATUS_BASAL      -> out.put(0).putShort(currentBasal().toShort()).putShort(0).putShort(0).put(0)

            STATUS_TEMP_BASAL -> {
                val temp = tempBasal
                val remain = if (temp == null) 0 else ((temp.startedAt + temp.minutes * MINUTE_MS - clock()) / MINUTE_MS).toInt().coerceAtLeast(0)
                out.put(0).putShort((temp?.rate ?: 0).toShort()).putShort(remain.toShort()).putShort(0).put(0)
            }

            STATUS_BOLUS      -> {
                val state = bolusStateOverride ?: if (running?.isExtended == true) BOLUS_STATE_EXTENDED else BOLUS_STATE_NOW
                val delivered = if (running?.isExtended == true) running.delayedDelivered else running?.delivered ?: 0
                out.put(state.toByte()).putShort(delivered.toShort()).putShort((running?.delayed ?: 0).toShort()).putShort(0).put(0)
            }

            STATUS_PAUSED, STATUS_STOPPED -> out.put(0).putInt(pumpTime())
        }
        return message(PARAM_STATUS, out.array())
    }

    private fun error(code: Int): ByteArray = message(PARAM_ERROR, errorPayload(code and 0xFF, code shr 8 and 0xFF))
    private fun errorPayload(type: Int, error: Int): ByteArray = byteArrayOf(type.toByte(), error.toByte())

    private fun message(paramId: Short, payload: ByteArray): ByteArray {
        val plain = buffer(INNER_HEADER + payload.size)
            .putShort((payload.size + INNER_AFTER_LENGTH).toShort())
            .put(COMMAND_WRITE.toByte())
            .putShort(paramId)
            .putShort(lastSequence)
            .put(payload)
            .array()
        val encrypted = cipher(Cipher.ENCRYPT_MODE).doFinal(plain)
        return buffer(OUTER_HEADER + encrypted.size).put(FRAME_START).putShort(encrypted.size.toShort()).put(encrypted).array()
    }

    private fun cipher(mode: Int): Cipher = Cipher.getInstance("AES/ECB/PKCS5Padding").apply { init(mode, SecretKeySpec(keyProvider(), "AES")) }
    private fun pumpTime(): Int = ((clock() - BASE_TIME_MS) / 1000).toInt()
    private fun buffer(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    private fun reader(data: ByteArray): ByteBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    private fun Short.unsigned(): Int = toInt() and 0xFFFF

    private fun defaultBasicParam(): ByteArray =
        buffer(BASIC_PARAM_SIZE)
            .putInt(0).put(1).putShort(25).putShort(2000).putShort(500).put(1)
            .putShort(10000.toShort()).put(1).putShort(2).put(1).putShort(0).putShort(20)
            .put(0).put(1).put(0).put(0).put(0).put(DEFAULT_PUMP_TYPE.toByte()).put(0).put(0)
            .array()

    companion object {

        const val STATUS_NOT_RUNNING = 0
        const val STATUS_BASAL = 1
        const val STATUS_TEMP_BASAL = 2
        const val STATUS_BOLUS = 3
        const val STATUS_DRAIN = 4
        const val STATUS_REWIND = 5
        const val STATUS_POSITION = 6
        const val STATUS_PAUSED = 7
        const val STATUS_STOPPED = 8

        const val ALARM_RUN_BASAL = 0x800C
        const val ALARM_RUN_TEMP_BASAL = 0x800D
        const val ALARM_RUN_BOLUS = 0x800E
        const val ALARM_LOW_RESERVOIR = 0x8103
        const val ALARM_NO_RESERVOIR = 0x8104

        const val TRANSITION_MS = 3000L
        const val BOLUS_STEP_MS = 2000L

        private const val PARAM_BASIC: Short = 0
        private const val PARAM_BASAL: Short = 1
        private const val PARAM_BOLUS_SETTING: Short = 4
        private const val PARAM_SYNC_TIME: Short = 5
        private const val PARAM_STATUS: Short = 6
        private const val PARAM_BASAL_CONTROL: Short = 7
        private const val PARAM_TEMP_BASAL: Short = 8
        private const val PARAM_BOLUS_CONTROL: Short = 9
        private const val PARAM_TOTAL_CONTROL: Short = 10
        private const val PARAM_ERROR: Short = 12
        private const val PARAM_HISTORY: Short = 13
        private const val PARAM_HISTORY_LOG: Short = 14
        private const val PARAM_REAL_DATA: Short = 15
        private const val PARAM_CONFIRM: Short = 16
        private const val PARAM_REPORT_DEVICE: Short = 17
        private const val PARAM_HISTORY_DAILY: Short = 20
        private const val PARAM_SERIAL: Short = 21
        private const val PARAM_CLOSED_MODE: Short = 24

        private const val COMMAND_READ = 0
        private const val COMMAND_WRITE = 1
        private const val FRAME_START: Byte = 0x55
        private const val OUTER_HEADER = 3
        private const val INNER_HEADER = 7
        private const val INNER_AFTER_LENGTH = 5

        private const val ACTION_OFFSET = 4
        private const val ACTION_START = 1
        private const val CONTROL_CONTINUE = 1
        private const val CONTROL_REPLACE = 3
        private const val CONTROL_PUSH_AIR = 4
        private const val CONTROL_PUSH_POSITION = 5
        private const val CONTROL_RESTORE = 6

        private const val REPORT_ACCEPTED: Byte = 6
        private const val PROTOCOL_VERSION: Byte = 1

        private const val ERROR_TYPE_SEQUENCE = 28
        private const val ERROR_OPERATION = 0x80
        private const val EVENT_POSITION_DONE = 16
        private const val EVENT_REWIND_DONE = 17
        private const val EVENT_LIQUID_SHOW = 25

        private const val BOLUS_STATE_NOW = 0
        private const val BOLUS_STATE_EXTENDED = 1
        private const val BOLUS_STATE_DONE = 2

        private const val HISTORY_BASAL = 0
        private const val HISTORY_TEMP_BASAL = 1
        private const val HISTORY_BOLUS = 2
        private const val HISTORY_ALARM = 3
        private const val HISTORY_REWIND = 4
        private const val HISTORY_POSITION = 5
        private const val HISTORY_PAUSE = 7
        private const val HISTORY_REQUEST_DAILY = 1
        private const val HISTORY_HEADER = 5
        private const val HISTORY_ITEM_SIZE = 12
        private const val HISTORY_PAGE_SIZE = 10

        private const val BASAL_AREAS_OFFSET = 12
        private const val BASAL_AREA_SIZE = 3
        private const val STATUS_SIZE = 15
        private const val BASIC_PARAM_SIZE = 32
        private const val BOLUS_STEP_OFFSET = 14
        private const val LOW_ALERT_OFFSET = 20
        private const val PRECISION_THOUSANDTHS = 25
        private const val DEFAULT_BASAL = 500
        private const val DEFAULT_PUMP_TYPE = 131
        private const val DEFAULT_RESERVOIR = 200.0
        private const val FULL_RESERVOIR = 300.0
        private const val DEFAULT_BATTERY = 90
        private const val BLE_SIGNAL: Byte = -50

        private const val BASE_TIME_MS = 1609430400000L
        private const val DAY_SECONDS = 86400L
        private const val MINUTE_MS = 60_000L
        private const val HOUR_MS = 3_600_000.0
    }
}
