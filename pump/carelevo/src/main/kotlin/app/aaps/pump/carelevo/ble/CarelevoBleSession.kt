package app.aaps.pump.carelevo.ble

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.carelevo.ble.commands.AlertAlarmSetCommand
import app.aaps.pump.carelevo.ble.commands.AdditionalPrimingCommand
import app.aaps.pump.carelevo.ble.commands.AppAuthCommand
import app.aaps.pump.carelevo.ble.commands.BasalProgramCommand
import app.aaps.pump.carelevo.ble.commands.InfusionInfoCommand
import app.aaps.pump.carelevo.ble.commands.InfusionInfoResponse
import app.aaps.pump.carelevo.ble.commands.MacAddressCommand
import app.aaps.pump.carelevo.ble.commands.PatchInfoResponse
import app.aaps.pump.carelevo.ble.commands.SafetyCheckCommand
import app.aaps.pump.carelevo.ble.commands.SafetyCheckResponse
import app.aaps.pump.carelevo.ble.commands.SetTimeForPatchInfoCommand
import app.aaps.pump.carelevo.ble.commands.SimpleResultResponse
import app.aaps.pump.carelevo.ble.commands.ThresholdSetupCommand
import app.aaps.pump.carelevo.ble.gatt.BleTransportGattConnection
import app.aaps.pump.carelevo.ble.gatt.GattConnState
import app.aaps.pump.carelevo.ble.gatt.GattEvent
import app.aaps.pump.carelevo.ext.checkSumV2
import app.aaps.pump.carelevo.ext.convertHexToByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.joda.time.DateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Owns BLE sessions: connect → discover services → enable notifications → run [BleClient] exchanges → close.
 *
 * Two modes share one code path ([withSession]):
 * - **Queue-owned held link** ([openLink]/[requestConnect] … [requestDisconnect]): the AAPS CommandQueue
 *   holds ONE session open across a whole connect→run-ops→disconnect burst; every op reuses it, so a
 *   multi-op sequence (e.g. cancel-temp then pump-stop) does NOT re-dial between ops. This is the resting
 *   therapy path once a patch is activated. See `_docs/CARELEVO_QUEUE_OWNED_LINK.md`.
 * - **Transient per-op session** ([transientSession]): open→run→close for pre-activation / pairing / any op
 *   with no held link.
 *
 * Either way each session builds a **fresh** [BleTransportGattConnection] + [BleClientImpl] + [CoroutineScope]
 * and closes them once: [BleTransportGattConnection.close] is one-shot (it latches `closed` and releases the
 * transport's single listener slot), so a closed instance is never reused — [openLink] builds a new one on
 * every connect, and [tearDownHeld]/[transientSession] close it exactly once.
 *
 * A session opens its own GATT; two GATT clients to one patch cause the status-133 collision, so a
 * session must not run concurrently with any other link to the patch. The caller (a customCommand)
 * is responsible for ensuring no other link is active before invoking a session.
 */
@Singleton
class CarelevoBleSession @Inject constructor(
    private val transport: CarelevoBleTransport,
    @Named("characterRx") private val writeUuid: UUID,
    @Named("characterTx") private val notifyUuid: UUID,
    private val aapsLogger: AAPSLogger
) {

    // The new transport is a @Singleton with a SINGLE GATT + single listener slot, so two sessions can
    // never physically overlap. This mutex enforces that at the API level: every session serializes, so an
    // out-of-band caller (e.g. a bolus cancel fired off the queue worker by cancelAllBoluses) waits for the
    // in-flight session to close and release before it opens — never a two-GATT status-133 collision.
    private val sessionMutex = Mutex()

    // Test seam: the per-session scope dispatcher. Production always uses IO; tests inject a
    // TestDispatcher so the event-router subscription and frame emissions are deterministically ordered
    // (the events SharedFlow has no replay, so a frame emitted before the router subscribes is lost —
    // impossible against real-radio latencies, but instant fake responses can hit it).
    internal var sessionDispatcher: CoroutineDispatcher = Dispatchers.IO

    // Wall-clock of the last session close, for the inter-session settle below.
    @Volatile private var lastCloseAtMs = 0L

    /**
     * Handler for unsolicited notifications (alarms, stop/basal-restart reports) received while a session
     * is open — the [BleClient.unsolicitedEvents] bridge the BLE migration dropped. Set by the pump plugin
     * on start, cleared on stop. Invoked on the session's IO scope; the handler MUST NOT start another BLE
     * session ([withSession] holds [sessionMutex] for its whole duration, so a nested session self-deadlocks).
     */
    @Volatile var unsolicitedHandler: ((UnsolicitedMessage) -> Unit)? = null

    private val _connected = MutableStateFlow(false)
    /**
     * Queue-owned held-link state: true only while [openLink]'s held link is up — NOT for transient per-op
     * sessions ([transientSession]). The coordinator reports this as `Pump.isConnected` post-activation, so
     * a transient op must never flip it (else the queue would skip connect() and re-dial per op).
     */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _lastConnectedAt = MutableStateFlow(0L)
    /** Wall-clock (ms) of the last time a session reached CONNECTED — the "last connection" reachability signal. */
    val lastConnectedAt: StateFlow<Long> = _lastConnectedAt.asStateFlow()

    // ===== Queue-owned held link (CommandQueue lifecycle) =====
    // The AAPS CommandQueue (QueueWorker) drives connect() → run ops → disconnect(). openLink holds ONE
    // session open across a whole queue-busy burst; ops reuse it via withSession (no per-op re-dial), and the
    // queue's disconnect() closes it. The new stack dials autoConnect=false with no self-reconnect, so a close
    // stays closed and the queue is the sole reconnect driver. See _docs/CARELEVO_QUEUE_OWNED_LINK.md.

    // Test seam (mirrors sessionDispatcher): the dispatcher for queue-driven connect/disconnect kicks.
    // Production uses IO; tests inject the shared test dispatcher so requestConnect's async openLink is drivable.
    internal var linkDispatcher: CoroutineDispatcher = Dispatchers.IO
    /** App-lifetime scope for queue-driven connect/disconnect kicks (never a per-session scope). */
    private val linkScope: CoroutineScope by lazy { CoroutineScope(linkDispatcher + SupervisorJob()) }

    // True while a queue-driven connect attempt is in flight; its compareAndSet also guards against piling
    // up overlapping connect launches (feeds `Pump.isConnecting` via the coordinator).
    private val _connecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _connecting.asStateFlow()

    /** The in-flight connect job, so [requestDisconnect]/stopConnecting can cancel a slow/hung openLink. */
    @Volatile private var connectJob: Job? = null

    private class HeldLink(
        val gatt: BleTransportGattConnection,
        val client: BleClient,
        val scope: CoroutineScope
    )

    /** The currently held queue-owned link, or null when no session is up. */
    @Volatile private var heldLink: HeldLink? = null

    /** Read Infusion Info (0x31 → 0x91) — the periodic status read (reservoir, totals, pump state). */
    suspend fun readInfusionInfo(address: String): InfusionInfoResponse =
        withSession(address, "infusion info") { it.request(InfusionInfoCommand()) }

    /** Run any single-response [command] (write or read) on a fresh session. */
    suspend fun <R : BleResponse> runSingle(address: String, command: BleCommand<R>, timeoutMs: Long = READ_TIMEOUT_MS): R =
        withSession(address, command::class.simpleName ?: "command", timeoutMs) { it.request(command) }

    /** Additional priming (0x1D -> 0x7D) may take longer than a normal single write. */
    suspend fun runAdditionalPriming(address: String): SimpleResultResponse =
        withSession(address, "additional priming", ADDITIONAL_PRIMING_TIMEOUT_MS) { it.request(AdditionalPrimingCommand()) }

    /**
     * Run the streaming Safety Check (0x12 → 0x72). [onFrame] is invoked for every decoded frame (each
     * progress report and the terminal SUCCESS/error) as the pump reports it; the stream completes on the
     * terminal frame. Uses a long timeout — the check runs ~100-210 s.
     */
    suspend fun runSafetyCheck(address: String, onFrame: (SafetyCheckResponse) -> Unit) =
        withSession(address, "safety check", SAFETY_CHECK_TIMEOUT_MS) { client ->
            client.requestStream(SafetyCheckCommand()).collect { onFrame(it) }
        }

    /**
     * Write a full basal program — the initial set (activation, 0x13 → 0x73, [isUpdate] = false) or the
     * mid-therapy profile update (0x21 → 0x81, [isUpdate] = true; the V2 update sends the same 3-write
     * shape as set, just under the change opcode). A full program is **three sequential
     * [BasalProgramCommand]s** (seqNo 0, 1, 2) that MUST share ONE connection, so — unlike [runSingle] —
     * all three run inside a single session. [programs] are the per-seqNo segment-speed lists (v2 sends
     * speed only). Returns true only if every write reports `resultCode == 0`; short-circuits on the first
     * failure (`all` stops early) so a rejected seqNo does not send the rest of a partial program.
     */
    suspend fun runBasalProgram(address: String, programs: List<List<Double>>, isUpdate: Boolean = false): Boolean =
        withSession(address, "basal program", BASAL_PROGRAM_TIMEOUT_MS) { client ->
            programs.withIndex().all { (index, speeds) ->
                client.request(BasalProgramCommand(isUpdate = isUpdate, seqNo = index, segmentSpeeds = speeds)).resultCode == RESULT_SUCCESS
            }
        }

    /**
     * Pair a factory-fresh patch, all on ONE session: connect → **create the Android bond**
     * (`ensureBond`; the patch requires the bond be created after CONNECTED, before discovery) →
     * discover → enable notifications → MAC read (0x3B) → checksum app-auth (0x4B) → set-time→patch-info
     * rounds (0x11 → 0x93+0x94, retried while the serial is empty) → alert-alarm mode (0x48) →
     * threshold bundle (0x1B). Persistence is NOT done here — the caller feeds the returned
     * [PairingResult] to `CarelevoConnectNewPatchUseCase.persistNewPatch`.
     */
    suspend fun runPairing(address: String, spec: PairingSpec): PairingResult =
        withSession(address, "pairing", PAIRING_TIMEOUT_MS, ensureBond = true) { client ->
            val key = (0..255).random()
            val macResponse = client.request(MacAddressCommand(key.toByte()))
            // The persisted MAC is lowercase, colon-separated.
            val colonMac = macResponse.macAddress.lowercase().chunked(2).joinToString(":")
            aapsLogger.debug(LTag.PUMPCOMM, "bleSession: pairing MAC=$colonMac")
            val checkSumByte = (macResponse.macAddress + macResponse.checkSum).convertHexToByteArray().checkSumV2(key)
            val auth = client.request(AppAuthCommand(checkSumByte.toUByte().toInt()))
            check(auth.resultCode == RESULT_SUCCESS) { "app auth failed result=${auth.resultCode}" }

            // KNOWN RESIDUAL RISK (accepted): the retry rounds below share ONE live session/collector,
            // and BleMultiCommand correlation is opcode-only (the 0x93/0x94 frames carry no round or
            // correlation byte the wire protocol could check). A round-1 frame arriving AFTER its own
            // 10 s window expired could therefore satisfy one of round-2's expected opcodes and mix
            // round-1/round-2 data (e.g. round-1 serial + round-2 firmware). The window is a frame
            // delayed >10 s but landing exactly inside the next round — unobserved on real hardware,
            // and a fresh session per round would repeat MAC/auth against a factory-fresh patch for a
            // worse trade. If it ever bites, the pairing result is rejected by the serial/detail
            // result-code checks below or caught at first status read.
            var patchInfo: PatchInfoResponse? = null
            for (round in 1..PATCH_INFO_ROUND_RETRY_COUNT) {
                val info = withTimeoutOrNull(PATCH_INFO_ROUND_TIMEOUT_MS.milliseconds) {
                    client.requestMultiple(SetTimeForPatchInfoCommand(subId = 0, volume = spec.volume, aidMode = 0, dateTime = DateTime.now()))
                }
                if (info != null && info.serialResultCode == RESULT_SUCCESS && info.serialNumber.trim().isNotEmpty() && info.detailResultCode == RESULT_SUCCESS) {
                    patchInfo = info
                    break
                }
                aapsLogger.warn(
                    LTag.PUMPCOMM,
                    "bleSession: invalid patch info round=$round/$PATCH_INFO_ROUND_RETRY_COUNT " +
                        "result=${info?.serialResultCode} serial=${info?.serialNumber?.trim()} detailResult=${info?.detailResultCode}"
                )
            }
            val info = patchInfo ?: throw IllegalStateException("patch info invalid after $PATCH_INFO_ROUND_RETRY_COUNT rounds")

            val alarm = client.request(AlertAlarmSetCommand(ALERT_ALARM_MODE_DEFAULT))
            check(alarm.resultCode == RESULT_SUCCESS) { "alert alarm set failed result=${alarm.resultCode}" }
            val threshold = client.request(
                ThresholdSetupCommand(
                    insulinRemainsThreshold = spec.remains,
                    expiryThreshold = spec.expiry,
                    maxBasalSpeed = spec.maxBasalSpeed,
                    maxBolusDose = spec.maxBolusDose,
                    buzzUse = spec.buzzUse
                )
            )
            check(threshold.resultCode == RESULT_SUCCESS) { "threshold setup failed result=${threshold.resultCode}" }

            PairingResult(
                address = colonMac,
                serialNumber = info.serialNumber.trim(),
                firmwareVersion = info.firmwareVersion,
                modelName = info.modelName
            )
        }

    /** Inputs of the activation threshold/set-time writes. */
    data class PairingSpec(
        val volume: Int,
        val remains: Int,
        val expiry: Int,
        val maxBasalSpeed: Double,
        val maxBolusDose: Double,
        val buzzUse: Boolean
    )

    /** Identity of the freshly paired patch, decoded from the 0x9B + 0x93/0x94 responses. */
    data class PairingResult(
        val address: String,
        val serialNumber: String,
        val firmwareVersion: String,
        val modelName: String
    )

    /**
     * Fire ONE connect attempt and return — the queue re-polls [connected] until true (matching the
     * Dash/Medtrum `connect()` contract). Idempotent: no-ops while a link is already up or a connect is
     * in flight. Called from the QueueWorker thread via the coordinator's `connect()`.
     */
    fun requestConnect(address: String, reason: String) {
        if (_connected.value || !_connecting.compareAndSet(false, true)) return
        connectJob = linkScope.launch {
            try {
                openLink(address, reason)
            } catch (c: CancellationException) {
                throw c // aborted by requestDisconnect/stopConnecting — not an error
            } catch (t: Throwable) {
                aapsLogger.error(LTag.PUMPCOMM, "bleSession: openLink failed reason=$reason", t)
            } finally {
                _connecting.value = false
            }
        }
    }

    /**
     * Close the held link (queue `disconnect()` / `stopConnecting()`). Also cancels any in-flight
     * [openLink] so a slow/hung connect is aborted promptly rather than briefly opening the link first.
     */
    fun requestDisconnect(reason: String) {
        connectJob?.cancel()
        connectJob = null
        linkScope.launch { sessionMutex.withLock { tearDownHeld(reason) } }
    }

    /**
     * Open the queue-owned held link — one attempt, then leave it up for [withSession] to reuse until the
     * queue's `disconnect()` (or an unexpected drop) closes it. Holds [sessionMutex] for the handshake so no
     * transient session can race it onto the transport's single GATT.
     */
    private suspend fun openLink(address: String, reason: String) = sessionMutex.withLock {
        if (heldLink != null) return@withLock
        awaitInterSessionSettle()
        val mac = address.uppercase()
        val scope = CoroutineScope(sessionDispatcher + SupervisorJob())
        val gatt = BleTransportGattConnection(transport, writeUuid, notifyUuid, scope)
        val client: BleClient = BleClientImpl(gatt, writeUuid, notifyUuid, scope)
        subscribeUnsolicited(scope, client)
        try {
            withTimeout(CONNECT_TIMEOUT_MS.milliseconds) { open(gatt, mac) }
        } catch (t: Throwable) {
            // Half-open handshake: release the fresh gatt + scope (one-shot close) so the next queue
            // connect() starts clean.
            gatt.close()
            scope.cancel()
            lastCloseAtMs = System.currentTimeMillis()
            throw t
        }
        heldLink = HeldLink(gatt, client, scope)
        // Subscribe the drop watcher (UNDISPATCHED → live synchronously) BEFORE marking the link up, so a
        // DISCONNECTED racing the handshake can't slip through the replay-0 events flow with no subscriber.
        watchForDrop(scope, gatt)
        _lastConnectedAt.value = System.currentTimeMillis()
        _connected.value = true
        aapsLogger.debug(LTag.PUMPCOMM, "bleSession: held link up reason=$reason")
    }

    /**
     * Flip [connected] false and release the link on an unexpected drop (out-of-range / patch sleep), so
     * the queue re-dials on its next command. Runs on the held session scope; the tear-down hops onto
     * [linkScope] so it survives that scope's own cancellation.
     */
    private fun watchForDrop(scope: CoroutineScope, gatt: BleTransportGattConnection) {
        // UNDISPATCHED so the flow subscription registers synchronously on the calling thread (like open()'s
        // CONNECTED wait) — the collector is live before this returns, closing the replay-0 lost-event window.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            gatt.events
                .filterIsInstance<GattEvent.ConnectionStateChanged>()
                .first { it.state == GattConnState.DISCONNECTED }
            aapsLogger.debug(LTag.PUMPCOMM, "bleSession: held link dropped")
            linkScope.launch { sessionMutex.withLock { tearDownHeld("dropped") } }
        }
    }

    /** Idempotent teardown of the held link. Caller holds [sessionMutex]. */
    private fun tearDownHeld(reason: String) {
        val link = heldLink ?: return
        _connected.value = false
        link.gatt.close()
        link.scope.cancel()
        lastCloseAtMs = System.currentTimeMillis()
        heldLink = null
        aapsLogger.debug(LTag.PUMPCOMM, "bleSession: held link closed reason=$reason")
    }

    /**
     * Run [block] against a [BleClient]. Reuses the queue-owned held link when one is up (no open/close —
     * the queue owns teardown); otherwise falls back to a [transientSession] (open→run→close). [ensureBond]
     * (pairing) always forces a fresh transient session.
     */
    private suspend fun <R> withSession(
        address: String,
        label: String,
        timeoutMs: Long = READ_TIMEOUT_MS,
        ensureBond: Boolean = false,
        block: suspend (BleClient) -> R
    ): R =
        sessionMutex.withLock {
            val link = heldLink
            if (link != null && !ensureBond) {
                aapsLogger.debug(LTag.PUMPCOMM, "bleSession: $label on held link")
                try {
                    withTimeout(timeoutMs.milliseconds) { block(link.client) }
                } catch (t: TimeoutCancellationException) {
                    // Op timed out on the held link → the link is suspect. Drop it so the next queue command
                    // re-dials a fresh GATT (restores the per-op self-heal the held link removed).
                    tearDownHeld("op timed out on held link: $label")
                    throw t
                } catch (c: CancellationException) {
                    throw c // caller cancelled the op (e.g. StopBolus) — the link is fine, leave it up
                } catch (t: Throwable) {
                    // GATT write/disconnect failure → the link is dead. Drop it so the next command re-dials.
                    tearDownHeld("op failed on held link: $label")
                    throw t
                }
            } else {
                // A transient session must be the ONLY link on the single-GATT/single-listener transport. If
                // a held link is somehow up (e.g. an ensureBond pairing while a prior link is still held),
                // close it first to avoid a second concurrent GATT (status-133) and a stolen listener slot.
                if (link != null) tearDownHeld("transient session needs the transport: $label")
                transientSession(address, label, timeoutMs, ensureBond, block)
            }
        }

    /**
     * Per-op session: open a fresh connection, run [block] against the [BleClient], and close. Used for
     * pre-activation / pairing / any op with no held link. Each call gets its own adapter+client+scope —
     * see the class KDoc for why (one-shot [BleTransportGattConnection.close]). [ensureBond] (pairing only)
     * creates the Android bond after CONNECTED, before discovery — the order the patch requires. Caller
     * holds [sessionMutex].
     */
    private suspend fun <R> transientSession(
        address: String,
        label: String,
        timeoutMs: Long,
        ensureBond: Boolean,
        block: suspend (BleClient) -> R
    ): R {
        awaitInterSessionSettle()
        // BluetoothAdapter.getRemoteDevice requires an UPPERCASE MAC (lowercase throws
        // IllegalArgumentException); the stored address is lowercase, so normalize here.
        val mac = address.uppercase()
        val scope = CoroutineScope(sessionDispatcher + SupervisorJob())
        val gatt = BleTransportGattConnection(transport, writeUuid, notifyUuid, scope)
        val client: BleClient = BleClientImpl(gatt, writeUuid, notifyUuid, scope)
        subscribeUnsolicited(scope, client)
        return try {
            // Bound the ENTIRE connect→discover→enable handshake, not just the CONNECTED wait: a lost
            // CCCD-write callback (which the transport documents as "surfaced by the caller's withTimeout")
            // would otherwise suspend forever HERE while holding sessionMutex, wedging every later
            // session op — including a delivery-critical out-of-band bolus cancel. On timeout the finally closes the
            // gatt (aborting the pending ack) and releases the mutex.
            val openTimeoutMs = if (ensureBond) CONNECT_TIMEOUT_MS + BOND_TIMEOUT_MS else CONNECT_TIMEOUT_MS
            withTimeout(openTimeoutMs.milliseconds) { open(gatt, mac, ensureBond) }
            // NB: a transient session does NOT touch _connected — that flag is the queue-owned held-link
            // signal (see [connected]). It updates lastConnectedAt only, since it is a real connection.
            _lastConnectedAt.value = System.currentTimeMillis()
            aapsLogger.debug(LTag.PUMPCOMM, "bleSession: reading $label")
            withTimeout(timeoutMs.milliseconds) { block(client) }
        } finally {
            gatt.close()
            scope.cancel()
            lastCloseAtMs = System.currentTimeMillis()
        }
    }

    /** Inter-session settle: let the patch fully release the previous link before the next dial (~1 s). */
    private suspend fun awaitInterSessionSettle() {
        val sinceCloseMs = System.currentTimeMillis() - lastCloseAtMs
        if (sinceCloseMs in 0 until INTER_SESSION_SETTLE_MS) delay((INTER_SESSION_SETTLE_MS - sinceCloseMs).milliseconds)
    }

    /**
     * Bridge patch-pushed frames (alarms, stop/basal-restart reports) to [unsolicitedHandler] for the life
     * of [scope]. Subscribed before open() so a frame during the handshake isn't missed; the scope's
     * cancellation tears this collector down with the session/link.
     */
    private fun subscribeUnsolicited(scope: CoroutineScope, client: BleClient) {
        scope.launch {
            client.unsolicitedEvents.collect { msg ->
                try {
                    unsolicitedHandler?.invoke(msg)
                } catch (t: Throwable) {
                    aapsLogger.error(LTag.PUMPCOMM, "unsolicited handler error", t)
                }
            }
        }
    }

    private suspend fun open(gatt: BleTransportGattConnection, address: String, ensureBond: Boolean = false) = coroutineScope {
        // Subscribe to the CONNECTED event BEFORE calling connect() so the state change cannot race
        // ahead of our collector. UNDISPATCHED runs the async body up to the flow subscription
        // synchronously, guaranteeing the subscription is live before connect() fires.
        val connected = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(CONNECT_TIMEOUT_MS.milliseconds) {
                gatt.events
                    .filterIsInstance<GattEvent.ConnectionStateChanged>()
                    .first { it.state == GattConnState.CONNECTED }
            }
        }
        aapsLogger.debug(LTag.PUMPCOMM, "bleSession: connecting to $address")
        require(gatt.connect(address)) { "bleSession: connect() refused for $address" }
        connected.await()
        if (ensureBond && !transport.adapter.isDeviceBonded(address)) {
            // The patch requires the bond be created right after STATE_CONNECTED; the transport exposes
            // no bond-state callback, so poll isDeviceBonded until the SMP completes.
            aapsLogger.debug(LTag.PUMPCOMM, "bleSession: creating bond")
            transport.adapter.createBond(address)
            withTimeout(BOND_TIMEOUT_MS.milliseconds) {
                while (!transport.adapter.isDeviceBonded(address)) delay(BOND_POLL_MS.milliseconds)
            }
            aapsLogger.debug(LTag.PUMPCOMM, "bleSession: bonded")
        }
        aapsLogger.debug(LTag.PUMPCOMM, "bleSession: connected; discovering services")
        gatt.discoverServices()
        gatt.enableNotifications(notifyUuid)
        aapsLogger.debug(LTag.PUMPCOMM, "bleSession: notifications enabled")
    }

    private companion object {

        const val CONNECT_TIMEOUT_MS = 20_000L
        const val READ_TIMEOUT_MS = 15_000L
        const val ADDITIONAL_PRIMING_TIMEOUT_MS = 60_000L

        // Safety check streams progress for ~100-210 s before the terminal frame; give it headroom.
        const val SAFETY_CHECK_TIMEOUT_MS = 250_000L

        // Three sequential basal-program writes on one connection; generous headroom over READ_TIMEOUT_MS.
        const val BASAL_PROGRAM_TIMEOUT_MS = 30_000L
        private const val RESULT_SUCCESS = 0

        // Pairing: MAC read + auth + up to 2 patch-info rounds (10 s each) + alarm + threshold;
        // bond wait is bounded separately in open().
        const val PAIRING_TIMEOUT_MS = 45_000L
        private const val PATCH_INFO_ROUND_RETRY_COUNT = 2
        private const val PATCH_INFO_ROUND_TIMEOUT_MS = 10_000L
        private const val BOND_TIMEOUT_MS = 15_000L
        private const val BOND_POLL_MS = 250L

        // Activation sends alert-alarm mode 0.
        private const val ALERT_ALARM_MODE_DEFAULT = 0

        // Minimum gap between one session's close and the next session's connect (patch-side settle).
        private const val INTER_SESSION_SETTLE_MS = 1000L
    }
}
