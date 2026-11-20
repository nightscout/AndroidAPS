package app.aaps.plugins.dexcomoneplus.session

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.plugins.dexcomoneplus.OnePlusCalibrationOutcome
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import app.aaps.plugins.dexcomoneplus.OnePlusLog
import app.aaps.plugins.dexcomoneplus.OnePlusLogMarkers
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattClient
import app.aaps.plugins.dexcomoneplus.oem.DeviceProfileRegistry
import app.aaps.plugins.dexcomoneplus.oem.OemDeviceProfile
import app.aaps.plugins.dexcomoneplus.onePlusSleep
import app.aaps.plugins.dexcomoneplus.parse.OnePlusCalibrateRx
import app.aaps.plugins.dexcomoneplus.parse.OnePlusCalibrationBoundsRx
import app.aaps.plugins.dexcomoneplus.parse.OnePlusExtendedVersionRx
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
     * The least time between two standing connects of the watch (see `standingConnectWait` of
     * [OnePlusBleSessionSkeleton]), counted from the start of the one before.
     *
     * A standing connect normally ends with the sensor, or after a whole cycle without it. One that
     * fails at once (status 133 on a busy radio) must not be placed again at once: that is the burst
     * of connects the rules above exist to stop, and each round would also scan.
     */
    const val STANDING_CONNECT_MIN_GAP_MS = 60_000L

    /**
     * The longest the watch scans for the sensor at the start of a session, before it falls back to
     * standing connects: one sensor cycle and half a minute, so one wake-up of the sensor is inside
     * it wherever in its cycle the session starts.
     */
    const val STANDING_CONNECT_STARTUP_SCAN_MS = 330_000L

    /**
     * How long the processor is kept awake to place a standing connect: the settle after the last
     * link (up to 4 s), the handoff (0.5 s) and the connect call. Not longer, because the wait
     * itself is the point: the Bluetooth chip waits for the sensor, and the watch sleeps. The lock
     * cannot be released when the connect is placed, the call only returns with the sensor, so it
     * runs to this limit every time. At 10 s, the two locks of a round were held 4 minutes an hour.
     */
    const val STANDING_CONNECT_PLACE_WAKE_LOCK_MS = 6_000L

    /** Margin on the lock that covers the guard before a standing connect; the placing has its own. */
    const val STANDING_CONNECT_GUARD_WAKE_LOCK_MARGIN_MS = 2_000L

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

    /** Android GATT status when the peer ended the link itself (HCI 0x13, remote user terminated). */
    const val GATT_STATUS_PEER_TERMINATED = 19

    /** What to do after a failed cycle in persistent-ADV mode (Android; the iPhone holds a connect). */
    enum class FailureRetry {
        /** Park an `autoConnect` at once, without waiting for an advertisement. */
        IMMEDIATE_AUTO_CONNECT,

        /** The sensor closed the link and went back to sleep: wait for its next cycle, with the guard. */
        WAIT_AFTER_SENSOR_CLOSE,

        /** Wait for the next advertisement. */
        WAIT_FOR_ADV,
    }

    /**
     * Picks the retry after a failed cycle.
     *
     * A link the sensor closed itself ([GATT_STATUS_PEER_TERMINATED]) after we reached it means the
     * sensor is asleep until its next wake-up, about 4.5 min later. An `autoConnect` parked at once
     * cannot succeed then (field log 2026-11-14 06:47: 60 s park, no connect). After a lost link
     * (status 8, supervision timeout) the sensor is sometimes still awake, and the same immediate
     * park saved the cycle 2 times in 5 in a snoop log, so that case keeps
     * [skipAdvWaitAfterFailedConnect].
     *
     * @param sensorReached the connect of this cycle finished (link up, services found)
     * @param linkDownStatus [app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattClient.lastLinkDownStatus]
     */
    fun retryAfterFailure(
        sensorReached: Boolean,
        linkDownStatus: Int?,
        lastConnectUsedAutoConnect: Boolean,
        nextAttempt: Int,
        autoConnectFromAttempt: Int,
    ): FailureRetry = when {
        sensorReached && linkDownStatus == GATT_STATUS_PEER_TERMINATED -> FailureRetry.WAIT_AFTER_SENSOR_CLOSE
        skipAdvWaitAfterFailedConnect(lastConnectUsedAutoConnect, nextAttempt, autoConnectFromAttempt) ->
            FailureRetry.IMMEDIATE_AUTO_CONNECT
        else -> FailureRetry.WAIT_FOR_ADV
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
    /** [OnePlusBleSessionCyclePolicy.POST_CYCLE_ADV_GUARD_MS]; a test shortens it, the app never does. */
    private val postCycleAdvGuardMs: Long = OnePlusBleSessionCyclePolicy.POST_CYCLE_ADV_GUARD_MS,
    /**
     * Fingersticks waiting to be handed to the sensor's own algorithm, or null when this session may
     * not calibrate.
     *
     * The driver passes its queue to every session, the pre-soak ones too, so a promoted session
     * can calibrate. A pre-soak sensor must never be calibrated — it is not the one feeding the
     * loop, and a sensor keeps a calibration for good — so the driver refuses every offer on a
     * pre-soak slot and that queue stays empty. The plugin switch and its checks are a second gate:
     * while the switch is off nothing is offered.
     */
    private val calibrationQueue: OnePlusCalibrationQueue? = null,
    /** What the sensor answered to a calibration, so a refusal or an unreadable answer reaches the user. */
    private val onCalibrationResult: (OnePlusCalibrateRx?) -> Unit = {},
    /** The sensor's calibration state read after a calibration, and whether it holds the value sent. */
    private val onCalibrationState: (OnePlusCalibrationBoundsRx, Boolean) -> Unit = { _, _ -> },
    /** Checks run again right before a waiting value is written, see `OnePlusEgvSession`. Default: refuse. */
    private val calibrationSendCheck: (Int, Long, Long) -> OnePlusCalibrationGate.Refusal? = { _, _, _ ->
        OnePlusCalibrationGate.Refusal.NOT_READY
    },
    /** A waiting value was dropped before any write, and why. */
    private val onCalibrationNotSent: (OnePlusCalibrationOutcome.NotSent.Reason) -> Unit = {},
    /**
     * Where the refusals in a row of this slot's sensor are kept. The driver backs it with the slot
     * store, so the count outlives this session and an app restart. See [OnePlusAuthRefusals].
     */
    private val refusalLedger: OnePlusAuthRefusalLedger = OnePlusAuthRefusalLedgerInMemory(),
    private val refusalPolicy: OnePlusAuthRefusalPolicy = OnePlusAuthRefusalPolicy(),
    /** Whether the session length of this slot's sensor may still be asked (0x52), see `OnePlusEgvSession`. */
    private val askSensorInfo: () -> Boolean = { false },
    private val onSensorInfoAsked: () -> Unit = {},
    private val onSensorInfo: (OnePlusExtendedVersionRx) -> Unit = {},
    /** Warm-up length of this slot's sensor. */
    private val warmupMs: () -> Long = { OnePlusWarmupClock.DEFAULT_WARMUP_MS },
    /**
     * Wait for the sensor's next cycle with a standing connect (`autoConnect`), not with a scan.
     * Only the watch passes true.
     *
     * The scan wait listens at full power for almost the whole 5 minutes between two readings. A
     * phone can afford that, and it is what keeps the sensor in a car or a metro. A watch cannot:
     * it scanned 34 minutes in an hour and used 18 % of its battery (Galaxy Watch 5), and a watch
     * that never heard its sensor was empty in 6 hours. With this on:
     * - after a cycle that reached the sensor, one standing connect is placed and nothing scans.
     *   The Bluetooth chip waits for the sensor, and the processor may sleep;
     * - after a standing connect that ended without the sensor, one scan window follows before the
     *   next one. It keeps the stale address diagnosis and catches a sensor that is advertising now;
     * - two standing connects are at least [standingConnectMinGapMs] apart;
     * - until the sensor was reached once in this session, the watch scans as the phone does, for at
     *   most [standingConnectStartupScanMs]. A standing connect placed at an unknown moment of the
     *   sensor's cycle missed its next wake-up.
     * Every other rule of [OnePlusBleSessionCyclePolicy] is unchanged.
     */
    private val standingConnectWait: Boolean = false,
    /** [OnePlusBleSessionCyclePolicy.STANDING_CONNECT_MIN_GAP_MS]; a test shortens it, the app never does. */
    private val standingConnectMinGapMs: Long = OnePlusBleSessionCyclePolicy.STANDING_CONNECT_MIN_GAP_MS,
    /** [OnePlusBleSessionCyclePolicy.STANDING_CONNECT_STARTUP_SCAN_MS]; a test changes it, the app never does. */
    private val standingConnectStartupScanMs: Long = OnePlusBleSessionCyclePolicy.STANDING_CONNECT_STARTUP_SCAN_MS,
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

    /** When the connect of the current cycle finished (link up, services found); 0 = not reached. */
    private var cycleLinkReadyAtMs = 0L

    /** When the last standing connect was placed (see `standingConnectWait`); 0 = none yet. */
    private var lastStandingConnectAtMs = 0L

    /** True once a connect of this session reached the sensor: from then on its cycle is known. */
    private var sensorReachedThisSession = false

    /** When the watch's start-up scan began, see [startupScanRuns]; 0 = not yet. */
    private var startupScanStartedAtMs = 0L

    /**
     * True while the watch still looks for the sensor by scanning, as the phone does, and places no
     * standing connect: from the start of a session until the sensor was reached once, for at most
     * [standingConnectStartupScanMs].
     *
     * A standing connect placed right after a reading catches the sensor's next wake-up. One placed
     * at any other moment did not: on a Galaxy Watch 5 the first reading after each start of the
     * watch app was missed, twice out of two. So the first reading is found by scanning. The time
     * limit keeps a watch that never hears its sensor from scanning without end, which is how a
     * watch battery went empty in 6 hours.
     */
    private fun startupScanRuns(nowMs: Long): Boolean {
        if (sensorReachedThisSession) return false
        if (startupScanStartedAtMs == 0L) startupScanStartedAtMs = nowMs
        return nowMs - startupScanStartedAtMs < standingConnectStartupScanMs
    }

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
                        postCycleQuietMs = postCycleAdvGuardMs,
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

        // Before anything else, even the scan: a sensor that refused us must not be asked again too
        // soon, nor at all once it refused enough times to lock itself. Normal link losses never
        // reach this, they are not refusals.
        var usablePrep = preparedConnect
        when (val decision = refusalPolicy.decide(refusalLedger.load(), deviceAddress, nowMs())) {
            OnePlusAuthRefusalPolicy.Decision.Locked  -> {
                failRefusedTooOften(refusalLedger.load().count)
                return CycleOutcome.Fatal
            }

            is OnePlusAuthRefusalPolicy.Decision.Wait -> {
                val waitMs = decision.untilMs - nowMs()
                OnePlusLog.w(
                    "${OnePlusLogMarkers.SESSION}: [$slot] the sensor refused us " +
                        "${refusalLedger.load().count} time(s) in a row, waiting ${waitMs / 1000}s before asking again " +
                        "(${OnePlusAuthRefusalPolicy.MAX_REFUSALS_IN_A_ROW + 1} in a row lock it)",
                )
                enterReconnecting(OnePlusAuthRefusalPolicy.MESSAGE_PAUSED)
                if (!sleepWhileRunning(waitMs)) return CycleOutcome.Stopped
                // A sighting from before the pause is long out of date.
                usablePrep = null
            }

            OnePlusAuthRefusalPolicy.Decision.Allowed -> Unit
        }

        val prep = usablePrep ?: try {
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
        if (prep.blindFallback && !standingConnectWait) {
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
        cycleLinkReadyAtMs = 0L
        OnePlusLog.i(
            "${OnePlusLogMarkers.SESSION}: [$slot] connect decision advFresh=${prep.advFresh} " +
                "autoConnect=$useAutoConnect attempt=$attempt profile=${profile.id}",
        )
        // Hold the CPU awake through settle, scan→connect handoff, connectGatt and discovery.
        // The wait clock freezes under suspend (Android sleeps on CLOCK_MONOTONIC): field log 26/08/2026
        // showed a 500 ms handoff taking 27.7 s with the screen off, so the in-window connect
        // missed the ADV the wait had just caught. The lock used to start only after connect
        // returned, which was too late.
        //
        // A standing connect of the watch is the exception: it may wait for minutes, and holding the
        // processor awake for that long would cost more than the scan it replaces. So the lock only
        // covers placing the connect, and the full one is taken when the link is up. In between, the
        // GATT client holds its own short lock from the moment the sensor connects.
        val standingConnect = standingConnectWait && useAutoConnect
        if (standingConnect) {
            lastStandingConnectAtMs = nowMs()
            OnePlusLog.i(
                "${OnePlusLogMarkers.SESSION}: [$slot] standing connect — waiting for the sensor " +
                    "without a scan (attempt=$attempt)",
            )
        }
        var wakeLock = cpuLease?.hold(
            if (standingConnect) OnePlusBleSessionCyclePolicy.STANDING_CONNECT_PLACE_WAKE_LOCK_MS else AUTH_WAKE_LOCK_MS,
        )
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
            if (standingConnect) {
                wakeLock?.release()
                wakeLock = cpuLease?.hold(AUTH_WAKE_LOCK_MS)
            }

            if (!running) return CycleOutcome.Stopped
            cycleLinkReadyAtMs = Clock.System.now().toEpochMilliseconds()
            sensorReachedThisSession = true

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
                if (authResult.refusedBySensor && recordRefusal(deviceAddress, authResult.refusedSavedKey)) {
                    return CycleOutcome.Fatal
                }
                onError(msg, false)
                // Retryable: the loop will re-attempt (short-auth reject wipes the key → full J-PAKE
                // next). Show RECONNECTING, NOT FAILED — the old FAILED flash made the user abandon
                // during the reconnect delay (field log 23:18:40).
                enterReconnecting(msg)
                return if (running) CycleOutcome.RetryableFailure else CycleOutcome.Stopped
            }

            if (refusalLedger.load().count > 0) {
                OnePlusLog.i("${OnePlusLogMarkers.SESSION}: [$slot] the sensor accepted us, refusal count cleared")
                refusalLedger.save(refusalPolicy.afterSuccess())
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
                calibrationQueue = calibrationQueue,
                onCalibrationResult = onCalibrationResult,
                onCalibrationState = onCalibrationState,
                calibrationSendCheck = calibrationSendCheck,
                onCalibrationNotSent = onCalibrationNotSent,
                askSensorInfo = askSensorInfo,
                onSensorInfoAsked = onSensorInfoAsked,
                onSensorInfo = onSensorInfo,
                warmupMs = warmupMs,
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
        val sensorReachedAtMs = cycleLinkReadyAtMs
        cycleLinkReadyAtMs = 0L
        if (sensorReachedAtMs != 0L) {
            // The sensor was there a moment ago, so its cycle is known again: count the silence from
            // that moment, and the blind connect lands near its next wake-up instead of being placed
            // from an older silence or blocked by an older blind.
            advSilenceStartedMs = sensorReachedAtMs
            lastBlindConnectAtMs = 0L
            staleMacReportedThisSilence = false
        }
        val linkDownStatus = gatt.lastLinkDownStatus()
        val retry = OnePlusBleSessionCyclePolicy.retryAfterFailure(
            sensorReached = sensorReachedAtMs != 0L,
            linkDownStatus = linkDownStatus,
            lastConnectUsedAutoConnect = lastConnectUsedAutoConnect,
            nextAttempt = nextAttempt,
            autoConnectFromAttempt = profile.autoConnectFromAttempt,
        )
        // On the watch an autoConnect placed at once is a standing connect at an unknown moment of
        // the sensor's cycle. Until the sensor was reached once, the watch scans for it instead.
        val startupScan = standingConnectWait &&
            retry == OnePlusBleSessionCyclePolicy.FailureRetry.IMMEDIATE_AUTO_CONNECT && startupScanRuns(nowMs())
        return when (if (startupScan) OnePlusBleSessionCyclePolicy.FailureRetry.WAIT_FOR_ADV else retry) {
            OnePlusBleSessionCyclePolicy.FailureRetry.IMMEDIATE_AUTO_CONNECT -> {
                OnePlusLog.i(
                    "${OnePlusLogMarkers.RECONNECT}: [$slot] skip ADV wait — previous was hard connect, " +
                        "retry autoConnect attempt=$nextAttempt profile=${profile.id} linkDown=$linkDownStatus",
                )
                // Not counted as a blind connect: it would block the real blind escape for a whole
                // silence window, and that escape is how the next cycle is caught.
                PersistentRetry(
                    attempt = nextAttempt,
                    prep = OnePlusConnectPrep(advFresh = false, blindFallback = true),
                )
            }

            OnePlusBleSessionCyclePolicy.FailureRetry.WAIT_AFTER_SENSOR_CLOSE -> {
                OnePlusLog.i(
                    "${OnePlusLogMarkers.RECONNECT}: [$slot] sensor closed the link (status $linkDownStatus) — " +
                        "no immediate retry, wait for its next cycle attempt=$nextAttempt",
                )
                PersistentRetry(
                    attempt = nextAttempt,
                    prep = preparePostCollectionReconnect(deviceAddress, reason, postCycleQuietMs = postCycleAdvGuardMs),
                )
            }

            OnePlusBleSessionCyclePolicy.FailureRetry.WAIT_FOR_ADV -> PersistentRetry(
                attempt = nextAttempt,
                prep = preparePostCollectionReconnect(deviceAddress, reason),
            )
        }
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
        // The guard is passed exactly when the sensor was reached a moment ago and closed the link
        // itself. The watch then knows where the sensor is in its cycle and has nothing to scan for.
        val sensorWasJustHere = postCycleQuietMs > 0L
        // Awake through the guard, or a sleeping watch ends it late, places the connect late and
        // misses the sensor. The placing takes its own lock. This one ends by itself: the wait that
        // follows must not hold it.
        if (standingConnectWait && sensorWasJustHere) {
            cpuLease?.hold(postCycleQuietMs + OnePlusBleSessionCyclePolicy.STANDING_CONNECT_GUARD_WAKE_LOCK_MARGIN_MS)
        }
        if (postCycleQuietMs > 0L) {
            OnePlusLog.i(
                "${OnePlusLogMarkers.SCAN}: [$slot] post-cycle ADV guard ${postCycleQuietMs}ms — " +
                    "ignoring the residual advertising burst of the cycle that just closed",
            )
            if (!sleepWhileRunning(postCycleQuietMs)) return null
        }
        if (standingConnectWait && sensorWasJustHere) {
            return OnePlusConnectPrep(advFresh = false, blindFallback = true)
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
        if (standingConnectWait && lastStandingConnectAtMs != 0L) {
            // A standing connect that failed at once: no new one, and no scan, before the gap is over.
            val sinceLastMs = nowMs() - lastStandingConnectAtMs
            if (sinceLastMs in 0 until standingConnectMinGapMs) {
                OnePlusLog.i(
                    "${OnePlusLogMarkers.RECONNECT}: [$slot] the standing connect ended after ${sinceLastMs}ms — " +
                        "waiting ${standingConnectMinGapMs - sinceLastMs}ms before the next one",
                )
                if (!sleepWhileRunning(standingConnectMinGapMs - sinceLastMs)) return null
            }
        }
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

            if (standingConnectWait && !startupScanRuns(nowMs)) {
                // The watch scans one window, not without end: the sensor did not answer the standing
                // connect and is not advertising now, so the next standing connect waits for it.
                enterReconnecting(
                    message = "waiting_for_adv",
                    advSilenceMinutes = silenceMinutes,
                    staleMacSuspected = staleMacReportedThisSilence,
                )
                if (!running) return null
                OnePlusLog.i(
                    "${OnePlusLogMarkers.SCAN}: [$slot] target ADV absent after one window — " +
                        "standing connect silentMs=$silenceMs foreign=${foreignEverSeen ?: "-"}",
                )
                return prep.copy(blindFallback = true)
            }

            val msSinceLastBlind =
                if (lastBlindConnectAtMs == 0L) null else nowMs - lastBlindConnectAtMs
            // Not during the watch's start-up scan: its blind connect is a standing connect, and one
            // placed at an unknown moment is what the start-up scan is there to avoid.
            if (!standingConnectWait && OnePlusBleSessionCyclePolicy.authorizeBlindConnect(silenceMs, msSinceLastBlind)) {
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

    /**
     * Counts one refusal from [deviceAddress].
     *
     * @return true when that was the last refusal we allow: the session has then failed for good and
     *   the caller must stop.
     */
    private fun recordRefusal(deviceAddress: String, withSavedKey: Boolean): Boolean {
        val updated = refusalPolicy.afterRefusal(refusalLedger.load(), deviceAddress, nowMs(), withSavedKey)
        refusalLedger.save(updated)
        OnePlusLog.w(
            "${OnePlusLogMarkers.SESSION}: [$slot] the sensor refused authentication " +
                "${updated.count} time(s) in a row savedKey=$withSavedKey",
        )
        if (refusalPolicy.decide(updated, deviceAddress, nowMs()) != OnePlusAuthRefusalPolicy.Decision.Locked) return false
        failRefusedTooOften(updated.count)
        return true
    }

    /** Stops for good, before the refusal that would lock the sensor. Only the user starts again. */
    private fun failRefusedTooOften(count: Int) {
        fail(
            "${OnePlusAuthRefusalPolicy.MESSAGE_LOCKED}: the sensor refused us $count times in a row, " +
                "stopped before it locks itself; check the code, close other apps that use the sensor, then start it again",
            fatal = false,
        )
    }

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

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
