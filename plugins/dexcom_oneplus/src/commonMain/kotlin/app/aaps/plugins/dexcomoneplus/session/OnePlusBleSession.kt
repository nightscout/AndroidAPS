package app.aaps.plugins.dexcomoneplus.session

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import app.aaps.plugins.dexcomoneplus.OnePlusLog
import app.aaps.plugins.dexcomoneplus.OnePlusLogMarkers
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattClient
import app.aaps.plugins.dexcomoneplus.oem.DeviceProfileRegistry
import app.aaps.plugins.dexcomoneplus.oem.OemDeviceProfile
import app.aaps.plugins.dexcomoneplus.onePlusSleep
import app.aaps.plugins.dexcomoneplus.reconnect.OemAwareReconnectPolicy
import app.aaps.plugins.dexcomoneplus.reconnect.OnePlusReconnectPolicy
import app.aaps.plugins.dexcomoneplus.warmup.OnePlusWarmupClock
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/**
 * ONE+ BLE session lifecycle (scan already done → connect → auth → Control EGV → warm-up/glucose).
 *
 * ⚠️ ASYNC IMPACT: Real fills run on bleExecutor; [startWithPairingCode] blocks that thread
 * through KEKS + [OnePlusEgvSession] loop + reconnect sleeps. [stop] must be callable without
 * waiting for the executor queue (sets [running] false + GATT disconnect to unblock awaits).
 */
interface OnePlusBleSession {
    fun startWithPairingCode(deviceAddress: String, pairingCode: String)
    fun stop(reason: String?)
    fun isUp(): Boolean
    fun warmupState(): OnePlusWarmupState
}

/**
 * Outcome of the pre-connect step (`beforeConnect`).
 *
 * @param advFresh a recent ADV sighting for the target MAC is in hand (UI handoff or in-window
 *   rescan hit) → prefer a fast direct connect (`autoConnect=false`) over the OEM autoConnect park.
 * @param blindFallback the persistent ADV wait gave up waiting and authorises **one** connect
 *   without a sighting. Without this escape a restored session can never leave the wait, because
 *   [OnePlusBleSessionCyclePolicy.allowConnection] refuses every ADV-less connect (field log
 *   2026-08-03: 18 min of `target ADV absent — remain waiting`, no glucose, no error, no retry).
 * @param foreignAdv redacted label of the strongest *other* ONE+ heard while the target stayed
 *   silent — the stale-stored-MAC signature.
 */
data class OnePlusConnectPrep(
    val advFresh: Boolean = false,
    val blindFallback: Boolean = false,
    val foreignAdv: String? = null,
)

/** Next persistent-ADV retry: raised strategy index plus the connect prep to use. */
private data class PersistentRetry(
    val attempt: Int,
    val prep: OnePlusConnectPrep?,
)

/**
 * Pure decisions for the post-Control-loop transition, kept separate from Android BLE calls so the
 * normal duty-cycle behavior can be unit tested.
 *
 * **Link robustness.** Early builds lost the sensor after a while in a car with its Bluetooth on,
 * with a Bluetooth headset playing audio, and in crowded places such as a metro. Audio uses the
 * same radio as BLE and goes first, so connects fail (133, 147) and links drop (8) far more often
 * there. These rules are what keeps the sensor connected in those places. Nothing in their names
 * says so, so check this list before you simplify any of them:
 * - a session that has sent Control traffic never ends in a terminal failure. It goes to the
 *   persistent ADV wait ([recoverExhaustedBudgetWithPersistentWait], [applyFailureBudget]);
 * - the wait listens in long windows that stay inside the platform scan quota
 *   ([PERSISTENT_ADV_SCAN_MS]), so the platform does not block our scans;
 * - it ignores the end of the burst of the cycle that just closed ([POST_CYCLE_ADV_GUARD_MS]);
 * - after a failed `autoConnect` it does not retry at once ([skipAdvWaitAfterFailedConnect]), and
 *   it connects without an ADV at most once per silence window ([authorizeBlindConnect]). Both
 *   stop a burst of connects on a busy radio;
 * - the link never asks for `CONNECTION_PRIORITY_HIGH` (`OemDeviceProfile.forceHighConnectionPriority`).
 *
 * `OnePlusBleSessionCyclePolicyTest` holds these rules for every OEM profile. On Android the GATT
 * client logs the Bluetooth audio state on every link loss, so a field log shows whether a car
 * or a headset was in use.
 */
internal object OnePlusBleSessionCyclePolicy {
    const val POST_COLLECTION_RECONNECT_ATTEMPT = 1

    /**
     * Continuous ADV silence after which the persistent wait authorises one blind connect. Long
     * enough that a normal G7 duty cycle (~5 min) never triggers it, short enough that a sensor the
     * phone simply stopped hearing is retried rather than waited on forever.
     */
    const val BLIND_CONNECT_AFTER_ADV_SILENCE_MS = 4L * 60_000L

    /** Continuous ADV silence after which a stale stored MAC is reported. */
    const val STALE_MAC_SUSPICION_AFTER_MS = 6L * 60_000L

    /**
     * Quiet period after a **successful** EGV cycle before the next ADV wait may start.
     *
     * The transmitter keeps radiating the tail of its current advertising burst for a few hundred
     * milliseconds after it closes the link. Re-arming the wait immediately latches that residual
     * burst, reports "next-cycle target ADV acquired" and connects into a sensor that is already
     * going to sleep. CUBOT field log 2026-08-20: cycle down 17:10:14.012 → ADV at 17:10:14.139
     * (+127 ms) → `connectGatt` → status 133 after 30 s → autoConnect retry → 60 s timeout. That is
     * 97 s of a 300 s cycle spent on a connect that could not succeed, and the silence clock only
     * starts once it ends, so the blind-connect escape is late for the real next window too.
     *
     * The next genuine burst is a whole duty cycle away (~5 min), so a guard of this size cannot
     * hide it. Applies to the healthy-cycle path only: after a *failed* connect the sensor may
     * legitimately still be advertising and that sighting is worth catching.
     */
    const val POST_CYCLE_ADV_GUARD_MS = 10_000L

    /**
     * Length of one filtered ADV window during the persistent wait.
     *
     * Android's throttle counts scan **registrations** (5 per 30 s), not scan time, so a few long
     * windows are far cheaper than many short ones *and* hear the advertisement the moment it
     * arrives instead of at the next poll. The per-OEM [OemDeviceProfile.preConnectScanMs] stays
     * what it is for the normal pre-connect rescan; only this open-ended wait uses the long window.
     *
     * Sized so that two slots (production + staging) polling together stay under the platform quota
     * with room to spare for the UI discovery scan: 20 s + [ADV_WAIT_RESTART_DELAY_MS] ≈ 3 starts
     * per minute per slot, against the 10 per minute the platform allows. It is also the worst-case
     * lag between `stop` and the wait loop noticing it, which is why it is not longer still.
     */
    const val PERSISTENT_ADV_SCAN_MS = 20_000L

    /**
     * Pause between two ADV windows of the persistent wait. Only long enough to keep `stop`
     * responsive between windows — the scan-start rate is governed by [PERSISTENT_ADV_SCAN_MS].
     */
    const val ADV_WAIT_RESTART_DELAY_MS = 250L

    /**
     * A cycle that proved the link works ends in the persistent ADV wait instead of the bounded
     * failure budget.
     *
     * Gating this on a *usable glucose value* made warm-up impossible to survive: a warming sensor
     * only sends `WarmingUp` packets, so every normal duty-cycle disconnect burned one retry, and the
     * budget was exhausted (FAILED, session dead) minutes into the ~30 min warm-up. Any parsed
     * Control packet proves the same thing a glucose value does — the sensor is ours, authenticated
     * and talking.
     */
    fun waitForAdvertisementAfterExit(sessionProvedHealthy: Boolean): Boolean =
        sessionProvedHealthy

    /**
     * Control traffic that proves the session is alive. [OnePlusWarmupState.Phase.FAILED] is
     * excluded on purpose: a stopped / expired / failed sensor must keep the bounded budget so it
     * ends in a real failure instead of waiting for an advertisement that means nothing.
     */
    fun controlTrafficProvesSession(phase: OnePlusWarmupState.Phase): Boolean =
        phase == OnePlusWarmupState.Phase.WARMING || phase == OnePlusWarmupState.Phase.READY

    /**
     * Retry budget exhausted: recover into the persistent ADV wait instead of the terminal FAILED,
     * but ONLY when the Control channel has already delivered at least once. Such a sensor is
     * asleep between radio windows, not lost, so waiting is right.
     *
     * The proof must be real Control traffic, never a successful authentication. A sensor that has
     * no session started completes the whole handshake and then hangs up on the first glucose
     * request, so "authenticated" is true on every cycle while the slot receives nothing at all.
     * Recovering on that signal removed the terminal FAILED, and with it the only way back to a
     * fresh sensor start (SessionStart is issued on attempt 0 only) — the slot then retried for
     * ever and could never come back.
     */
    fun recoverExhaustedBudgetWithPersistentWait(sessionEverProvedControlChannel: Boolean): Boolean =
        sessionEverProvedControlChannel

    /**
     * Warm-up deadline to remember once [state] has been emitted, so the countdown survives the
     * CONNECTING / RECONNECTING states of a duty cycle (those carry no clock of their own and used
     * to blank the UI countdown until the next EGV packet).
     */
    fun warmupDeadlineAfter(previousEndsAtMs: Long?, state: OnePlusWarmupState): Long? =
        when (state.phase) {
            OnePlusWarmupState.Phase.WARMING      -> state.endsAtEpochMs ?: previousEndsAtMs
            OnePlusWarmupState.Phase.READY,
            OnePlusWarmupState.Phase.IDLE,
            OnePlusWarmupState.Phase.FAILED       -> null
            OnePlusWarmupState.Phase.PAIRING,
            OnePlusWarmupState.Phase.CONNECTING,
            OnePlusWarmupState.Phase.RECONNECTING -> previousEndsAtMs
        }

    fun applyFailureBudget(preparedPostCollectionAdvertisement: Boolean): Boolean =
        !preparedPostCollectionAdvertisement

    fun requireFreshAdvertisementBeforeReconnect(persistentAdvertisementMode: Boolean): Boolean =
        persistentAdvertisementMode

    fun allowConnection(
        restoredSessionMode: Boolean,
        advertisementFresh: Boolean,
        blindFallbackAuthorized: Boolean = false,
    ): Boolean = !restoredSessionMode || advertisementFresh || blindFallbackAuthorized

    /**
     * Long enough silence on the stored MAC to suspect it is stale.
     *
     * A foreign ONE+ sighting **strengthens** the log line; it must not **gate** the diagnosis.
     * Under the pre-connect MAC+FEBC filter, a house with one sensor never sees a foreign ADV, so
     * requiring one made this check unreachable (CUBOT field log 2026-08-16).
     */
    fun suspectStaleMac(continuousAdvSilenceMs: Long, foreignSightings: Int): Boolean {
        // [foreignSightings] is logged at the call site; it must not gate the diagnosis.
        return continuousAdvSilenceMs >= STALE_MAC_SUSPICION_AFTER_MS
    }

    /**
     * Strategy index after a persistent-ADV retryable failure. This must **rise**, not reset to
     * [POST_COLLECTION_RECONNECT_ATTEMPT]: Generic/Pixel only enable `autoConnect` from attempt 2,
     * and pinning at 1 made that park unreachable. The failure **budget** stays separate
     * ([applyFailureBudget] is still false while a post-collection wait is prepared).
     */
    fun nextPersistentAttempt(currentAttempt: Int): Int =
        currentAttempt + 1

    /**
     * Fresh ADV → hard connect (fail-fast). Otherwise the OEM `autoConnectFromAttempt` park.
     * Samsung uses `autoConnectFromAttempt = 0`, so a quiet sensor still parks a background connect
     * on attempt 1. That path must stay as-is.
     */
    fun useAutoConnect(
        advFresh: Boolean,
        attempt: Int,
        autoConnectFromAttempt: Int,
    ): Boolean = when {
        advFresh -> false
        autoConnectFromAttempt < 0 -> false
        else -> attempt >= autoConnectFromAttempt
    }

    /**
     * After a failed connect in persistent-ADV mode, skip the next ADV wait only when the failed
     * attempt was a **hard** connect and the next one would use `autoConnect`.
     *
     * Generic CUBOT log: attempt 1 `autoConnect=false` → 133 in 30 s. The next attempt (2) is the
     * first park and should run now, not after another 4 min of filtered scanning.
     *
     * Samsung: attempt 1 already used `autoConnect=true`. Immediate retry would GATT-storm after
     * 133. Return false so Samsung keeps the ADV wait and the 4 min blind cooldown.
     */
    fun skipAdvWaitAfterFailedConnect(
        lastConnectUsedAutoConnect: Boolean,
        nextAttempt: Int,
        autoConnectFromAttempt: Int,
    ): Boolean {
        if (lastConnectUsedAutoConnect) return false
        return useAutoConnect(
            advFresh = false,
            attempt = nextAttempt,
            autoConnectFromAttempt = autoConnectFromAttempt,
        )
    }

    /**
     * One blind connect after [BLIND_CONNECT_AFTER_ADV_SILENCE_MS] of silence, then not again until
     * another full silence window has passed **since that blind**. The silence clock itself must
     * keep running (stale-MAC at 6 min); only the escape is rate-limited.
     *
     * [msSinceLastBlindConnect] null means no blind has been issued in this silence episode.
     */
    fun authorizeBlindConnect(
        continuousAdvSilenceMs: Long,
        msSinceLastBlindConnect: Long? = null,
    ): Boolean {
        if (continuousAdvSilenceMs < BLIND_CONNECT_AFTER_ADV_SILENCE_MS) return false
        val sinceBlind = msSinceLastBlindConnect ?: Long.MAX_VALUE
        return sinceBlind >= BLIND_CONNECT_AFTER_ADV_SILENCE_MS
    }

    /** Whole minutes of silence — the granularity at which the wait re-emits a warm-up state. */
    fun advSilenceMinutes(continuousAdvSilenceMs: Long): Long =
        continuousAdvSilenceMs.coerceAtLeast(0L) / 60_000L
}

/**
 * Session: pairing validation → GATT connect → KEKS auth → Control/EGV loop, with OEM
 * reconnect retries after GATT drop / connect-auth failure. After a successful EGV cycle, the
 * transmitter's disconnect is treated as normal duty cycling: wait persistently for its next ADV
 * instead of consuming the finite failure budget while the radio is asleep.
 */
class OnePlusBleSessionSkeleton(
    private val gatt: OnePlusGattClient,
    private val auth: OnePlusSessionAuth,
    private val reconnectPolicy: OnePlusReconnectPolicy = OemAwareReconnectPolicy(),
    private val profile: OemDeviceProfile = DeviceProfileRegistry.resolve(),
    private val onWarmup: (OnePlusWarmupState) -> Unit = {},
    private val onGlucose: (OnePlusGlucoseSample) -> Unit = {},
    private val onSession: (Boolean, String?) -> Unit = { _, _ -> },
    private val onError: (String, Boolean) -> Unit = { _, _ -> },
    /**
     * When true on the **first** successful connect only: SessionStart **if** the
     * transmitter has no session yet. Never auto SessionStop (Dexcom recovery).
     * Reconnect attempts always pass false.
     */
    private val requestNewSensorStart: Boolean = true,
    /**
     * Ob1-style pre-connect: LE rescan / handoff before [OnePlusGattClient.connect].
     * Runs on bleExecutor; must not touch UI. Returns [OnePlusConnectPrep] (ADV freshness).
     *
     * `scanMs` overrides [OemDeviceProfile.preConnectScanMs] for this call; null keeps the profile
     * value. The persistent ADV wait passes
     * [OnePlusBleSessionCyclePolicy.PERSISTENT_ADV_SCAN_MS] so an open-ended wait costs few scan
     * registrations instead of one every few seconds.
     *
     * Default no-op (tests / stub).
     */
    private val beforeConnect: (deviceAddress: String, attempt: Int, scanMs: Long?) -> OnePlusConnectPrep =
        { _, _, _ -> OnePlusConnectPrep() },
    /** Juggluco-style reconnect: preload 16-byte KEKS shared key when available. */
    private val savedSharedKeyProvider: () -> ByteArray? = { null },
    /** Persist MAC + shared key after successful auth. */
    private val onAuthSuccess: (deviceAddress: String, sharedKey: ByteArray) -> Unit = { _, _ -> },
    /** Clear persisted shared key when short-auth / bond path is invalidated. */
    private val onAuthInvalidate: () -> Unit = {},
    /** Keeps the processor awake during connect / auth (Juggluco does the same). */
    private val cpuLease: OnePlusCpuLease? = null,
    /** Sensor slot owning this session (`prod` / `staging`) — logged on every marker. */
    private val slot: String = OnePlusLogMarkers.SLOT_PRODUCTION,
) : OnePlusBleSession {

    @Volatile
    private var up = false

    @Volatile
    private var running = false

    @Volatile
    private var cancelled = false

    private val lifecycleLock = AapsLock()

    @Volatile
    private var warmup = OnePlusWarmupState(phase = OnePlusWarmupState.Phase.IDLE)

    /**
     * Last known end of warm-up, kept across connection phases so the UI countdown does not blank
     * out on every duty cycle. Maintained by [OnePlusBleSessionCyclePolicy.warmupDeadlineAfter].
     */
    @Volatile
    private var warmupEndsAtEpochMs: Long? = null

    /**
     * At least one cycle received real Control traffic (warm-up state or glucose) — the sensor is
     * ours AND its data channel works.
     *
     * This must NOT be "auth succeeded": a sensor with no active session completes the whole KEKS
     * handshake and then drops the link as soon as it is asked for glucose (peer status 19). Using
     * auth as the proof kept such a sensor in the persistent wait for ever, so the retry budget
     * never ran out, the session never reached FAILED, and the only path that re-issues
     * SessionStart (a fresh start on attempt 0) could never be reached again.
     */
    @Volatile
    private var everProvedControlChannel = false

    /**
     * Start of the current ADV-silence episode. 0 = not in an episode.
     * Reset only on a real target sighting (or a proven healthy cycle), never on a failed connect.
     */
    private var advSilenceStartedMs = 0L

    /** Wall-clock of the last blind-connect escape in this silence episode. 0 = none yet. */
    private var lastBlindConnectAtMs = 0L

    /** Stale-MAC [onError] already posted for this silence episode. */
    private var staleMacReportedThisSilence = false

    /**
     * Whether the connect that just failed used `autoConnect=true`.
     * Used so Generic can skip the ADV wait to reach the autoConnect park, while Samsung
     * (which already parked) does not GATT-storm after status 133.
     */
    private var lastConnectUsedAutoConnect = false

    override fun startWithPairingCode(deviceAddress: String, pairingCode: String) {
        val codeError = OnePlusSessionStart.validationError(pairingCode)
        if (codeError != null) {
            fail(codeError, fatal = false)
            return
        }
        val normalized = OnePlusSessionStart.normalizePairingCode(pairingCode)
        if (deviceAddress.isBlank()) {
            OnePlusLog.i(
                "${OnePlusLogMarkers.SESSION}: [$slot] start pairing (addr blank) profile=${profile.id}",
            )
        } else {
            OnePlusLog.i(
                "${OnePlusLogMarkers.SESSION}: [$slot] start pairing addr=${redactAddress(deviceAddress)} profile=${profile.id}",
            )
        }

        val startAllowed = lifecycleLock.withLock {
            if (cancelled) {
                false
            } else {
                running = true
                true
            }
        }
        if (!startAllowed) {
            OnePlusLog.i(
                "${OnePlusLogMarkers.SESSION}: [$slot] start ignored — session already stopped",
            )
            return
        }
        setWarmup(OnePlusWarmupState(phase = OnePlusWarmupState.Phase.PAIRING, message = "pairing"))

        resetAdvSilenceTracking()
        lastConnectUsedAutoConnect = false
        var attempt = 0
        var preparedConnect: OnePlusConnectPrep? = null
        // A restored session must never fall back to a blind connect while the transmitter sleeps.
        // Explicit new-sensor setup may use its existing bounded initial-connect policy.
        var persistentAdvertisementMode = !requestNewSensorStart
        while (running) {
            if (attempt > 0) {
                val applyFailureBudget = OnePlusBleSessionCyclePolicy.applyFailureBudget(
                    preparedPostCollectionAdvertisement = preparedConnect != null,
                )
                if (applyFailureBudget && !reconnectPolicy.shouldRetry(attempt, profile)) {
                    if (!OnePlusBleSessionCyclePolicy.recoverExhaustedBudgetWithPersistentWait(everProvedControlChannel)) {
                        fail("ONEPLUS_RECONNECT_EXHAUSTED: attempts=$attempt", fatal = false)
                        return
                    }
                    // A sensor that has already sent us Control traffic is asleep, not lost: keep
                    // waiting for its next radio window instead of ending in a terminal FAILED only
                    // a manual restart could leave.
                    OnePlusLog.w(
                        "${OnePlusLogMarkers.RECONNECT}: [$slot] retry budget exhausted after proven Control " +
                            "traffic (attempts=$attempt) — switch to persistent ADV wait",
                    )
                    persistentAdvertisementMode = true
                    preparedConnect = preparePostCollectionReconnect(
                        deviceAddress = deviceAddress,
                        reason = "retry_budget_exhausted_waiting_for_adv",
                    )
                    if (!running || preparedConnect == null) return
                    attempt = OnePlusBleSessionCyclePolicy.POST_COLLECTION_RECONNECT_ATTEMPT
                    // The prepared fresh ADV bypasses the backoff delay and the pre-connect rescan.
                    continue
                }
                if (applyFailureBudget) {
                    val delayMs = reconnectPolicy.nextDelayMs(attempt, profile)
                    OnePlusLog.i(
                        "${OnePlusLogMarkers.RECONNECT}: [$slot] attempt=$attempt delayMs=$delayMs " +
                            "profile=${profile.id} aggressive=${profile.aggressiveReconnect}",
                    )
                    if (!sleepWhileRunning(delayMs)) {
                        stop("cancelled")
                        return
                    }
                } else {
                    val bypassReason = when {
                        preparedConnect?.advFresh == true -> "fresh_adv"
                        preparedConnect?.blindFallback == true -> "blind_escape"
                        else -> "prepared_connect"
                    }
                    OnePlusLog.i(
                        "${OnePlusLogMarkers.RECONNECT}: [$slot] bypass retry delay " +
                            "reason=$bypassReason attempt=$attempt profile=${profile.id}",
                    )
                }
            } else {
                OnePlusLog.i(
                    "${OnePlusLogMarkers.RECONNECT}: [$slot] attempt=0 " +
                        "maxRetries=${profile.connectRetryCount} timeoutMs=${profile.connectTimeoutMs}",
                )
            }

            val wantSessionStart = OnePlusSessionStartPolicy.wantSessionStartOnAttempt(
                requestNewSensorStart = requestNewSensorStart,
                attempt = attempt,
            )
            val outcome = runConnectionCycle(
                deviceAddress = deviceAddress,
                pairingCode = normalized,
                requestNewSensorStart = wantSessionStart,
                attempt = attempt,
                preparedConnect = preparedConnect,
            )
            preparedConnect = null
            if (!running) return

            when (outcome) {
                CycleOutcome.Stopped -> return
                CycleOutcome.HealthyCycleThenExited -> {
                    persistentAdvertisementMode = true
                    resetAdvSilenceTracking()
                    preparedConnect = preparePostCollectionReconnect(
                        deviceAddress = deviceAddress,
                        reason = "egv_cycle_complete_waiting_for_adv",
                        postCycleQuietMs = OnePlusBleSessionCyclePolicy.POST_CYCLE_ADV_GUARD_MS,
                    )
                    if (!running || preparedConnect == null) return
                    // Attempt 1 suppresses SessionStart and reports a reconnect, while the prepared
                    // fresh ADV bypasses both the backoff delay and a second pre-connect scan.
                    attempt = OnePlusBleSessionCyclePolicy.POST_COLLECTION_RECONNECT_ATTEMPT
                }
                CycleOutcome.EgvExited -> {
                    if (OnePlusBleSessionCyclePolicy.requireFreshAdvertisementBeforeReconnect(
                            persistentAdvertisementMode,
                        )
                    ) {
                        val retry = persistentRetryAfterFailure(
                            deviceAddress = deviceAddress,
                            currentAttempt = attempt,
                            reason = "egv_loop_exit_waiting_for_adv",
                        )
                        if (!running || retry.prep == null) return
                        attempt = retry.attempt
                        preparedConnect = retry.prep
                    } else {
                        // Drop before the first usable EGV: retain bounded failure retries.
                        up = false
                        try {
                            gatt.disconnect()
                        } catch (_: Throwable) {
                        }
                        onSession(false, "egv_loop_exit")
                        enterReconnecting("egv_loop_exit")
                        OnePlusLog.i(
                            "${OnePlusLogMarkers.SESSION}: [$slot] down reason=egv_loop_exit → reconnect",
                        )
                        attempt++
                    }
                }
                CycleOutcome.RetryableFailure -> {
                    if (OnePlusBleSessionCyclePolicy.requireFreshAdvertisementBeforeReconnect(
                            persistentAdvertisementMode,
                        )
                    ) {
                        val retry = persistentRetryAfterFailure(
                            deviceAddress = deviceAddress,
                            currentAttempt = attempt,
                            reason = "cycle_failure_waiting_for_adv",
                        )
                        if (!running || retry.prep == null) return
                        attempt = retry.attempt
                        preparedConnect = retry.prep
                    } else {
                        up = false
                        try {
                            gatt.disconnect()
                        } catch (_: Throwable) {
                        }
                        attempt++
                    }
                }
                CycleOutcome.Fatal -> return
            }
        }
    }

    /**
     * Safe to call from any thread. Clears [running] and disconnects GATT so Control awaits unblock.
     *
     * ⚠️ ASYNC IMPACT: Must not be queued behind [startWithPairingCode] on the same single-thread
     * executor alone — callers should invoke [stop] directly (see `OnePlusCgmDriverReal.disconnect`).
     */
    override fun stop(reason: String?) {
        lifecycleLock.withLock {
            cancelled = true
            running = false
            up = false
            warmup = OnePlusWarmupState(phase = OnePlusWarmupState.Phase.IDLE, message = reason)
            warmupEndsAtEpochMs = null
        }
        gatt.disconnect()
        emitWarmup()
        onSession(false, reason)
        OnePlusLog.i("${OnePlusLogMarkers.SESSION}: [$slot] down reason=$reason")
    }

    override fun isUp(): Boolean = up

    override fun warmupState(): OnePlusWarmupState = warmup

    private fun runConnectionCycle(
        deviceAddress: String,
        pairingCode: String,
        requestNewSensorStart: Boolean,
        attempt: Int,
        preparedConnect: OnePlusConnectPrep?,
    ): CycleOutcome {
        // CONNECTING (first attempt) / RECONNECTING (retry) — never PAIRING here, so the UI can tell
        // "establishing link" from the terminal FAILED it used to flash between retries.
        val connectingPhase =
            if (attempt == 0) OnePlusWarmupState.Phase.CONNECTING else OnePlusWarmupState.Phase.RECONNECTING
        setWarmup(OnePlusWarmupState(phase = connectingPhase, message = "connect_attempt_$attempt"))

        val prep = preparedConnect ?: try {
            beforeConnect(deviceAddress, attempt, null)
        } catch (t: Throwable) {
            val msg = t.message ?: "ONEPLUS_BEFORE_CONNECT_FAILED"
            OnePlusLog.e(
                "${OnePlusLogMarkers.ERROR}: [$slot] beforeConnect failed: $msg attempt=$attempt",
            )
            onError(msg, false)
            enterReconnecting(msg)
            return if (running) CycleOutcome.RetryableFailure else CycleOutcome.Stopped
        }
        if (!running) return CycleOutcome.Stopped
        if (!OnePlusBleSessionCyclePolicy.allowConnection(
                restoredSessionMode = !this.requestNewSensorStart,
                advertisementFresh = prep.advFresh,
                blindFallbackAuthorized = prep.blindFallback,
            )
        ) {
            OnePlusLog.i(
                "${OnePlusLogMarkers.SCAN}: [$slot] restored session requires fresh ADV — remain waiting",
            )
            enterReconnecting("waiting_for_next_adv")
            return CycleOutcome.RetryableFailure
        }
        if (prep.blindFallback) {
            OnePlusLog.w(
                "${OnePlusLogMarkers.SESSION}: [$slot] blind connect — no ADV sighting, " +
                    "persistent wait escape (attempt=$attempt)",
            )
        }

        // Fresh ADV in hand → fast direct connect (fail-fast status 133 → quick retry). Otherwise fall
        // back to the OEM autoConnect policy (Samsung parks a background connect when the sensor is
        // quiet — that park cost ~48 s in the field log; the handoff avoids it when a sighting exists).
        val useAutoConnect = OnePlusBleSessionCyclePolicy.useAutoConnect(
            advFresh = prep.advFresh,
            attempt = attempt,
            autoConnectFromAttempt = profile.autoConnectFromAttempt,
        )
        lastConnectUsedAutoConnect = useAutoConnect
        OnePlusLog.i(
            "${OnePlusLogMarkers.SESSION}: [$slot] connect decision advFresh=${prep.advFresh} " +
                "autoConnect=$useAutoConnect attempt=$attempt profile=${profile.id}",
        )
        // Hold the CPU awake through settle, scan→connect handoff, connectGatt and discovery.
        // The wait clock freezes under suspend (Android sleeps on CLOCK_MONOTONIC): field log 26/08/2026
        // showed a 500 ms handoff taking 27.7 s with the screen off, so the in-window connect
        // missed the ADV the wait had just caught. The lock used to start only after connect
        // returned, which was too late.
        val wakeLock = cpuLease?.hold(AUTH_WAKE_LOCK_MS)
        try {
            try {
                gatt.connect(deviceAddress, autoConnect = useAutoConnect)
            } catch (t: Throwable) {
                val msg = t.message ?: "ONEPLUS_GATT_FAILED"
                OnePlusLog.e("${OnePlusLogMarkers.ERROR}: [$slot] $msg attempt=$attempt", t)
                onError(msg, false)
                enterReconnecting(msg)
                return if (running) CycleOutcome.RetryableFailure else CycleOutcome.Stopped
            }

            if (!running) return CycleOutcome.Stopped

            val authResult = auth.authenticate(pairingCode, savedSharedKeyProvider())
            if (!running) return CycleOutcome.Stopped
            if (!authResult.ok) {
                val msg = authResult.message ?: "ONEPLUS_AUTH_FAILED"
                OnePlusLog.e("${OnePlusLogMarkers.ERROR}: [$slot] $msg attempt=$attempt")
                if (authResult.invalidateSharedKey ||
                    msg.contains("bond failure", ignoreCase = true) ||
                    msg.contains("key refresh", ignoreCase = true) ||
                    msg.contains("Missing QR", ignoreCase = true) ||
                    msg.contains("short-auth rejected", ignoreCase = true)
                ) {
                    // Stale shared key / cert path — force full KEKS next attempt (Juggluco resetCerts).
                    try {
                        onAuthInvalidate()
                    } catch (_: Throwable) {
                    }
                }
                onError(msg, false)
                // Retryable: the loop will re-attempt (short-auth reject wipes the key → full J-PAKE
                // next). Show RECONNECTING, NOT FAILED — the old FAILED flash made the user abandon
                // during the reconnect delay (field log 23:18:40).
                enterReconnecting(msg)
                return if (running) CycleOutcome.RetryableFailure else CycleOutcome.Stopped
            }

            authResult.sharedKey?.let { key ->
                try {
                    onAuthSuccess(deviceAddress, key)
                } catch (t: Throwable) {
                    OnePlusLog.w(
                        "${OnePlusLogMarkers.SESSION}: [$slot] onAuthSuccess persist failed: ${t.message}",
                    )
                }
            }

            // Do NOT invent a 30 min WARMING clock here — that blocked ingest even when the
            // transmitter was already producing Ok EGVs. Protocol (TransmitterTime / EGV) sets phase.
            up = true
            setWarmup(OnePlusWarmupState(phase = OnePlusWarmupState.Phase.PAIRING, message = "auth_ok"))
            onSession(true, if (attempt == 0) "session_up" else "session_reconnected")
            OnePlusLog.i(
                "${OnePlusLogMarkers.SESSION}: [$slot] up attempt=$attempt newStart=$requestNewSensorStart — Control/EGV",
            )

            // Any Control traffic proves the link — a warming sensor never sends usable glucose, so
            // gating the persistent ADV wait on glucose alone made warm-up burn the retry budget.
            var sessionProvedHealthy = false
            val egv = OnePlusEgvSession(
                gatt = gatt,
                onWarmup = { state ->
                    if (OnePlusBleSessionCyclePolicy.controlTrafficProvesSession(state.phase)) {
                        sessionProvedHealthy = true
                        everProvedControlChannel = true
                    }
                    setWarmup(state)
                },
                onGlucose = { sample ->
                    sessionProvedHealthy = true
                    everProvedControlChannel = true
                    onGlucose(sample)
                },
                onError = onError,
                requestNewSensorStart = requestNewSensorStart,
            )
            try {
                egv.run(shouldContinue = { running && gatt.isConnected() })
            } catch (t: Throwable) {
                if (!running) return CycleOutcome.Stopped
                val msg = t.message ?: "ONEPLUS_CONTROL_FAILED"
                OnePlusLog.e("${OnePlusLogMarkers.ERROR}: [$slot] $msg", t)
                onError(msg, false)
                enterReconnecting(msg)
                return CycleOutcome.RetryableFailure
            }

            return when {
                !running -> CycleOutcome.Stopped
                OnePlusBleSessionCyclePolicy.waitForAdvertisementAfterExit(sessionProvedHealthy) ->
                    CycleOutcome.HealthyCycleThenExited
                else -> CycleOutcome.EgvExited
            }
        } finally {
            // Held from before connect through the first EGV cycle; Juggluco holds it unbounded.
            wakeLock?.release()
        }
    }

    /**
     * Transient / retryable transition. UI shows RECONNECTING (never terminal FAILED) so the user
     * keeps waiting through the reconnect delay. Terminal failure stays in [fail].
     */
    private fun enterReconnecting(
        message: String?,
        advSilenceMinutes: Long? = null,
        staleMacSuspected: Boolean = false,
    ) {
        if (!running) return
        setWarmup(
            OnePlusWarmupState(
                phase = OnePlusWarmupState.Phase.RECONNECTING,
                message = message,
                advSilenceMinutes = advSilenceMinutes,
                staleMacSuspected = staleMacSuspected,
            ),
        )
    }

    /**
     * Persistent-ADV failure: raise the strategy index (so Generic/Pixel can reach autoConnect)
     * without touching the failure budget. Samsung already parked on attempt 1, so it keeps the
     * ADV wait instead of a tight GATT retry after status 133.
     */
    private fun persistentRetryAfterFailure(
        deviceAddress: String,
        currentAttempt: Int,
        reason: String,
    ): PersistentRetry {
        val nextAttempt = OnePlusBleSessionCyclePolicy.nextPersistentAttempt(currentAttempt)
        if (OnePlusBleSessionCyclePolicy.skipAdvWaitAfterFailedConnect(
                lastConnectUsedAutoConnect = lastConnectUsedAutoConnect,
                nextAttempt = nextAttempt,
                autoConnectFromAttempt = profile.autoConnectFromAttempt,
            )
        ) {
            OnePlusLog.i(
                "${OnePlusLogMarkers.RECONNECT}: [$slot] skip ADV wait — previous was hard connect, " +
                    "retry autoConnect attempt=$nextAttempt profile=${profile.id}",
            )
            lastBlindConnectAtMs = Clock.System.now().toEpochMilliseconds()
            return PersistentRetry(
                attempt = nextAttempt,
                prep = OnePlusConnectPrep(advFresh = false, blindFallback = true),
            )
        }
        return PersistentRetry(
            attempt = nextAttempt,
            prep = preparePostCollectionReconnect(deviceAddress, reason),
        )
    }

    private fun resetAdvSilenceTracking() {
        advSilenceStartedMs = 0L
        lastBlindConnectAtMs = 0L
        staleMacReportedThisSilence = false
    }

    /**
     * Ends the current successful duty cycle and waits for the next transmitter radio window.
     * Watcher exceptions are isolated so they cannot terminate the persistent BLE loop.
     */
    private fun preparePostCollectionReconnect(
        deviceAddress: String,
        reason: String,
        /**
         * Quiet period before the first ADV window, so the transmitter's residual advertising burst
         * is not mistaken for the next cycle — see
         * [OnePlusBleSessionCyclePolicy.POST_CYCLE_ADV_GUARD_MS]. Only the healthy-cycle path passes
         * a value; after a failure the sensor may still be advertising for real.
         */
        postCycleQuietMs: Long = 0L,
    ): OnePlusConnectPrep? {
        if (!running) return null
        up = false
        try {
            gatt.disconnect()
        } catch (_: Throwable) {
        }
        if (!running) return null
        try {
            onSession(false, reason)
        } catch (t: Throwable) {
            OnePlusLog.w(
                "${OnePlusLogMarkers.SESSION}: [$slot] down callback failed: ${t.message}",
            )
        }
        if (!running) return null
        try {
            enterReconnecting("waiting_for_next_adv")
        } catch (t: Throwable) {
            OnePlusLog.w(
                "${OnePlusLogMarkers.WARMUP}: [$slot] waiting callback failed: ${t.message}",
            )
        }
        if (!running) return null
        OnePlusLog.i(
            "${OnePlusLogMarkers.SESSION}: [$slot] cycle down reason=$reason — wait persistently for next ADV",
        )
        if (postCycleQuietMs > 0L) {
            OnePlusLog.i(
                "${OnePlusLogMarkers.SCAN}: [$slot] post-cycle ADV guard ${postCycleQuietMs}ms — " +
                    "ignoring the residual advertising burst of the cycle that just closed",
            )
            if (!sleepWhileRunning(postCycleQuietMs)) return null
        }
        return awaitFreshAdvertisement(deviceAddress)
    }

    /**
     * A successful EGV cycle resets failure semantics. Dexcom advertises only in short windows, so
     * repeatedly scan until the target MAC is actually visible and hand that fresh sighting directly
     * to the next connection cycle — no finite retry exhaustion while the radio is merely asleep.
     *
     * The wait is **not** unbounded any more:
     * - every whole minute of silence re-emits a RECONNECTING state, so the UI / ongoing
     *   notification shows progress instead of a frozen warm-up;
     * - after [OnePlusBleSessionCyclePolicy.BLIND_CONNECT_AFTER_ADV_SILENCE_MS] it returns a
     *   `blindFallback` prep so the cycle may connect without a sighting (a sensor whose ADV the
     *   phone can no longer hear is otherwise never retried);
     * - if the stored MAC stays silent long enough, the stale-MAC suspicion is surfaced through
     *   [onError] once per silence episode (a foreign ONE+ sighting only enriches the message).
     *
     * ⚠️ ASYNC IMPACT: blocks bleExecutor in bounded scan calls; [stop] clears [running], so this
     * loop exits as soon as the current bounded pre-connect scan returns.
     */
    private fun awaitFreshAdvertisement(deviceAddress: String): OnePlusConnectPrep? {
        if (advSilenceStartedMs == 0L) {
            advSilenceStartedMs = Clock.System.now().toEpochMilliseconds()
        }
        val silenceStartedMs = advSilenceStartedMs
        var lastEmittedMinute = -1L
        var lastEmittedStale = false
        var foreignEverSeen: String? = null
        while (running) {
            val prep = try {
                beforeConnect(
                    deviceAddress,
                    OnePlusBleSessionCyclePolicy.POST_COLLECTION_RECONNECT_ATTEMPT,
                    OnePlusBleSessionCyclePolicy.PERSISTENT_ADV_SCAN_MS,
                )
            } catch (t: Throwable) {
                OnePlusLog.w(
                    "${OnePlusLogMarkers.SCAN}: [$slot] waiting for next ADV failed: ${t.message}",
                )
                OnePlusConnectPrep(advFresh = false)
            }
            if (!running) return null
            if (prep.advFresh) {
                OnePlusLog.i(
                    "${OnePlusLogMarkers.SCAN}: [$slot] next-cycle target ADV acquired",
                )
                resetAdvSilenceTracking()
                return prep
            }

            val nowMs = Clock.System.now().toEpochMilliseconds()
            val silenceMs = nowMs - silenceStartedMs
            val silenceMinutes = OnePlusBleSessionCyclePolicy.advSilenceMinutes(silenceMs)
            // A transmitter only advertises in short windows, so a foreign sighting is sporadic:
            // remember it for the whole wait instead of requiring it in the same 8 s scan as the
            // suspicion threshold.
            prep.foreignAdv?.let { foreignEverSeen = it }
            val foreignCount = if (foreignEverSeen != null) 1 else 0

            if (!staleMacReportedThisSilence &&
                OnePlusBleSessionCyclePolicy.suspectStaleMac(silenceMs, foreignCount)
            ) {
                staleMacReportedThisSilence = true
                val message = if (foreignEverSeen != null) {
                    "ONEPLUS_ADV_STALE_MAC: target ${redactAddress(deviceAddress)} silent " +
                        "${silenceMinutes}min while another ONE+ advertises ($foreignEverSeen) — " +
                        "sensor replaced or started in the other slot?"
                } else {
                    "ONEPLUS_ADV_STALE_MAC: target ${redactAddress(deviceAddress)} silent " +
                        "${silenceMinutes}min with no advertisement — stored MAC may be stale"
                }
                OnePlusLog.e("${OnePlusLogMarkers.ERROR}: [$slot] $message")
                try {
                    onError(message, false)
                } catch (_: Throwable) {
                }
            }

            val msSinceLastBlind =
                if (lastBlindConnectAtMs == 0L) null else nowMs - lastBlindConnectAtMs
            if (OnePlusBleSessionCyclePolicy.authorizeBlindConnect(silenceMs, msSinceLastBlind)) {
                OnePlusLog.w(
                    "${OnePlusLogMarkers.SCAN}: [$slot] ADV silent ${silenceMinutes}min — " +
                        "authorising one blind connect (escape from persistent wait)",
                )
                lastBlindConnectAtMs = nowMs
                return prep.copy(blindFallback = true)
            }

            // One state per whole minute (and immediately when the stale-MAC suspicion appears):
            // enough for the UI / ongoing notification to show progress, rare enough not to churn
            // the notification or the warm-up basal guard every 8 s.
            if (silenceMinutes != lastEmittedMinute || staleMacReportedThisSilence != lastEmittedStale) {
                lastEmittedMinute = silenceMinutes
                lastEmittedStale = staleMacReportedThisSilence
                enterReconnecting(
                    message = "waiting_for_adv",
                    advSilenceMinutes = silenceMinutes,
                    staleMacSuspected = staleMacReportedThisSilence,
                )
                if (!running) return null
            }
            OnePlusLog.i(
                "${OnePlusLogMarkers.SCAN}: [$slot] target ADV absent — remain waiting " +
                    "silentMs=$silenceMs foreign=${foreignEverSeen ?: "-"}",
            )
            if (!sleepWhileRunning(OnePlusBleSessionCyclePolicy.ADV_WAIT_RESTART_DELAY_MS)) return null
        }
        return null
    }

    private fun fail(message: String, fatal: Boolean) {
        running = false
        up = false
        try {
            gatt.disconnect()
        } catch (_: Throwable) {
        }
        setWarmup(OnePlusWarmupState(phase = OnePlusWarmupState.Phase.FAILED, message = message))
        onError(message, fatal)
        onSession(false, message)
        OnePlusLog.e("${OnePlusLogMarkers.ERROR}: [$slot] $message fatal=$fatal")
        OnePlusLog.i("${OnePlusLogMarkers.SESSION}: [$slot] down reason=fail")
    }

    /**
     * Publishes [state] and keeps the warm-up deadline sticky: connection phases carry no clock of
     * their own, so without this the UI countdown (and the ongoing notification) went blank on every
     * duty cycle and only came back with the next EGV packet.
     */
    private fun setWarmup(state: OnePlusWarmupState) {
        val published = lifecycleLock.withLock {
            // A stopped / superseded session must not be resurrected by a late callback still queued
            // on the BLE executor: it would republish a stale phase and, worse, bring the sticky
            // deadline back for every later state. [stop] owns the final IDLE state.
            if (cancelled && state.phase != OnePlusWarmupState.Phase.IDLE) return
            val endsAt = OnePlusBleSessionCyclePolicy.warmupDeadlineAfter(warmupEndsAtEpochMs, state)
            warmupEndsAtEpochMs = endsAt
            warmup = if (state.endsAtEpochMs == null) state.copy(endsAtEpochMs = endsAt) else state
            warmup
        }
        emitWarmup(published)
    }

    /** Logs and publishes [state] to the watcher — outside [lifecycleLock], callbacks are foreign code. */
    private fun emitWarmup(state: OnePlusWarmupState = warmup) {
        val remaining = OnePlusWarmupClock.resolveRemainingMs(state, Clock.System.now().toEpochMilliseconds())
        OnePlusLog.i(
            "${OnePlusLogMarkers.WARMUP}: [$slot] phase=${state.phase} remainingMs=$remaining " +
                "msg=${state.message} advSilenceMin=${state.advSilenceMinutes ?: "-"} " +
                "staleMac=${state.staleMacSuspected}",
        )
        onWarmup(state)
    }

    /** Interruptible sleep that exits early when [stop] clears [running]. */
    private fun sleepWhileRunning(delayMs: Long): Boolean {
        if (delayMs <= 0L) return running
        val end = Clock.System.now().toEpochMilliseconds() + delayMs
        while (running && Clock.System.now().toEpochMilliseconds() < end) {
            val slice = (end - Clock.System.now().toEpochMilliseconds()).coerceAtMost(200L).coerceAtLeast(1L)
            if (!onePlusSleep(slice)) return false
        }
        return running
    }

    private fun redactAddress(address: String): String {
        if (address.length <= 5) return "***"
        return "***" + address.takeLast(5)
    }

    private enum class CycleOutcome {
        /** The cycle proved the session works (glucose or warm-up Control traffic) before exiting. */
        HealthyCycleThenExited,
        EgvExited,
        RetryableFailure,
        Stopped,
        Fatal,
    }

    companion object {
        /**
         * Cover settle, scan→connect handoff, connectGatt + discovery, KEKS, bond, first EGV.
         * Must be held before those waits: the clock they use freezes under CPU suspend.
         * Juggluco uses an unbounded wake lock for the whole connection; we bound it.
         */
        private const val AUTH_WAKE_LOCK_MS = 600_000L
    }
}
