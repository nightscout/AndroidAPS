package app.aaps.plugins.dexcomoneplus.session

import app.aaps.plugins.dexcomoneplus.OnePlusCalibrationOutcome
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import app.aaps.plugins.dexcomoneplus.OnePlusLog
import app.aaps.plugins.dexcomoneplus.OnePlusLogMarkers
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattClient
import app.aaps.plugins.dexcomoneplus.onePlusSleep
import app.aaps.plugins.dexcomoneplus.parse.OnePlusCalibrateRx
import app.aaps.plugins.dexcomoneplus.parse.OnePlusCalibrateTx
import app.aaps.plugins.dexcomoneplus.parse.OnePlusCalibrationBoundsRx
import app.aaps.plugins.dexcomoneplus.parse.OnePlusCalibrationMapper
import app.aaps.plugins.dexcomoneplus.parse.OnePlusCalibrationState
import app.aaps.plugins.dexcomoneplus.parse.OnePlusEGlucoseTx
import app.aaps.plugins.dexcomoneplus.parse.OnePlusGlucoseParser
import app.aaps.plugins.dexcomoneplus.parse.OnePlusSessionStartRx
import app.aaps.plugins.dexcomoneplus.parse.OnePlusSessionStartTx
import app.aaps.plugins.dexcomoneplus.parse.OnePlusSessionStopRx
import app.aaps.plugins.dexcomoneplus.parse.OnePlusTransmitterTimeRx
import app.aaps.plugins.dexcomoneplus.warmup.OnePlusWarmupClock
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/**
 * Post-KEKS Control / EGV path aligned with Juggluco `getdatacmd` + xDrip Ob1 `doGetData` (G7).
 *
 * Sequence: enable Control **INDICATE** (NOTIFY fallback) → [OnePlusEGlucoseTx] (`0x4e`) →
 * continuous EGV loop → optional BackFill (`0x59`) once dex time is known from EGV.
 * SessionStart (`0x26`) only reactively if EGV reports Stopped (never auto SessionStop).
 *
 * Do **not** write TransmitterTime (`0x24`) before/with the first `0x4e`: Juggluco never
 * sends `0x24` on ONE+/G7; Ob1 queues TimeTx *after* glucose. Dual/early `0x24` correlated
 * with peer GATT status=19 on Samsung.
 *
 * ⚠️ ASYNC IMPACT: Blocks the calling thread (intended: single bleExecutor).
 * [shouldContinue] must flip false and [OnePlusGattClient.disconnect] must run
 * (typically from [OnePlusBleSession.stop]) so [OnePlusGattClient.awaitControlNotify]
 * unblocks. Do not call dosing / UI work from this loop; only invoke callbacks.
 *
 * Provenance: Juggluco DexGattCallback.getdatacmd + Ob1G5StateMachine.doGetData
 * `1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f` (GPL-3.0).
 */
class OnePlusEgvSession(
    private val gatt: OnePlusGattClient,
    private val onWarmup: (OnePlusWarmupState) -> Unit = {},
    private val onGlucose: (OnePlusGlucoseSample) -> Unit = {},
    private val onError: (String, Boolean) -> Unit = { _, _ -> },
    /**
     * When true, allow SessionStartTx (0x26) if EGV reports Stopped / SensorStopped.
     * Never auto SessionStop. Dex time for Start comes from EGV session age when available.
     */
    private val requestNewSensorStart: Boolean = false,
    /**
     * Fingersticks waiting to be handed to the sensor's own algorithm, or null when this session may
     * not calibrate at all. A null queue here never builds opcode 0x34.
     */
    private val calibrationQueue: OnePlusCalibrationQueue? = null,
    /** What the sensor answered to a calibration (0x34 echo, or 0x35), so a refusal can reach the user. */
    private val onCalibrationResult: (OnePlusCalibrateRx?) -> Unit = {},
    /**
     * The sensor's calibration state (0x32) read right after, and whether it holds the value just
     * sent. That is how a lost answer is resolved without sending the value a second time.
     */
    private val onCalibrationState: (OnePlusCalibrationBoundsRx, Boolean) -> Unit = { _, _ -> },
    /**
     * The checks of [OnePlusCalibrationGate], run again right before a waiting value is written:
     * minutes or hours may have passed since the user confirmed it. Gets the value, its time and
     * now; returns why it must not go out, or null. The driver answers from its own warm-up phase
     * and last reading. The default refuses, so a session that was given a queue but no check
     * never writes.
     */
    private val calibrationSendCheck: (Int, Long, Long) -> OnePlusCalibrationGate.Refusal? = { _, _, _ ->
        OnePlusCalibrationGate.Refusal.NOT_READY
    },
    /** A waiting value was dropped before anything was written, and why. The sensor never saw it. */
    private val onCalibrationNotSent: (OnePlusCalibrationOutcome.NotSent.Reason) -> Unit = {},
) {

    @Volatile
    private var sessionStartAttempted = false

    /** Last transmitter time (seconds). Filled from EGV session age / TimeRx / SessionStartRx. */
    @Volatile
    private var lastDexTimeSeconds: Int = 0

    /**
     * Wall clock when [lastDexTimeSeconds] was read. The transmitter clock is only ever known as
     * "this value, at that moment", and a calibration carries the time of the BLOOD, so both ages
     * have to be taken off it.
     */
    @Volatile
    private var lastDexTimeAtMs: Long = 0L

    @Volatile
    private var backfillAttempted = false

    /** Fingerstick already reported as "waiting for the transmitter clock", so it is said once. */
    @Volatile
    private var waitingForDexTimeAtMs: Long = 0L

    /**
     * Continuous Control/EGV loop until [shouldContinue] is false or GATT drops.
     *
     * @param notifyTimeoutMs per-wait for Control indication (Ob1 uses ~10s)
     * @param rewriteIntervalMs re-issue EGlucoseTx after idle
     */
    fun run(
        shouldContinue: () -> Boolean,
        notifyTimeoutMs: Long = DEFAULT_NOTIFY_TIMEOUT_MS,
        rewriteIntervalMs: Long = DEFAULT_REWRITE_INTERVAL_MS,
    ) {
        if (!gatt.isConnected()) {
            OnePlusLog.e("${OnePlusLogMarkers.ERROR}: EGV GATT not connected")
            onError("ONEPLUS_EGV_NOT_CONNECTED", false)
            return
        }

        // Control is INDICATE on ONE+/G7. xDrip (same libkeks stack) does setupIndication(Control)
        // then writes 0x4E; we previously enabled NOTIFY → transmitter accepted the request but
        // could not deliver the EGV indication and terminated the link (peer status 19). Juggluco
        // uses NOTIFY, but that is its native stack — libkeks/xDrip is our reference here.
        gatt.enableControlIndications()
        // Ob1 speakSlowly() — give CCCD write time before Control write.
        if (!onePlusSleep(CCCD_SETTLE_MS)) return

        // Juggluco getdatacmd / Ob1 doGetData (G7): Control CCCD → 0x4E only.
        var lastWriteMs = 0L
        writeEgvRequest(preferShort = true)
        lastWriteMs = Clock.System.now().toEpochMilliseconds()

        var preferShort = true
        var consecutiveTimeouts = 0
        var triedIndicateFallback = false

        while (shouldContinue() && gatt.isConnected()) {
            val now = Clock.System.now().toEpochMilliseconds()
            // Between two Control exchanges is the only safe moment: the loop owns the
            // characteristic, and nothing else is waiting for an indication right now.
            if (sendPendingCalibration(shouldContinue)) {
                lastWriteMs = Clock.System.now().toEpochMilliseconds()
                continue
            }
            if (now - lastWriteMs >= rewriteIntervalMs) {
                writeEgvRequest(preferShort = preferShort)
                lastWriteMs = now
            }
            val packet = gatt.awaitControlNotify(notifyTimeoutMs)
            if (!shouldContinue()) break
            if (packet == null) {
                consecutiveTimeouts++
                OnePlusLog.d(
                    "${OnePlusLogMarkers.SESSION}: Control notify timeout ${notifyTimeoutMs}ms " +
                        "n=$consecutiveTimeouts — re-request EGV",
                )
                // After a few short-form misses, try CRC form once (Ob1 length 3).
                if (consecutiveTimeouts >= 2) preferShort = false
                // Silent NOTIFY accept but no traffic → Ob1 INDICATE.
                if (consecutiveTimeouts >= 2 && !triedIndicateFallback) {
                    triedIndicateFallback = true
                    try {
                        OnePlusLog.w(
                            "${OnePlusLogMarkers.SESSION}: Control quiet after INDICATE — try NOTIFY",
                        )
                        gatt.enableControlNotifications()
                        onePlusSleep(CCCD_SETTLE_MS)
                    } catch (t: Throwable) {
                        OnePlusLog.w(
                            "${OnePlusLogMarkers.SESSION}: Control INDICATE fallback failed: ${t.message}",
                        )
                    }
                }
                // After prolonged silence with new-sensor request: try SessionStart once (no 0x24).
                if (consecutiveTimeouts >= 3 &&
                    requestNewSensorStart &&
                    !sessionStartAttempted &&
                    gatt.isConnected()
                ) {
                    performSessionStart(reason = "egv_timeout_new_start", shouldContinue = shouldContinue)
                    writeEgvRequest(preferShort = preferShort)
                    lastWriteMs = Clock.System.now().toEpochMilliseconds()
                    continue
                }
                writeEgvRequest(preferShort = preferShort)
                lastWriteMs = Clock.System.now().toEpochMilliseconds()
                continue
            }
            consecutiveTimeouts = 0
            preferShort = true
            handleControlPacket(packet, shouldContinue)
        }
        OnePlusLog.i("${OnePlusLogMarkers.SESSION}: Control/EGV loop exit")
    }

    /**
     * Finite initial poll (useful for tests / one-shot). Prefer [run] after auth in production path.
     *
     * @return true if at least one Control EGV packet was seen or warm-up reported healthy.
     */
    fun runInitialPoll(maxRounds: Int = 6, stepTimeoutMs: Long = 20_000L): Boolean {
        if (!gatt.isConnected()) {
            OnePlusLog.e("${OnePlusLogMarkers.ERROR}: EGV GATT not connected")
            return false
        }
        // Control is INDICATE on ONE+/G7 (xDrip libkeks reference); NOTIFY made the peer drop.
        gatt.enableControlIndications()
        if (!onePlusSleep(CCCD_SETTLE_MS)) return false
        // Juggluco/xDrip GET_DATA: 0x4E first (same as [run]).
        writeEgvRequest(preferShort = true)
        var gotPacket = false
        var deliveredGlucose = false
        for (round in 0 until maxRounds) {
            val preferShort = round % 2 == 0
            if (round > 0) writeEgvRequest(preferShort = preferShort)
            val packet = gatt.awaitControlNotify(stepTimeoutMs) ?: continue
            gotPacket = true
            if (OnePlusSessionStartRx.parse(packet) != null) continue
            if (OnePlusSessionStopRx.parse(packet) != null) continue
            if (OnePlusTransmitterTimeRx.parse(packet) != null) continue
            val parsed = OnePlusGlucoseParser.parseControlPacket(packet) ?: continue
            val warmup = OnePlusCalibrationMapper.toWarmupState(
                state = parsed.calibration,
                sessionAgeSeconds = parsed.sessionAgeSeconds,
            )
            onWarmup(warmup)
            val sample = parsed.sample
            if (parsed.usable && sample != null) {
                onGlucose(sample)
                deliveredGlucose = true
                break
            }
            if (warmup.phase == OnePlusWarmupState.Phase.WARMING) return true
            if (warmup.phase == OnePlusWarmupState.Phase.FAILED) return false
        }
        return deliveredGlucose || gotPacket
    }

    /**
     * Hand the waiting fingerstick to the sensor, and wait for what it says about it.
     *
     * Returns true when a calibration was actually written, so the caller re-arms its own EGV
     * request: the exchange has used the Control characteristic in the middle of the cycle.
     *
     * The checks of [OnePlusCalibrationGate] run again here ([calibrationSendCheck]): the sensor may
     * have gone back to warm-up, the last reading may be old, or glucose may now change fast. A
     * value that fails them, or is too old or out of range, is dropped before any write and
     * reported as [OnePlusCalibrationOutcome.NotSent]: it is never kept for later, because an old
     * value sent to a sensor that lines it up with its own history is worse than no calibration.
     * Only a value that was written can end as "unknown".
     */
    private fun sendPendingCalibration(shouldContinue: () -> Boolean): Boolean {
        val queue = calibrationQueue ?: return false
        val waiting = queue.peek() ?: return false
        val now = Clock.System.now().toEpochMilliseconds()
        if (dropIfNotAllowed(queue, waiting, now)) return false
        val dexTime = OnePlusCalibrationQueue.dexTimeForBlood(
            bloodAtMs = waiting.bloodAtMs,
            nowMs = now,
            lastDexTimeSeconds = lastDexTimeSeconds,
            lastDexTimeAtMs = lastDexTimeAtMs,
        )
        if (dexTime == null) {
            if (waiting.bloodAtMs != waitingForDexTimeAtMs) {
                waitingForDexTimeAtMs = waiting.bloodAtMs
                OnePlusLog.i("${OnePlusLogMarkers.SESSION}: calibration waiting — transmitter time unknown")
            }
            return false
        }
        // Taken only if it is still the value that was checked: a sensor change may have cleared it.
        if (!queue.take(waiting)) return false
        val pending = waiting
        val packet = OnePlusCalibrateTx.build(pending.glucoseMgdl, dexTime)
        if (packet == null) {
            reportNotSent(OnePlusCalibrationOutcome.NotSent.Reason.OUT_OF_RANGE)
            return false
        }
        try {
            gatt.writeControl(packet)
            OnePlusLog.i(
                "${OnePlusLogMarkers.SESSION}: wrote CalibrateTx opcode=0x34 " +
                    "glucose=${pending.glucoseMgdl} dexTime=$dexTime",
            )
        } catch (t: Throwable) {
            // The write may have reached the sensor before it failed, so this is "unknown", not "not sent".
            OnePlusLog.e("${OnePlusLogMarkers.ERROR}: calibration write failed: ${t.message}", t)
            onCalibrationResult(null)
            return false
        }
        awaitCalibrationReply(shouldContinue)
        readCalibrationState(pending.glucoseMgdl, dexTime, shouldContinue)
        return true
    }

    /** Why [pending] must not be written now, or null when it may. */
    private fun notAllowed(pending: OnePlusCalibrationQueue.Pending, nowMs: Long): OnePlusCalibrationOutcome.NotSent.Reason? {
        if (OnePlusCalibrationQueue.isTooOld(pending.bloodAtMs, nowMs)) return OnePlusCalibrationOutcome.NotSent.Reason.TOO_OLD
        val refusal = calibrationSendCheck(pending.glucoseMgdl, pending.bloodAtMs, nowMs) ?: return null
        return OnePlusCalibrationOutcome.NotSent.Reason.of(refusal)
    }

    /** Takes [waiting] out of the queue and reports it as not sent when it must not go out now. */
    private fun dropIfNotAllowed(queue: OnePlusCalibrationQueue, waiting: OnePlusCalibrationQueue.Pending, nowMs: Long): Boolean {
        val reason = notAllowed(waiting, nowMs) ?: return false
        if (queue.take(waiting)) reportNotSent(reason)
        return true
    }

    private fun reportNotSent(reason: OnePlusCalibrationOutcome.NotSent.Reason) {
        OnePlusLog.w("${OnePlusLogMarkers.SESSION}: calibration dropped before the write — $reason")
        onCalibrationNotSent(reason)
    }

    /**
     * Ask the sensor for its calibration state (0x32) and report it.
     *
     * Never sends the value again, whatever comes back: a sensor keeps every calibration it takes,
     * so a second copy cannot be undone. A sensor that does not answer changes nothing.
     */
    private fun readCalibrationState(sentGlucoseMgdl: Int, sentDexTimeSeconds: Int, shouldContinue: () -> Boolean) {
        if (!shouldContinue() || !gatt.isConnected()) return
        try {
            gatt.writeControl(OnePlusCalibrationBoundsRx.request())
        } catch (t: Throwable) {
            OnePlusLog.w("${OnePlusLogMarkers.SESSION}: calibration state request failed: ${t.message}")
            return
        }
        for (attempt in 0 until CALIBRATION_REPLY_ROUNDS) {
            if (!shouldContinue() || !gatt.isConnected()) return
            val packet = gatt.awaitControlNotify(CALIBRATION_REPLY_TIMEOUT_MS) ?: break
            val bounds = OnePlusCalibrationBoundsRx.parse(packet)
            if (bounds == null) {
                handleControlPacket(packet, shouldContinue)
                continue
            }
            val matches = bounds.matches(sentGlucoseMgdl, sentDexTimeSeconds)
            OnePlusLog.i(
                "${OnePlusLogMarkers.SESSION}: calibration state last=${bounds.lastGlucoseMgdl} " +
                    "at=${bounds.lastCalibrationTimeSeconds} processing=${bounds.processing} " +
                    "permitted=${bounds.calibrationsPermitted} matchesSent=$matches",
            )
            onCalibrationState(bounds, matches)
            return
        }
        OnePlusLog.i("${OnePlusLogMarkers.SESSION}: no calibration state answer")
    }

    /**
     * Wait for the answer (0x34 echo, or 0x35), letting anything else through on the way.
     *
     * The sensor may well send a glucose packet before it answers, and dropping it here would lose a
     * reading, so every other packet keeps going through the normal handling.
     */
    private fun awaitCalibrationReply(shouldContinue: () -> Boolean) {
        for (attempt in 0 until CALIBRATION_REPLY_ROUNDS) {
            if (!shouldContinue() || !gatt.isConnected()) break
            val reply = gatt.awaitControlNotify(CALIBRATION_REPLY_TIMEOUT_MS) ?: break
            val parsed = OnePlusCalibrateRx.parse(reply)
            if (parsed == null) {
                handleControlPacket(reply, shouldContinue)
                continue
            }
            OnePlusLog.i(
                "${OnePlusLogMarkers.SESSION}: CalibrateRx form=${parsed.form} result=0x${parsed.result.toString(16)} " +
                    "accepted=${parsed.accepted()} msg=${parsed.message()}",
            )
            onCalibrationResult(parsed)
            return
        }
        OnePlusLog.w("${OnePlusLogMarkers.SESSION}: no CalibrateRx — the sensor did not answer the calibration")
        onCalibrationResult(null)
    }

    private fun applyTransmitterTime(rx: OnePlusTransmitterTimeRx) {
        lastDexTimeSeconds = rx.currentTimeSeconds
        lastDexTimeAtMs = Clock.System.now().toEpochMilliseconds()
        val age = rx.sessionAgeSeconds()
        OnePlusLog.d(
            "${OnePlusLogMarkers.SESSION}: TransmitterTimeRx current=${rx.currentTimeSeconds} " +
                "sessionStart=${rx.sessionStartTimeSeconds} inProgress=${rx.sessionInProgress()} " +
                "ageSec=$age status=0x${rx.statusByte.toString(16)}",
        )
        if (rx.sessionInProgress()) {
            val startMs = rx.realSessionStartEpochMs()
            if (startMs != null) {
                val warmup = OnePlusWarmupClock.warmingFromStart(
                    startEpochMs = startMs,
                    nowMs = Clock.System.now().toEpochMilliseconds(),
                    message = "tx_time_session",
                )
                onWarmup(warmup)
            }
        }
    }

    private fun performSessionStart(reason: String, shouldContinue: () -> Boolean) {
        if (sessionStartAttempted) return
        sessionStartAttempted = true
        if (!gatt.isConnected() || !shouldContinue()) return
        // A new sensor session starts with a warm-up: a value confirmed before it does not belong to it.
        if (calibrationQueue?.take() != null) reportNotSent(OnePlusCalibrationOutcome.NotSent.Reason.SENSOR_CHANGED)

        val payload = OnePlusSessionStartTx.build(
            dexTimeSeconds = lastDexTimeSeconds,
            startTimeEpochMs = Clock.System.now().toEpochMilliseconds(),
        )
        try {
            gatt.writeControl(payload)
            OnePlusLog.i(
                "${OnePlusLogMarkers.SESSION}: wrote SessionStartTx opcode=0x26 " +
                    "dexTime=$lastDexTimeSeconds reason=$reason",
            )
        } catch (t: Throwable) {
            val msg = t.message ?: "ONEPLUS_SESSION_START_WRITE_FAILED"
            OnePlusLog.e("${OnePlusLogMarkers.ERROR}: $msg", t)
            onError(msg, false)
            return
        }

        val packet = gatt.awaitControlNotify(SESSION_START_TIMEOUT_MS)
        if (packet == null) {
            OnePlusLog.w(
                "${OnePlusLogMarkers.SESSION}: SessionStartRx timeout — continuing EGV",
            )
            return
        }

        val rx = OnePlusSessionStartRx.parse(packet)
        if (rx == null) {
            // Could be an EGV that arrived first — process it and continue.
            OnePlusLog.i(
                "${OnePlusLogMarkers.SESSION}: SessionStart await got non-0x27; handling as Control",
            )
            handleControlPacket(packet, shouldContinue)
            return
        }

        if (rx.transmitterTime != 0 && rx.transmitterTime != OnePlusSessionStartRx.INVALID_TIME) {
            lastDexTimeSeconds = rx.transmitterTime
            lastDexTimeAtMs = Clock.System.now().toEpochMilliseconds()
        }

        when {
            rx.isOkay() -> OnePlusLog.i(
                "${OnePlusLogMarkers.SESSION}: SessionStartRx OK info=${rx.info} msg=${rx.message()} " +
                    "sessionStart=${rx.sessionStartTime} txTime=${rx.transmitterTime}",
            )
            rx.isAlreadyStarted() -> OnePlusLog.i(
                "${OnePlusLogMarkers.SESSION}: SessionStartRx already started — continuing EGV",
            )
            rx.isFubar() -> {
                OnePlusLog.e(
                    "${OnePlusLogMarkers.ERROR}: SessionStartRx fubar ${rx.message()}",
                )
                onError("ONEPLUS_SESSION_START_FUBAR: ${rx.message()}", false)
            }
            else -> OnePlusLog.w(
                "${OnePlusLogMarkers.SESSION}: SessionStartRx status=${rx.status} info=${rx.info} " +
                    "msg=${rx.message()} — continuing EGV",
            )
        }
    }

    private fun performBackfill(shouldContinue: () -> Boolean) {
        if (!shouldContinue() || !gatt.isConnected()) return
        try {
            OnePlusBackfillSession(
                gatt = gatt,
                onGlucose = onGlucose,
                onError = onError,
            ).runOnce(
                currentDexTimeSeconds = lastDexTimeSeconds,
                shouldContinue = shouldContinue,
            )
        } catch (t: Throwable) {
            val msg = t.message ?: "ONEPLUS_BACKFILL_FAILED"
            OnePlusLog.e("${OnePlusLogMarkers.ERROR}: $msg", t)
            onError(msg, false)
        }
    }

    private fun writeEgvRequest(preferShort: Boolean) {
        val payload = if (preferShort) {
            OnePlusEGlucoseTx.requestShort()
        } else {
            OnePlusEGlucoseTx.requestWithCrc()
        }
        try {
            gatt.writeControl(payload)
            OnePlusLog.d(
                "${OnePlusLogMarkers.SESSION}: wrote EGlucoseTx short=$preferShort " +
                    "opcode=0x${(OnePlusEGlucoseTx.OPCODE.toInt() and 0xff).toString(16)}",
            )
        } catch (t: Throwable) {
            val msg = t.message ?: "ONEPLUS_CONTROL_WRITE_FAILED"
            OnePlusLog.e("${OnePlusLogMarkers.ERROR}: $msg", t)
            onError(msg, false)
        }
    }

    private fun handleControlPacket(packet: ByteArray, shouldContinue: () -> Boolean) {
        OnePlusTransmitterTimeRx.parse(packet)?.let { rx ->
            applyTransmitterTime(rx)
            return
        }

        OnePlusSessionStopRx.parse(packet)?.let { rx ->
            OnePlusLog.d(
                "${OnePlusLogMarkers.SESSION}: late SessionStopRx status=${rx.status} ok=${rx.isOkay()}",
            )
            if (rx.transmitterTime != 0) {
                lastDexTimeSeconds = rx.transmitterTime
                lastDexTimeAtMs = Clock.System.now().toEpochMilliseconds()
            }
            return
        }

        OnePlusSessionStartRx.parse(packet)?.let { rx ->
            OnePlusLog.d(
                "${OnePlusLogMarkers.SESSION}: late SessionStartRx info=${rx.info} msg=${rx.message()}",
            )
            if (rx.transmitterTime != 0 && rx.transmitterTime != OnePlusSessionStartRx.INVALID_TIME) {
                lastDexTimeSeconds = rx.transmitterTime
                lastDexTimeAtMs = Clock.System.now().toEpochMilliseconds()
            }
            return
        }

        val parsed = OnePlusGlucoseParser.parseControlPacket(packet)
        if (parsed == null) {
            val op = if (packet.isNotEmpty()) packet[0].toInt() and 0xff else -1
            OnePlusLog.d(
                "${OnePlusLogMarkers.SESSION}: Control packet ignored opcode=0x${op.toString(16)} len=${packet.size}",
            )
            return
        }

        if (parsed.calibration == OnePlusCalibrationState.Stopped ||
            parsed.calibration == OnePlusCalibrationState.SensorStopped
        ) {
            if (requestNewSensorStart && !sessionStartAttempted && shouldContinue()) {
                performSessionStart(
                    reason = "calibration=${parsed.calibration.name}",
                    shouldContinue = shouldContinue,
                )
            }
        }

        parsed.sessionAgeSeconds?.takeIf { it > 0 }?.let { age ->
            lastDexTimeSeconds = age
            lastDexTimeAtMs = Clock.System.now().toEpochMilliseconds()
        }

        val warmup = OnePlusCalibrationMapper.toWarmupState(
            state = parsed.calibration,
            nowMs = Clock.System.now().toEpochMilliseconds(),
            sessionAgeSeconds = parsed.sessionAgeSeconds,
        )
        onWarmup(warmup)

        val sample = parsed.sample
        if (parsed.usable && sample != null) {
            OnePlusLog.d(
                "${OnePlusLogMarkers.BG}: mgdl=${sample.mgdl} ts=${sample.timestampMs} " +
                    "cal=${parsed.calibration.name} op=0x${parsed.opcode.toString(16)} flags=${flagsHex(parsed)}",
            )
            onGlucose(sample)
            maybeBackfillAfterEgv(shouldContinue)
        } else {
            OnePlusLog.d(
                "${OnePlusLogMarkers.WARMUP}: egv cal=${parsed.calibration.name} usable=false " +
                    "displayOnly=${parsed.glucoseIsDisplayOnly} flags=${flagsHex(parsed)} " +
                    "age=${parsed.ageSeconds} sessionAge=${parsed.sessionAgeSeconds}",
            )
        }
    }

    private fun flagsHex(parsed: OnePlusGlucoseParser.ParsedEgv): String =
        parsed.flags?.let { "0x" + it.toString(16).padStart(2, '0') } ?: "-"

    /** Juggluco askbackfill after first glucose when dex time is known. */
    private fun maybeBackfillAfterEgv(shouldContinue: () -> Boolean) {
        if (backfillAttempted) return
        if (lastDexTimeSeconds < 1) return
        backfillAttempted = true
        performBackfill(shouldContinue)
    }

    companion object {
        const val CCCD_SETTLE_MS: Long = 200L
        const val DEFAULT_NOTIFY_TIMEOUT_MS: Long = 10_000L
        const val DEFAULT_REWRITE_INTERVAL_MS: Long = 5 * 60_000L
        const val SESSION_START_TIMEOUT_MS: Long = 15_000L
        const val SESSION_STOP_TIMEOUT_MS: Long = 15_000L
        /** xDrip `SessionStopTxMessage.postExecuteGuardTime`. */
        const val SESSION_STOP_GUARD_MS: Long = 1_000L

        /** Per-wait for the calibration answer, and for the calibration state. */
        const val CALIBRATION_REPLY_TIMEOUT_MS: Long = 10_000L

        /** How many packets may arrive before the answer — a glucose packet often comes first. */
        const val CALIBRATION_REPLY_ROUNDS: Int = 3
    }
}
