package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.clientcontrol.PendingAction
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.scenes.ClientControlSendResult
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.nssdk.localmodel.clientcontrol.AckEnvelope
import app.aaps.core.nssdk.localmodel.clientcontrol.AckPhase
import app.aaps.core.nssdk.localmodel.clientcontrol.AckStatus
import app.aaps.core.nssdk.localmodel.clientcontrol.BolusPreview
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientControlMessage
import app.aaps.core.nssdk.localmodel.clientcontrol.PrefEntry
import app.aaps.core.nssdk.localmodel.clientcontrol.WizardDetailDto
import app.aaps.core.nssdk.localmodel.clientcontrol.ProgressEnvelope
import app.aaps.core.nssdk.localmodel.clientcontrol.ProgressPhase
import app.aaps.core.nssdk.utils.ClientControlCrypto
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientControlRoundTrip.Companion.PROPAGATION_MARGIN_MS
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientControlRoundTrip.Companion.ROUND_TRIP_TTL_MS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Client-side round-trip coordinator: sends a command via [ClientControlPublisher.publishTracked] and
 * resolves the master's two-step [AckEnvelope] into an [ActionProgress] stream.
 *
 * Wait window is principled — derived from the command's [validUntil] ([ROUND_TRIP_TTL_MS]) plus
 * [PROPAGATION_MARGIN_MS] for the Done ack to travel back. If the master goes unreachable mid-wait we
 * short-circuit to [ActionProgress.Unconfirmed] rather than burning the whole timeout. ACKs arrive via
 * [onAckDoc] (WS push); a single [pollAck] is the last-chance fallback before declaring Unconfirmed.
 *
 * ACK events are buffered with replay so an ack landing in the narrow window between publish and the
 * collector starting is not lost; the per-counter filter discards anything not for the live request.
 */
@Singleton
class ClientControlRoundTrip @Inject constructor(
    private val publisher: ClientControlPublisher,
    private val pairingRepository: ClientPairingRepository,
    private val nsClientV3Plugin: Provider<NSClientV3Plugin>,
    private val nsClientRepository: NSClientRepository,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val notificationManager: NotificationManager,
    private val rh: ResourceHelper,
    private val bolusProgressData: BolusProgressData,
    private val aapsLogger: AAPSLogger,
    @ApplicationScope private val appScope: CoroutineScope
) : ClientControlActionDispatcher {

    companion object {

        // The command travels over a live WS to the master and back — a healthy round trip is
        // sub-second, so "fast or never": wait ~10 s total, then treat it as unconfirmed. Validity (8 s)
        // is shorter than the client's give-up (8 s + 2 s margin) so the master never applies a command
        // AFTER the client has stopped waiting; the 2 s margin (and pollAck) catch a last-moment ack.
        const val ROUND_TRIP_TTL_MS = 8_000L

        // A pump-direct commit (a TBR / extended-bolus SET or CANCEL) blocks the master's confirm() until the pump
        // actually enacts it. Unlike a bolus (whose DELIVERY can take minutes — hence its queue-ack + progress mirror),
        // a TBR/cancel is bounded by the master↔pump CONNECTION time (a BLE reconnect is at most ~1 min), and the real
        // enacted result (Ok/Failed) is worth waiting for. So these get a 1-min window; the master enforces the matching
        // validUntil, so it still never applies AFTER the client gives up.
        const val PUMP_ROUND_TRIP_TTL_MS = 60_000L
        const val PROPAGATION_MARGIN_MS = 2_000L
        const val PING_TTL_MS = 10_000L

        // Client progress-stall watchdog. The master streams ~1 progress frame/s while a bolus delivers,
        // so this much silence means the stream stalled (master arm-TTL, NS outage, WS drop) before a
        // terminal frame — the dialog would otherwise hang forever. A false trigger self-heals: the next
        // frame clears the flag (see BolusProgressData.updateProgress).
        const val PROGRESS_WATCHDOG_MS = 30_000L
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Single command in flight at a time: one shared ACK identifier per client, so two concurrent
    // round-trips would race on it. Set when a dispatch starts collecting, cleared in its finally
    // (including cancellation / "stop waiting").
    private val inFlight = AtomicBoolean(false)

    // replay = 2 covers the only race that needs it (the Executing + Done acks for the SINGLE in-flight command can
    // land between publish and collect). The per-counter filter in dispatch() is the real guard; a small replay keeps
    // stale acks from a prior command from lingering in the buffer.
    private val ackEvents = MutableSharedFlow<AckEnvelope>(replay = 2, extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // Drops a late/out-of-order progress frame (NS can re-deliver or reorder).
    @Volatile private var lastProgressTs = 0L

    // Client progress-stall watchdog job. Re-armed on every fresh in-flight frame, cancelled on a
    // terminal frame; if it elapses it flags the dialog stalled. Only ever touched from onProgressDoc,
    // which runs single-threaded on the Socket.IO callback thread, so cancel-then-relaunch is race-free.
    private var progressWatchdogJob: Job? = null

    init {
        // Client→master alarm-clear (one-directional, by design): when the user dismisses/mutes the relayed
        // bolus-delivery-failure alarm HERE, tell the master to clear its own copy. The reverse is deliberately NOT
        // done — the master silencing its alarm must not rob the remote initiator of its failed-bolus notice. Best-effort
        // fire-and-forget; app-lifetime scope (singleton, no cancel). Gated to clients so a master never signals itself.
        if (config.AAPSCLIENT) appScope.launch {
            var present = false
            notificationManager.notifications.collect { list ->
                val showing = list.any { it.id == NotificationId.BOLUS_DELIVERY_FAILED }
                if (present && !showing) {
                    val result = publisher.publish(ClientControlMessage.DismissAlarm)
                    nsClientRepository.addLog("► CLIENTCTL", "dismiss_alarm relay: ${result::class.simpleName}")
                }
                present = showing
            }
        }
    }

    /**
     * WS-push entry for a master ACK doc (`aaps_clientcontrol_ack_<clientId>`). Parses, checks it is
     * addressed to this client, verifies the signature against the paired master secret (a forged
     * "Ok" must not surface as Applied), and republishes to the in-process [ackEvents].
     */
    fun onAckDoc(doc: JSONObject) {
        val ackObj = doc.optJSONObject("ack") ?: return
        val ack = runCatching { json.decodeFromString<AckEnvelope>(ackObj.toString()) }.getOrNull() ?: run {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: malformed ACK doc")
            return
        }
        val clientId = pairingRepository.currentPairing()?.clientId ?: return
        if (ack.clientId != clientId) return // ack for a different client sharing this NS
        val secret = pairingRepository.secretBytesOrNull() ?: return
        if (!ClientControlCrypto.verifyAck(secret, ack)) {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: ACK signature invalid (counter=${ack.commandCounter})")
            return
        }
        // Any authenticated ACK (a ping pong, or any command result) proves the master is alive right
        // now — feed the liveness clock so masterReachable clears the offline banner without waiting
        // for the next devicestatus heartbeat.
        nsClientV3Plugin.get().bumpMasterSignal(dateUtil.now())
        nsClientRepository.addLog("◄ CLIENTCTL", "ack ${ack.phase}/${ack.status} counter=${ack.commandCounter}" + (ack.reason?.let { " ($it)" } ?: ""))
        // A late Delivery/Failed ack relays an async bolus failure the master only learned AFTER its Done ack —
        // raise an URGENT alarm here. It is NOT a round-trip response (the commit already terminated), so it does
        // not feed ackEvents; the master itself alarmed locally too (executor, phase 1a).
        if (ack.phase == AckPhase.Delivery) {
            if (ack.status == AckStatus.Failed)
                notificationManager.post(
                    NotificationId.BOLUS_DELIVERY_FAILED,
                    // payload is the master-authored full text ("title\n<pump detail>"); show it as-is, don't re-prefix the title.
                    ack.payload ?: rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror),
                    validMinutes = 0, soundRes = app.aaps.core.ui.R.raw.boluserror
                )
            return
        }
        ackEvents.tryEmit(ack)
    }

    /**
     * WS-push entry for a master→client bolus-progress frame. Verifies the signature (a forged "100% delivered"
     * must not be believable), drops a late/out-of-order frame, then drives the client's OWN [BolusProgressData]
     * so the existing (un-gated) progress dialog lights up — no client-specific UI.
     */
    fun onProgressDoc(doc: JSONObject) {
        val obj = doc.optJSONObject("progress") ?: return
        val env = runCatching { json.decodeFromString<ProgressEnvelope>(obj.toString()) }.getOrNull() ?: run {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: malformed progress doc")
            return
        }
        val clientId = pairingRepository.currentPairing()?.clientId ?: return
        if (env.clientId != clientId) return // progress for a different client sharing this NS
        val secret = pairingRepository.secretBytesOrNull() ?: return
        if (!ClientControlCrypto.verifyProgress(secret, env)) {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: progress signature invalid")
            return
        }
        nsClientV3Plugin.get().bumpMasterSignal(dateUtil.now())
        if (env.timestamp <= lastProgressTs) return // stale / out-of-order
        lastProgressTs = env.timestamp
        when (env.phase) {
            ProgressPhase.Active   -> {
                if (bolusProgressData.state.value == null) bolusProgressData.start(env.insulin, isSMB = false)
                bolusProgressData.updateProgress(env.percent, env.status, PumpInsulin(env.delivered))
                bolusProgressData.enableStopDelivery(env.stopDeliveryEnabled)
                armProgressWatchdog()
            }

            ProgressPhase.Complete -> { cancelProgressWatchdog(); bolusProgressData.completeAndAutoClear() }
            ProgressPhase.Cleared  -> { cancelProgressWatchdog(); bolusProgressData.clear() }
        }
    }

    /**
     * (Re)start the progress-stall watchdog after a fresh in-flight frame. If [PROGRESS_WATCHDOG_MS]
     * passes with no further frame (the master stopped mirroring, or NS comms dropped) the dialog is
     * flagged stalled so it can offer a manual dismiss instead of hanging on a terminal frame that
     * will never arrive. Re-arming cancels the previous timer, so a steady stream never trips it.
     */
    private fun armProgressWatchdog() {
        // Capture the generation now so a late fire can't flag a newer bolus (markStalled re-checks it).
        val expectedGeneration = bolusProgressData.currentGeneration
        progressWatchdogJob?.cancel()
        progressWatchdogJob = appScope.launch {
            delay(PROGRESS_WATCHDOG_MS)
            aapsLogger.debug(LTag.NSCLIENT, "ClientControl: progress watchdog fired — marking bolus dialog stalled")
            bolusProgressData.markStalled(expectedGeneration)
        }
    }

    /** Cancel the watchdog on a terminal frame — nothing more to wait for. */
    private fun cancelProgressWatchdog() {
        progressWatchdogJob?.cancel()
        progressWatchdogJob = null
    }

    /** Fire-and-forget: publish a StopBolus to the master to abort the bolus being mirrored here. Client-only. */
    override fun stopBolus() {
        if (!config.AAPSCLIENT) return
        appScope.launch { publisher.publish(ClientControlMessage.StopBolus) }
    }

    /**
     * Fire a liveness probe. Fire-and-forget: the pong arrives via [onAckDoc], which bumps the
     * liveness clock. No-op if unpaired (publishTracked returns NotPaired).
     */
    suspend fun sendPing() {
        publisher.publishTracked(ClientControlMessage.Ping, dateUtil.now() + PING_TTL_MS, wantsAck = true)
    }

    // The single app-level modal signal (progress + label), fed by every run()/execute() regardless of
    // feature. Hosted once in ComposeMainActivity; round-trips are single-in-flight so at most one shows.
    private val _pending = MutableStateFlow<PendingAction?>(null)
    override val pendingAction: StateFlow<PendingAction?> = _pending.asStateFlow()

    // The currently running run()'s job, so dismissActionProgress() can "stop waiting" on it. AtomicReference so a
    // dismiss tap can't race the assignment in run(): getAndSet atomically takes the job and cancels it exactly once.
    private val currentRun = AtomicReference<Job?>(null)

    override fun dismissActionProgress() {
        _pending.value = null
        currentRun.getAndSet(null)?.cancel()
    }

    override suspend fun execute(command: ClientControlActionDispatcher.Command, label: String, localExecute: suspend () -> ActionProgress): ActionProgress =
        if (config.AAPSCLIENT) run(command, label)
        else {
            // Master: execute locally (instant). Success is silent; a failure surfaces on the SAME modal.
            val result = localExecute()
            if (result !is ActionProgress.Applied) _pending.value = PendingAction(result, label)
            result
        }

    /**
     * Client-side: run [command] over the round-trip, driving the single app-level modal ([pendingAction])
     * with [label] for the whole lifecycle and returning the terminal for the caller's follow-up. On
     * [ActionProgress.Applied] the modal is held a beat then cleared; Rejected/Unconfirmed stay until
     * dismissed. A dismiss ("stop waiting") cancels this run.
     */
    override suspend fun run(command: ClientControlActionDispatcher.Command, label: String): ActionProgress {
        val shownAt = dateUtil.now()
        var terminal: ActionProgress = ActionProgress.Unconfirmed(FailureReason.NoReply)
        try {
            coroutineScope {
                currentRun.set(coroutineContext[Job])
                dispatch(command).collect { p ->
                    terminal = p
                    // Applied is cleared after the loop (with the min-visible hold); show the rest live.
                    if (p !is ActionProgress.Applied && p !is ActionProgress.Prepared) _pending.value = PendingAction(p, label)
                }
            }
            // No ack came back (timeout / connection lost) → we don't actually know the master's state.
            // Flip offline so the app-level probe pings + re-pulls and the real result reconciles, rather
            // than leaving a stale optimistic guess. Self-heals on the next pong/heartbeat.
            if (terminal is ActionProgress.Unconfirmed) nsClientV3Plugin.get().markMasterUnreachable()
            if (terminal is ActionProgress.Applied || terminal is ActionProgress.Prepared) {
                val visibleMs = dateUtil.now() - shownAt
                if (visibleMs < ClientControlActionDispatcher.MIN_MODAL_VISIBLE_MS)
                    delay(ClientControlActionDispatcher.MIN_MODAL_VISIBLE_MS - visibleMs)
                _pending.value = null
            }
        } finally {
            currentRun.set(null)
            // Cancelled mid-wait (dismiss / scope teardown) — don't leave a stuck spinner.
            val current = _pending.value?.progress
            if (current is ActionProgress.Sending || current is ActionProgress.MasterExecuting)
                _pending.value = null
        }
        return terminal
    }

    override fun dispatch(command: ClientControlActionDispatcher.Command): Flow<ActionProgress> = channelFlow {
        if (!inFlight.compareAndSet(false, true)) {
            send(ActionProgress.Rejected(FailureReason.Busy))
            return@channelFlow
        }
        try {
            send(ActionProgress.Sending)
            val message = when (command) {
                is ClientControlActionDispatcher.Command.PreferenceEdit ->
                    ClientControlMessage.PreferencesUpdate(command.prefs.mapValues { (_, v) -> PrefEntry(value = v.first, lastModified = v.second) })

                is ClientControlActionDispatcher.Command.ScenePrepare   -> ClientControlMessage.ScenePrepare(command.sceneId, command.durationMinutes)
                is ClientControlActionDispatcher.Command.SceneCommit    -> ClientControlMessage.SceneCommit(command.bolusId)
                is ClientControlActionDispatcher.Command.SceneStop      -> ClientControlMessage.SceneStop(command.triggerChain)
                is ClientControlActionDispatcher.Command.BolusPrepare   -> ClientControlMessage.BolusPrepare(command.guid)
                is ClientControlActionDispatcher.Command.BolusCommit    -> ClientControlMessage.BolusCommit(command.bolusId, command.asAdvisor, command.correctionU)
                is ClientControlActionDispatcher.Command.WizardPrepare  -> with(command.inputs) {
                    ClientControlMessage.WizardPrepare(bg, carbs, percentage, directCorrection, carbTime, useBg, useCob, useIob, useTt, useTrend, alarm, notes, eCarbsGrams, eCarbsDelayMinutes, eCarbsDurationHours, profileName)
                }

                is ClientControlActionDispatcher.Command.BatchPrepare   -> ClientControlMessage.BatchPrepare(command.actions.map { it.toDto() })
            }
            // A pump-direct commit waits the longer window (the master blocks on the pump); everything else is fast-or-never.
            val ttl = if (command is ClientControlActionDispatcher.Command.BolusCommit && command.pumpDirect) PUMP_ROUND_TRIP_TTL_MS else ROUND_TRIP_TTL_MS
            val validUntil = dateUtil.now() + ttl
            val tracked = publisher.publishTracked(message, validUntil)
            val counter = when (val r = tracked.result) {
                is ClientControlSendResult.NotPaired     -> {
                    send(ActionProgress.Rejected(FailureReason.NotPaired)); return@channelFlow
                }

                is ClientControlSendResult.PublishFailed -> {
                    send(ActionProgress.Rejected(FailureReason.SendFailed, r.reason)); return@channelFlow
                }

                is ClientControlSendResult.Success       -> tracked.counter
            }
            if (counter == null) {
                send(ActionProgress.Rejected(FailureReason.Internal)); return@channelFlow
            }

            val plugin = nsClientV3Plugin.get()
            val terminal = CompletableDeferred<ActionProgress>()

            val ackJob = launch {
                ackEvents.filter { it.commandCounter == counter }.collect { ack ->
                    when (ack.phase) {
                        AckPhase.Executing -> if (ack.status == AckStatus.Pending) send(ActionProgress.MasterExecuting)
                        AckPhase.Done      -> doneOutcome(ack)?.let { terminal.complete(it) }
                        AckPhase.Delivery  -> Unit // late async-failure relay; handled out-of-band in onAckDoc, never emitted into ackEvents
                    }
                }
            }
            // React only to a transition to unreachable (drop the current value). If it was already
            // unreachable the timeout/poll path covers it.
            val reachJob = launch {
                plugin.masterReachable.drop(1).filter { !it }.collect {
                    terminal.complete(ActionProgress.Unconfirmed(FailureReason.NotReachable))
                }
            }

            val waitMs = (validUntil + PROPAGATION_MARGIN_MS - dateUtil.now()).coerceAtLeast(0L)
            val result = withTimeoutOrNull(waitMs) { terminal.await() }
                ?: pollAck(counter)
                ?: ActionProgress.Unconfirmed(FailureReason.NoReply)
            ackJob.cancel()
            reachJob.cancel()
            send(result)
        } finally {
            inFlight.set(false)
        }
    }

    private fun doneOutcome(ack: AckEnvelope): ActionProgress? = when (ack.status) {
        // A BolusPrepare ack carries the signed BolusPreview payload → Prepared (hand off to the confirm
        // dialog); every other Ok is a plain Applied (payload PRESENCE disambiguates prepare from commit/scene/TT).
        AckStatus.Ok      -> ack.payload?.let { payloadToPrepared(it) } ?: ActionProgress.Applied
        // ack.reason carries the FailureReason code NAME (set master-side); map it back, Unknown if newer.
        // ack.payload carries the master's specific detail (e.g. "no BG") for a BolusComputeFailed.
        AckStatus.Failed  -> ActionProgress.Rejected(ack.reason.toFailureReason(), ack.payload)
        AckStatus.Expired -> ActionProgress.Rejected(FailureReason.Expired)
        AckStatus.Pending -> null // not terminal
    }

    /** Parse the signed [BolusPreview] payload of a BolusPrepare ack into an [ActionProgress.Prepared]. */
    private fun payloadToPrepared(payload: String): ActionProgress {
        val preview = runCatching { json.decodeFromString<BolusPreview>(payload) }.getOrNull()
            ?: return ActionProgress.Rejected(FailureReason.Internal)
        return ActionProgress.Prepared(
            id = preview.bolusId,
            lines = preview.lines.map { ConfirmationLine(roleOf(it.role), it.text) },
            advisorApplies = preview.advisorApplies,
            advisorLines = preview.advisorLines.map { ConfirmationLine(roleOf(it.role), it.text) },
            wizardDetail = preview.wizardDetail?.toDomain(),
        )
    }

    private fun roleOf(name: String): ConfirmationRole =
        ConfirmationRole.entries.firstOrNull { it.name == name } ?: ConfirmationRole.NORMAL

    private fun String?.toFailureReason(): FailureReason =
        FailureReason.entries.firstOrNull { it.name == this } ?: FailureReason.Unknown

    /** Last-chance single read of the ACK doc before declaring Unconfirmed (covers a WS-missed ack). */
    private suspend fun pollAck(counter: Long): ActionProgress? {
        val client = nsClientV3Plugin.get().nsAndroidClient ?: return null
        val clientId = pairingRepository.currentPairing()?.clientId ?: return null
        val identifier = ClientControlPublisher.IDENTIFIER_ACK_PREFIX + clientId
        val doc = runCatching { client.getSettings(identifier) }.getOrNull()?.values ?: return null
        val ackObj = doc.optJSONObject("ack") ?: return null
        val ack = runCatching { json.decodeFromString<AckEnvelope>(ackObj.toString()) }.getOrNull() ?: return null
        if (ack.clientId != clientId || ack.commandCounter != counter || ack.phase != AckPhase.Done) return null
        val secret = pairingRepository.secretBytesOrNull() ?: return null
        if (!ClientControlCrypto.verifyAck(secret, ack)) return null
        return doneOutcome(ack)
    }
}

private fun WizardDetailDto.toDomain() = EventData.WizardDetail(
    totalInsulin = totalInsulin,
    unclampedInsulin = unclampedInsulin,
    carbs = carbs,
    insulinFromBG = insulinFromBG,
    insulinFromTrend = insulinFromTrend,
    insulinFromCOB = insulinFromCOB,
    insulinFromCarbs = insulinFromCarbs,
    insulinFromBolusIOB = insulinFromBolusIOB,
    insulinFromBasalIOB = insulinFromBasalIOB,
    includeBolusIOB = includeBolusIOB,
    includeBasalIOB = includeBasalIOB,
    percentageCorrection = percentageCorrection,
    cob = cob,
    tempTargetLabel = tempTargetLabel,
    ic = ic,
    sens = sens,
    eCarbsGrams = eCarbsGrams,
    eCarbsDelayMinutes = eCarbsDelayMinutes,
    eCarbsDurationHours = eCarbsDurationHours,
    carbTimeMinutes = carbTimeMinutes,
    alarm = alarm,
    maxBolus = maxBolus,
    bolusStep = bolusStep,
)
