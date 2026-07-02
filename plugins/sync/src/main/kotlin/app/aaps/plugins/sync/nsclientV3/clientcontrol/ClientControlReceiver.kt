package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey
import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey
import app.aaps.core.keys.interfaces.IntNonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.clientcontrol.AckEnvelope
import app.aaps.core.nssdk.localmodel.clientcontrol.AckPhase
import app.aaps.core.nssdk.localmodel.clientcontrol.AckStatus
import app.aaps.core.nssdk.localmodel.clientcontrol.AuthorizedClient
import app.aaps.core.nssdk.localmodel.clientcontrol.BolusPreview
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientControlMessage
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientState
import app.aaps.core.nssdk.localmodel.clientcontrol.ConfirmationLineDto
import app.aaps.core.nssdk.localmodel.clientcontrol.WizardDetailDto
import app.aaps.core.nssdk.localmodel.clientcontrol.ProgressEnvelope
import app.aaps.core.nssdk.localmodel.clientcontrol.ProgressPhase
import app.aaps.core.nssdk.localmodel.clientcontrol.SignedEnvelope
import app.aaps.core.nssdk.utils.ClientControlCrypto
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.services.RunningConfigurationPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Master-side receiver for inbound client-control envelopes.
 *
 * Two entry points feed the same verify-and-ack pipeline:
 * - [onSettingsDocChanged] — primary, low-latency. Called from the WS create/update
 *   listener with the doc already in hand.
 * - [processPending] — polling fallback. Probes the deterministic hello + cmd
 *   identifiers per authorized client to catch anything WS missed during a
 *   disconnect window.
 *
 * Replay protection sits between parse and dispatch, in this order:
 * 1. HMAC signature against the stored secret for the envelope's clientId
 *    (only check that proves authenticity)
 * 2. counter strictly greater than `counterReceived` for that client (monotonic)
 * 3. timestamp within ±5 min of master clock (skew window)
 *
 * Sig-first means a failure log unambiguously means "forgery / wrong secret"
 * rather than being shadowed by a benign replay log on a forged-but-stale message.
 *
 * Behaviour on failure — nothing is ever deleted:
 * - Hopeless garbage (no envelope field, malformed JSON, unknown clientId) → IGNORE and
 *   leave the doc in place. Deleting would be actively harmful: NS soft-deletes (tombstones)
 *   the identifier, so a later legitimate PUT to that same per-type slot gets HTTP 410 — and in
 *   a multi-device setup another master may legitimately own that command. Stray docs simply
 *   linger until NS auto-prunes them.
 * - Sig / counter / skew failures → likewise leave the doc in place. The next tick (poll or
 *   WS update) re-verifies. Counter regression in particular often just means "we already
 *   processed this" — harmless because the counter check still gates state mutation.
 *
 * On verified envelope: dispatch by [SignedEnvelope.type]; the doc is not deleted afterwards
 * either. Unknown types still advance the counter (the secret-holder sent this intentionally,
 * an older master version just doesn't know what to do with it). Replay is gated solely by the
 * persistent per-client counter — see the rationale comments in [verifyAndAck].
 */
@Singleton
class ClientControlReceiver @Inject constructor(
    private val authorizedRepository: AuthorizedClientsRepository,
    private val nsClientV3Plugin: Provider<NSClientV3Plugin>,
    private val nsClientRepository: NSClientRepository,
    private val sceneAutomationApi: SceneAutomationApi,
    private val offerPublisher: PairingOfferPublisher,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val uel: UserEntryLogger,
    private val runningConfigurationPublisher: RunningConfigurationPublisher,
    private val persistenceLayer: PersistenceLayer,
    private val wizardBolusExecutor: WizardBolusExecutor,
    private val notificationManager: NotificationManager,
    config: Config,
    private val bolusProgressData: BolusProgressData,
    private val commandQueue: CommandQueue,
    private val aapsLogger: AAPSLogger,
    @ApplicationScope private val appScope: CoroutineScope
) {

    private val json = Json { ignoreUnknownKeys = true }

    // Serialize the whole verify→dispatch→counter-bump pipeline. The replay gate reads counterReceived and the
    // handler advances it (bumpLastSeen) — a check-then-act gap: two coroutines handling the SAME doc concurrently
    // (a WS re-notify, or WS+poll overlap) could both pass the gate and both execute → double bolus / double record.
    // One command in flight at a time closes that window (the executor's atomic slot is the second line of defense).
    private val commandMutex = Mutex()

    // Phase 3 — client bolus-progress mirror (master side). The currently-armed client (the one whose bolus is
    // delivering); set in onVerifiedBolusCommit (the only place the clientId is known), cleared on terminal /
    // non-delivery. The arm-TTL bounds a stale arm (e.g. a record-only commit that never starts a pump bolus) —
    // but ONLY in the pre-delivery window: once the first in-flight frame arrives we latch [progressDelivering]
    // and mirror until the terminal frame regardless of wall-clock. Otherwise a long bolus (e.g. 1.6U on Omnipod
    // ≈ 70s, > the 60s TTL) would have its mirror cut off mid-delivery, the terminal Complete frame would never
    // be written, and the client's progress dialog would freeze forever (no client-side watchdog).
    private val progressClientId = AtomicReference<String?>(null)
    @Volatile private var progressArmedAt = 0L
    @Volatile private var progressDelivering = false
    // Generation of bolusProgressData captured at arm time. Only a bolus that start()s a NEWER generation (the
    // client's own, queued AFTER this commit) is mirrored — so a bolus already running on the master when the
    // client commits (the client's was queue-rejected) is never mis-attributed to the client's progress dialog.
    @Volatile private var progressArmedGeneration = 0L
    private val progressSampleMs = 1_000L
    private val progressArmTtlMs = 60_000L
    // Liveness heartbeat: some pump drivers (Medtronic, Omnipod Eros) deliver a bolus as one blocking call and
    // emit NO intermediate progress. Re-publishing the current frame on this cadence keeps the client's stall
    // watchdog from false-firing on a healthy long bolus; only a real relay/connection outage produces silence.
    // It is a lifecycle-bound job — started ONLY while a client bolus is delivering and cancelled on the terminal
    // frame — so when idle (no armed bolus) there is no scheduled delay to leak. Touched only from the single
    // mirror collector coroutine.
    private val progressHeartbeatMs = 10_000L
    @Volatile private var progressHeartbeatJob: Job? = null

    init {
        // Master only: mirror the live progress of a CLIENT-initiated bolus back to that client so its (un-gated)
        // progress dialog lights up. Throttled to ~1/s; start + terminal frames sent promptly. The command queue
        // serializes delivery (one bolus at a time), so the armed clientId stays valid for the whole lifecycle.
        if (!config.AAPSCLIENT) appScope.launch {
            var prev: BolusProgressState? = null
            var lastWrite = 0L
            bolusProgressData.state.collect { st ->
                val previous = prev // snapshot: prev is a mutated captured var, so it can't smart-cast in the when
                val clientId = progressClientId.get()
                // TTL guards only the PRE-delivery window (a commit that never starts a pump bolus). Once delivery
                // has started (progressDelivering) we keep mirroring until the terminal frame regardless of elapsed
                // time, so long boluses don't lose their Complete/Cleared frame and freeze the client dialog.
                val armedFresh = clientId != null && (progressDelivering || dateUtil.now() - progressArmedAt < progressArmTtlMs)
                // Only the client's OWN bolus: it start()s a generation NEWER than the one captured at arm. A bolus
                // already running when the client committed keeps the armed generation → never mirrored to the client.
                val ownBolus = bolusProgressData.currentGeneration > progressArmedGeneration
                if (clientId != null && armedFresh && ownBolus && st?.isSMB != true) {
                    when {
                        st == null && previous != null  -> { // ended before 100 (pump failure / cancel)
                            writeProgress(clientId, ProgressPhase.Cleared, previous)
                            progressClientId.set(null)
                            progressDelivering = false
                            stopProgressHeartbeat()
                        }

                        st != null && st.percent >= 100 -> { // delivered
                            writeProgress(clientId, ProgressPhase.Complete, st)
                            progressClientId.set(null)
                            progressDelivering = false
                            stopProgressHeartbeat()
                        }

                        st != null                      -> { // in flight: send start immediately, then throttle
                            progressDelivering = true
                            startProgressHeartbeat()
                            val nowMs = dateUtil.now()
                            if (previous == null || nowMs - lastWrite >= progressSampleMs) {
                                writeProgress(clientId, ProgressPhase.Active, st)
                                lastWrite = nowMs
                            }
                        }
                    }
                }
                prev = st
            }
        }
    }

    /**
     * Start the liveness heartbeat (idempotent) for the in-flight client bolus: re-publish the current frame every
     * [progressHeartbeatMs] so a non-streaming pump (Medtronic/Eros, which emits no intermediate progress) still
     * keeps the client's stall watchdog fed. Cancelled on the terminal frame by [stopProgressHeartbeat]. Started/
     * stopped only from the single mirror collector coroutine, so no extra synchronization is needed.
     */
    private fun startProgressHeartbeat() {
        if (progressHeartbeatJob?.isActive == true) return
        progressHeartbeatJob = appScope.launch {
            while (true) {
                delay(progressHeartbeatMs)
                val clientId = progressClientId.get() ?: continue
                val st = bolusProgressData.state.value ?: continue
                // Re-check the live state right before writing so a heartbeat that wins a race with a terminal
                // frame doesn't re-publish an Active after Complete/Cleared.
                if (progressDelivering && !st.isSMB && st.percent < 100 &&
                    bolusProgressData.currentGeneration > progressArmedGeneration
                ) writeProgress(clientId, ProgressPhase.Active, st)
            }
        }
    }

    /** Cancel the heartbeat on a terminal frame — nothing left to keep alive. */
    private fun stopProgressHeartbeat() {
        progressHeartbeatJob?.cancel()
        progressHeartbeatJob = null
    }

    /**
     * Polling fallback. Lists NS settings, filters to client-control identifiers, and
     * dispatches each through [verifyAndAck]. Safe to call repeatedly — the per-client counter
     * check rejects anything already accepted; docs are not deleted and simply linger until the
     * next command overwrites the slot or NS auto-prunes them.
     *
     * Using `searchSettings` instead of probing per-identifier means we don't need to know
     * in advance which clients exist or which command types they're using. New variants
     * land here with no polling-loop changes.
     */
    suspend fun processPending() {
        val client = nsClientV3Plugin.get().nsAndroidClient ?: return
        val now = dateUtil.now()
        val resp = runCatching { client.searchSettings(limit = 100) }.getOrNull() ?: return
        for (doc in resp.values) {
            val identifier = doc.optString("identifier")
            if (!identifier.startsWith(ClientControlPublisher.IDENTIFIER_PREFIX)) continue
            // Skip our own pairing offers — they share the prefix but are not signed envelopes,
            // so verifyAndAck would (harmlessly) log them as "malformed envelope, ignoring". We
            // skip to avoid that noise and a needless re-parse on every poll tick.
            if (identifier.startsWith(ClientControlPublisher.IDENTIFIER_OFFER_PREFIX)) continue
            // Skip our own command ACKs (master-written, for clients). Same reason as offers.
            if (identifier.startsWith(ClientControlPublisher.IDENTIFIER_ACK_PREFIX)) continue
            // Skip our own bolus-progress docs (master-written, for clients).
            if (identifier.startsWith(ClientControlPublisher.IDENTIFIER_PROGRESS_PREFIX)) continue
            verifyAndAck(identifier, doc, now)
        }
    }

    /**
     * WS-push entry. NSClientV3Service routes settings-collection create/update events
     * to here for any identifier with the [ClientControlPublisher.IDENTIFIER_PREFIX].
     */
    suspend fun onSettingsDocChanged(identifier: String, doc: JSONObject) {
        if (!identifier.startsWith(ClientControlPublisher.IDENTIFIER_PREFIX)) return
        if (identifier.startsWith(ClientControlPublisher.IDENTIFIER_OFFER_PREFIX)) return
        // Command ACKs are master-written, consumed by clients — never an inbound command here.
        if (identifier.startsWith(ClientControlPublisher.IDENTIFIER_ACK_PREFIX)) return
        // Bolus-progress docs are master-written, consumed by clients — never an inbound command here.
        if (identifier.startsWith(ClientControlPublisher.IDENTIFIER_PROGRESS_PREFIX)) return
        verifyAndAck(identifier, doc, dateUtil.now())
    }

    private suspend fun verifyAndAck(identifier: String, doc: JSONObject, now: Long): Unit = commandMutex.withLock {
        val client = nsClientV3Plugin.get().nsAndroidClient ?: return
        // Unrecognized / unverifiable docs are IGNORED, never deleted. A delete soft-deletes
        // (tombstones) the identifier on NS, so the next legitimate PUT to that same per-type slot
        // returns HTTP 410 forever. Worse, in a multi-device setup (several clients, or another
        // master that doesn't have this client paired) the WS echo makes one instance delete a
        // command another instance legitimately owns. Replay is already prevented by the per-client
        // counter; stray/garbage docs simply linger until NS auto-prunes them.
        val envelopeObj = doc.optJSONObject("envelope") ?: run {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: $identifier has no envelope field, ignoring")
            return
        }
        val envelope = runCatching { json.decodeFromString<SignedEnvelope>(envelopeObj.toString()) }.getOrNull()
        if (envelope == null) {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: $identifier malformed envelope, ignoring")
            return
        }
        // Resolve the authorized client by the envelope's clientId — never trust the
        // identifier suffix, since any paired client could write to any identifier.
        // findRaw is captured *before* current(now) so the pre-prune state is available for
        // the diagnostic branch below — current(now) silently drops expired pendings, which
        // would otherwise make a scraped-expired-QR replay indistinguishable from a typo.
        val raw = authorizedRepository.findRaw(envelope.clientId)
        val entry = authorizedRepository.current(now).firstOrNull { it.clientId == envelope.clientId }
        if (entry == null) {
            if (raw != null && raw.state == ClientState.Pending && raw.pairExpiresAt <= now)
                aapsLogger.error(LTag.NSCLIENT, "ClientControl: $identifier pairing window expired for clientId=${envelope.clientId}, ignoring")
            else
                aapsLogger.error(LTag.NSCLIENT, "ClientControl: $identifier unknown clientId=${envelope.clientId}, ignoring")
            // See the no-delete rationale above: never tombstone a slot we don't own.
            return
        }
        val lookup = authorizedRepository.secretLookup(entry.clientId) ?: return
        if (!ClientControlCrypto.verifyEnvelope(lookup.secretBytes, envelope)) {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: $identifier signature invalid")
            return
        }
        if (envelope.counter <= lookup.counterReceived) {
            aapsLogger.error(
                LTag.NSCLIENT,
                "ClientControl: $identifier replay (counter=${envelope.counter} <= seen=${lookup.counterReceived}); leaving doc for diagnostics"
            )
            return
        }
        if (!ClientControlCrypto.timestampWithinSkew(envelope.timestamp, now)) {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: $identifier timestamp outside skew window")
            return
        }
        // Hard expiry: a command that sat past its validity must NOT fire late (e.g. a profile switch
        // requested 15 min ago and only now picked up). Consume the counter so it can't replay, and
        // tell the client definitively rather than leaving it to time out.
        if (now > envelope.validUntil) {
            authorizedRepository.bumpLastSeen(entry.clientId, envelope.counter, now)
            nsClientRepository.addLog("◄ CLIENTCTL", "expired ${envelope.type} from ${entry.name}")
            aapsLogger.warn(LTag.NSCLIENT, "ClientControl: $identifier expired (validUntil=${envelope.validUntil} < now=$now), not executing")
            if (envelope.wantsAck) writeAck(client, lookup.secretBytes, envelope.clientId, envelope.counter, AckPhase.Done, AckStatus.Expired, "expired before master applied it", null, now)
            return
        }
        // Decode the payload into the sealed message family. A failure here means the verified
        // sender wrote a payload this master cannot interpret — older master vs newer client,
        // typically. Treat it the same as an unknown type: advance the counter so the next
        // envelope can be processed.
        val message = runCatching { json.decodeFromString(ClientControlMessage.serializer(), envelope.payload) }.getOrNull()
        // Master policy gate: when client control is OFF, reject mutating commands cleanly (signed Done/Failed ACK)
        // instead of silently dropping them — a silent drop times the client out → Unconfirmed → a false "master
        // offline" alarm in the toggle-propagation race window. Exempts Hello (pairing must still work while disabled)
        // and Ping (a liveness probe). Placed BEFORE the Hello branch so that branch stays adjacent to (and keeps
        // exhaustive) the when(message) below. Covers the WS push AND the poll fallback; consume the counter like expiry.
        if (message !is ClientControlMessage.Hello && message !is ClientControlMessage.Ping && !preferences.get(BooleanKey.NsClientAllowClientControl)) {
            authorizedRepository.bumpLastSeen(entry.clientId, envelope.counter, now)
            nsClientRepository.addLog("◄ CLIENTCTL", "control disabled, rejecting ${envelope.type} from ${entry.name}")
            aapsLogger.warn(LTag.NSCLIENT, "ClientControl: $identifier rejected — client control disabled on master")
            if (envelope.wantsAck) writeAck(client, lookup.secretBytes, envelope.clientId, envelope.counter, AckPhase.Done, AckStatus.Failed, FailureReason.ControlDisabled.name, null, now)
            return
        }
        // Hello is the pairing handshake, not a user action — no ACK round-trip. Its handling
        // includes the Pending → Active transition + offer-doc cleanup (suspends on deleteOffer).
        if (message is ClientControlMessage.Hello) {
            onVerifiedHello(entry, envelope, now)
            return
        }
        // Round-trip commands (wantsAck) get the two-step ACK: Executing before dispatch, then the
        // result. Fire-and-forget commands (scenes, pref sync) skip it — no client is waiting, so the
        // ACK doc would be pure write amplification.
        if (envelope.wantsAck) writeAck(client, lookup.secretBytes, envelope.clientId, envelope.counter, AckPhase.Executing, AckStatus.Pending, null, null, now)
        // Advance the replay counter ONCE here — post-gate, pre-dispatch — so a command can never be re-executed even
        // if a handler throws or early-returns before its own bump. The whole pipeline is inside commandMutex, so this
        // just makes the protection order explicit; the per-handler bumpLastSeen calls become idempotent no-ops (same
        // counter). (Hello is handled above via markActive; the expiry branch bumps + returns before reaching here.)
        authorizedRepository.bumpLastSeen(entry.clientId, envelope.counter, now)
        val outcome = when (message) {
            null                                      -> {
                onVerifiedUndecodablePayload(entry, envelope, now)
                AckOutcome(AckStatus.Failed, FailureReason.ExecutionFailed.name)
            }

            ClientControlMessage.Ping                 -> onVerifiedPing(entry, envelope, now)
            is ClientControlMessage.ScenePrepare      -> onVerifiedScenePrepare(entry, envelope, message, now)
            is ClientControlMessage.SceneCommit       -> onVerifiedSceneCommit(entry, envelope, message, now)
            is ClientControlMessage.SceneStop         -> onVerifiedSceneStop(entry, envelope, message, now)
            is ClientControlMessage.PreferencesUpdate -> onVerifiedPreferencesUpdate(entry, envelope, message, now)
            is ClientControlMessage.BolusPrepare      -> onVerifiedBolusPrepare(entry, envelope, message, now)
            is ClientControlMessage.BolusCommit       -> onVerifiedBolusCommit(entry, envelope, message, now)
            is ClientControlMessage.WizardPrepare     -> onVerifiedWizardPrepare(entry, envelope, message, now)
            is ClientControlMessage.BatchPrepare      -> onVerifiedBatchPrepare(entry, envelope, message, now)
            ClientControlMessage.DismissAlarm         -> onVerifiedDismissAlarm(entry, envelope, now)
            ClientControlMessage.StopBolus            -> onVerifiedStopBolus(entry, envelope, now)
        }
        if (envelope.wantsAck) writeAck(client, lookup.secretBytes, envelope.clientId, envelope.counter, AckPhase.Done, outcome.status, outcome.reason, outcome.payload, now)
        // Don't deleteSettings here (nor on any error path above). NS soft-deletes (tombstones)
        // the identifier; the next legitimate same-type command from the same client would PUT to
        // that identifier and hit HTTP 410. Counter dedup (line above) prevents replay; the doc
        // just lingers in the slot until the next overwrite or NS auto-prune.
    }

    private suspend fun onVerifiedHello(entry: AuthorizedClient, envelope: SignedEnvelope, now: Long) {
        when (entry.state) {
            ClientState.Pending -> {
                authorizedRepository.markActive(entry.clientId, envelope.counter, now)
                // Pair-complete: drop the wrapped offer doc so the brute-force window closes the moment
                // the secret is no longer needed for fetching. Without this, the offer would linger up
                // to pairExpiresAt giving an attacker the full window even though pairing already
                // succeeded — directly contradicting the security model in ClientControlPairingCrypto.
                offerPublisher.deleteOffer(entry.clientId)
            }

            ClientState.Active  -> authorizedRepository.bumpLastSeen(entry.clientId, envelope.counter, now)
        }
        nsClientRepository.addLog("◄ CLIENTCTL", "hello accepted for ${entry.name} (${entry.clientId})")
    }

    /** Liveness probe: nothing to do but advance the counter and ack Ok — the ack is the pong. */
    private fun onVerifiedPing(entry: AuthorizedClient, envelope: SignedEnvelope, now: Long): AckOutcome {
        authorizedRepository.bumpLastSeen(entry.clientId, envelope.counter, now)
        nsClientRepository.addLog("◄ CLIENTCTL", "ping from ${entry.name}")
        return AckOutcome(AckStatus.Ok, null)
    }

    /**
     * Client dismissed/muted the relayed bolus-delivery-failure alarm on its side → clear the master's own copy.
     * Fire-and-forget (the client sends wantsAck=false) so the returned outcome is never written as an ack. ONE-WAY by
     * design: the master clearing its alarm does NOT push back to the client (the remote initiator must acknowledge its
     * own failed bolus). dismiss() is a no-op when nothing of that id is showing.
     */
    private fun onVerifiedDismissAlarm(entry: AuthorizedClient, envelope: SignedEnvelope, now: Long): AckOutcome {
        authorizedRepository.bumpLastSeen(entry.clientId, envelope.counter, now)
        notificationManager.dismiss(NotificationId.BOLUS_DELIVERY_FAILED)
        nsClientRepository.addLog("◄ CLIENTCTL", "dismiss_alarm from ${entry.name}")
        return AckOutcome(AckStatus.Ok, null)
    }

    /**
     * Client asked to abort the in-progress bolus it initiated. Fire-and-forget (wantsAck=false). cancelAllBoluses
     * stops the running bolus (or no-ops if none is running); the resulting progress terminal mirrors back. ONE-WAY
     * like DismissAlarm — no ack.
     */
    private fun onVerifiedStopBolus(entry: AuthorizedClient, envelope: SignedEnvelope, now: Long): AckOutcome {
        authorizedRepository.bumpLastSeen(entry.clientId, envelope.counter, now)
        commandQueue.cancelAllBoluses(null)
        nsClientRepository.addLog("◄ CLIENTCTL", "stop_bolus from ${entry.name}")
        return AckOutcome(AckStatus.Ok, null)
    }

    /**
     * Client asked to PREPARE a scene activation: the master resolves it against ITS state, gates it, AUTHORS the
     * confirmation lines from the scene's actions, parks it, and returns the preview in the SIGNED payload so the
     * client renders the master's exact confirmation. Nothing is activated yet (a [ClientControlMessage.SceneCommit] follows).
     */
    private suspend fun onVerifiedScenePrepare(entry: AuthorizedClient, envelope: SignedEnvelope, message: ClientControlMessage.ScenePrepare, now: Long): AckOutcome {
        authorizedRepository.bumpLastSeen(entry.clientId, envelope.counter, now)
        return when (val result = sceneAutomationApi.prepareScene(message.sceneId, message.durationMinutes)) {
            is WizardBolusExecutor.PrepareResult.Error   -> {
                nsClientRepository.addLog("◄ CLIENTCTL", "scene.prepare sceneId=${message.sceneId} from ${entry.name}: ${result.message}")
                AckOutcome(AckStatus.Failed, FailureReason.ExecutionFailed.name, result.message)
            }

            // Scene prepare never resolves to a no-op, but the sealed type requires the branch — treat as a no-action reject.
            WizardBolusExecutor.PrepareResult.NoAction   -> {
                nsClientRepository.addLog("◄ CLIENTCTL", "scene.prepare sceneId=${message.sceneId} from ${entry.name}: no action")
                AckOutcome(AckStatus.Failed, FailureReason.NoAction.name)
            }

            is WizardBolusExecutor.PrepareResult.Preview -> {
                val preview = BolusPreview(
                    bolusId = result.bolusId,
                    lines = result.lines.map { ConfirmationLineDto(it.role.name, it.text) },
                    advisorApplies = false,
                    advisorLines = emptyList()
                )
                nsClientRepository.addLog("◄ CLIENTCTL", "scene.prepare sceneId=${message.sceneId} from ${entry.name}")
                AckOutcome(AckStatus.Ok, null, json.encodeToString(BolusPreview.serializer(), preview))
            }
        }
    }

    /**
     * Client confirmed a prepared scene: activate the parked scene matching [message.bolusId] EXACTLY once. A re-sent
     * commit finds the slot drained → NoPending (no double-activate). An activation failure rides back via onError → Failed.
     */
    private suspend fun onVerifiedSceneCommit(entry: AuthorizedClient, envelope: SignedEnvelope, message: ClientControlMessage.SceneCommit, now: Long): AckOutcome {
        authorizedRepository.bumpLastSeen(entry.clientId, envelope.counter, now)
        var err: String? = null
        val result = sceneAutomationApi.commitScene(message.bolusId) { err = it }
        val activated = result is WizardBolusExecutor.ConfirmResult.Delivered
        nsClientRepository.addLog("◄ CLIENTCTL", "scene.commit id=${message.bolusId} from ${entry.name}: ${if (activated) "activated" else "no-pending"}")
        return when {
            result is WizardBolusExecutor.ConfirmResult.NoPending -> AckOutcome(AckStatus.Failed, FailureReason.NoPendingBolus.name)
            err != null                                           -> AckOutcome(AckStatus.Failed, FailureReason.ExecutionFailed.name, err)
            else                                                  -> AckOutcome(AckStatus.Ok, null)
        }
    }

    private suspend fun onVerifiedSceneStop(entry: AuthorizedClient, envelope: SignedEnvelope, message: ClientControlMessage.SceneStop, now: Long): AckOutcome {
        authorizedRepository.bumpLastSeen(entry.clientId, envelope.counter, now)
        // triggerChain=true means "Skip to <ChainTarget>": stopActiveSceneAndChain resolves the active
        // scene's currently-configured chain target FRESH at receipt time (not from the wire) so a stale
        // client view can't trigger an unintended scene, and posts the chain notification — identical to
        // a master-local "Skip to" from the End-Scene dialog.
        val result = if (message.triggerChain) sceneAutomationApi.stopActiveSceneAndChain()
        else sceneAutomationApi.stopActiveScene()
        val outcome = if (message.triggerChain) "chain" else "plain-stop"
        nsClientRepository.addLog("◄ CLIENTCTL", "scene.stop $outcome from ${entry.name}: ${result.tag()}")
        // ChainCompleted with failedCount==0 is a fully successful chain — not a failure to warn about.
        val isFailure = when (result) {
            SceneAutomationResult.Success           -> false
            is SceneAutomationResult.ChainCompleted -> result.failedCount > 0
            else                                    -> true
        }
        if (isFailure)
            aapsLogger.warn(LTag.NSCLIENT, "ClientControl: scene.stop failed for ${entry.name}: ${result.tag()}")
        return if (isFailure) AckOutcome(AckStatus.Failed, result.failureReason().name) else AckOutcome(AckStatus.Ok, null)
    }

    /** Map a failed [SceneAutomationResult] to a localizable [FailureReason] code for the client's ack. */
    private fun SceneAutomationResult.failureReason(): FailureReason = when (this) {
        SceneAutomationResult.SceneNotFound     -> FailureReason.SceneNotFound
        SceneAutomationResult.SceneDisabled     -> FailureReason.SceneDisabled
        is SceneAutomationResult.ChainCompleted -> FailureReason.PartialFailure
        is SceneAutomationResult.Failed,
        SceneAutomationResult.Success           -> FailureReason.ExecutionFailed
    }

    /**
     * Client asked to PREPARE a QuickWizard bolus: compute + constraint-cap the dose on the master's OWN live
     * state, park it, and return the preview (the master's color-coded confirmation lines + advisor flags) in
     * the SIGNED ack payload so the client renders the master's exact confirmation. Nothing is delivered yet.
     */
    private suspend fun onVerifiedBolusPrepare(entry: AuthorizedClient, envelope: SignedEnvelope, message: ClientControlMessage.BolusPrepare, now: Long): AckOutcome {
        authorizedRepository.bumpLastSeen(entry.clientId, envelope.counter, now)
        return when (val result = wizardBolusExecutor.prepareQuickWizard(message.guid)) {
            is WizardBolusExecutor.PrepareResult.Error   -> {
                nsClientRepository.addLog("◄ CLIENTCTL", "bolus.prepare from ${entry.name}: ${result.message}")
                // Surface the master's specific reason (no BG / profile / pump) to the client via the payload.
                AckOutcome(AckStatus.Failed, FailureReason.BolusComputeFailed.name, result.message)
            }

            WizardBolusExecutor.PrepareResult.NoAction   -> {
                nsClientRepository.addLog("◄ CLIENTCTL", "bolus.prepare from ${entry.name}: no action")
                AckOutcome(AckStatus.Failed, FailureReason.NoAction.name)
            }

            is WizardBolusExecutor.PrepareResult.Preview -> {
                val preview = BolusPreview(
                    bolusId = result.bolusId,
                    lines = result.lines.map { ConfirmationLineDto(it.role.name, it.text) },
                    advisorApplies = result.advisorApplies,
                    advisorLines = result.advisorLines.map { ConfirmationLineDto(it.role.name, it.text) },
                    wizardDetail = result.wizardDetail?.toDto(),
                )
                nsClientRepository.addLog("◄ CLIENTCTL", "bolus.prepare from ${entry.name}: ${result.insulin}U ${result.carbs}g" + if (result.advisorApplies) " advisor" else "")
                AckOutcome(AckStatus.Ok, null, json.encodeToString(BolusPreview.serializer(), preview))
            }
        }
    }

    /**
     * Client confirmed a prepared bolus: deliver the parked dose matching [message.bolusId] EXACTLY once. A
     * re-sent commit finds the slot drained → NoPending → NoPendingBolus (no double-dose). A synchronous
     * mode-gate rejection rides back through deliverError → ExecutionFailed. [asAdvisor] picks the correction-only path.
     */
    private suspend fun onVerifiedBolusCommit(entry: AuthorizedClient, envelope: SignedEnvelope, message: ClientControlMessage.BolusCommit, now: Long): AckOutcome {
        authorizedRepository.bumpLastSeen(entry.clientId, envelope.counter, now)
        // AtomicReference (not a plain captured var): the onError closure CAN, in the microscopic window between
        // confirm() returning and syncDone.set(true), be invoked on the appScope thread — give the sync-path write a
        // memory barrier so the read below can never miss it. (Same reason syncDone is an AtomicBoolean.)
        val deliverError = AtomicReference<String?>(null)
        // The onError closure fires BOTH synchronously (a mode-gate reject → feeds THIS command's Done ack) and later,
        // asynchronously (the pump delivery failed — the outcome the Done ack couldn't wait for). After confirm() returns
        // the synchronous phase is over, so any further failure is the async one: relay it as a late Delivery ack the
        // client turns into its own alarm. (The master itself already alarms via the executor — phase 1a.)
        // Arm the progress mirror to THIS client BEFORE delivery starts (the executor only sees Sources.NSClient,
        // not the clientId). Disarmed below if nothing was parked, on the terminal frame by the observer, and in
        // the onError path if delivery fails before any frame streamed (so a stale arm can't mirror a later bolus).
        // Order matters: write progressArmedAt (plain @Volatile) BEFORE progressClientId.set() (AtomicReference). The
        // collector reads progressClientId.get() (acquire) then progressArmedAt; the release fence on .set() publishes
        // the prior armedAt write, so the collector can never observe the new clientId with a stale armedAt(0) and
        // wrongly drop the first progress frames (armedFresh=false).
        progressArmedAt = now
        progressDelivering = false // new arm starts in the pre-delivery, TTL-guarded window
        // Capture the generation now (before .set()'s release fence, like progressArmedAt): the mirror only
        // forwards a bolus whose generation is strictly newer — i.e. one this commit actually starts, not a
        // bolus already delivering on the master (which would otherwise be mis-shown on the client's dialog).
        progressArmedGeneration = bolusProgressData.currentGeneration
        progressClientId.set(entry.clientId)
        val syncDone = AtomicBoolean(false)
        val result = wizardBolusExecutor.confirm(message.bolusId, Sources.NSClient, { comment ->
            // Delivery failed. If no frame ever streamed for this arm (the bolus never started — e.g. the master was
            // already bolusing, so the queue rejected this one), disarm now so a LATER unrelated master bolus isn't
            // mirrored to this client during the remaining arm-TTL. Skip if it IS streaming (its terminal Cleared
            // frame disarms instead); compareAndSet so a newer commit's arm is never clobbered (fires sync OR async).
            if (!progressDelivering) progressClientId.compareAndSet(entry.clientId, null)
            if (syncDone.get()) appScope.launch { writeDeliveryFailureAck(entry, envelope.counter, comment) }
            else deliverError.set(comment)
        }, message.asAdvisor, correctionU = message.correctionU)
        syncDone.set(true)
        val delivered = result is WizardBolusExecutor.ConfirmResult.Delivered
        if (!delivered) progressClientId.set(null) // NoPending → nothing starts → don't mirror a future bolus to this client
        nsClientRepository.addLog("◄ CLIENTCTL", "bolus.commit id=${message.bolusId}${if (message.asAdvisor) " advisor" else ""} from ${entry.name}: ${if (delivered) "delivered" else "no-pending"}")
        val err = deliverError.get()
        return when {
            result is WizardBolusExecutor.ConfirmResult.NoPending -> AckOutcome(AckStatus.Failed, FailureReason.NoPendingBolus.name)
            err != null                                           -> AckOutcome(AckStatus.Failed, FailureReason.ExecutionFailed.name, err)
            else                                                  -> AckOutcome(AckStatus.Ok, null)
        }
    }

    /**
     * Client asked to PREPARE a MANUAL bolus-wizard bolus: recompute the dose on the master's OWN profile/COB/IOB
     * from the client's raw inputs, park it, and return the master's color-coded confirmation in the SIGNED payload
     * (same `BolusPreview`/commit as QuickWizard). Nothing is delivered yet.
     */
    private suspend fun onVerifiedWizardPrepare(entry: AuthorizedClient, envelope: SignedEnvelope, message: ClientControlMessage.WizardPrepare, now: Long): AckOutcome {
        authorizedRepository.bumpLastSeen(entry.clientId, envelope.counter, now)
        val inputs = WizardBolusExecutor.WizardInputs(
            bg = message.bg, carbs = message.carbs, percentage = message.percentage, directCorrection = message.directCorrection,
            carbTime = message.carbTime, useBg = message.useBg, useCob = message.useCob, useIob = message.useIob,
            useTt = message.useTt, useTrend = message.useTrend, alarm = message.alarm, notes = message.notes,
            eCarbsGrams = message.eCarbsGrams, eCarbsDelayMinutes = message.eCarbsDelayMinutes, eCarbsDurationHours = message.eCarbsDurationHours,
            profileName = message.profileName, source = Sources.NSClient
        )
        return when (val result = wizardBolusExecutor.prepareWizard(inputs)) {
            is WizardBolusExecutor.PrepareResult.Error   -> {
                nsClientRepository.addLog("◄ CLIENTCTL", "wizard.prepare from ${entry.name}: ${result.message}")
                AckOutcome(AckStatus.Failed, FailureReason.BolusComputeFailed.name, result.message)
            }

            WizardBolusExecutor.PrepareResult.NoAction   -> {
                nsClientRepository.addLog("◄ CLIENTCTL", "wizard.prepare from ${entry.name}: no action")
                AckOutcome(AckStatus.Failed, FailureReason.NoAction.name)
            }

            is WizardBolusExecutor.PrepareResult.Preview -> {
                val preview = BolusPreview(
                    bolusId = result.bolusId,
                    lines = result.lines.map { ConfirmationLineDto(it.role.name, it.text) },
                    advisorApplies = result.advisorApplies,
                    advisorLines = result.advisorLines.map { ConfirmationLineDto(it.role.name, it.text) },
                    wizardDetail = result.wizardDetail?.toDto(),
                )
                nsClientRepository.addLog("◄ CLIENTCTL", "wizard.prepare from ${entry.name}: ${result.insulin}U ${result.carbs}g" + if (result.advisorApplies) " advisor" else "")
                AckOutcome(AckStatus.Ok, null, json.encodeToString(BolusPreview.serializer(), preview))
            }
        }
    }

    /**
     * Client asked to PREPARE a multi-action batch: cap the delivery (a record-only is kept as given), build the
     * MERGED confirmation lines, park the bundle, and return the preview in the SIGNED payload (same `BolusPreview` +
     * commit path as the other prepares). Nothing delivered yet; a `BolusCommit` applies it (TT per the ordering rule).
     */
    private suspend fun onVerifiedBatchPrepare(entry: AuthorizedClient, envelope: SignedEnvelope, message: ClientControlMessage.BatchPrepare, now: Long): AckOutcome {
        authorizedRepository.bumpLastSeen(entry.clientId, envelope.counter, now)
        val actions = message.actions.mapNotNull { it.toDomain() }
        return when (val result = wizardBolusExecutor.prepareBatch(actions)) {
            is WizardBolusExecutor.PrepareResult.Error   -> {
                nsClientRepository.addLog("◄ CLIENTCTL", "batch.prepare from ${entry.name}: ${result.message}")
                AckOutcome(AckStatus.Failed, FailureReason.BolusComputeFailed.name, result.message)
            }

            WizardBolusExecutor.PrepareResult.NoAction   -> {
                nsClientRepository.addLog("◄ CLIENTCTL", "batch.prepare from ${entry.name}: no action")
                AckOutcome(AckStatus.Failed, FailureReason.NoAction.name)
            }

            is WizardBolusExecutor.PrepareResult.Preview -> {
                val preview = BolusPreview(
                    bolusId = result.bolusId,
                    lines = result.lines.map { ConfirmationLineDto(it.role.name, it.text) },
                    advisorApplies = false,
                    advisorLines = emptyList()
                )
                nsClientRepository.addLog("◄ CLIENTCTL", "batch.prepare from ${entry.name}: ${actions.size} action(s)")
                AckOutcome(AckStatus.Ok, null, json.encodeToString(BolusPreview.serializer(), preview))
            }
        }
    }

    /**
     * Generic apply of client-pushed bidirectionally-synced preference values. Per key: resolve it
     * from the registry, reject anything not declared `Bidirectional` (authority), drop stale pushes
     * by per-key last-writer-wins, and write the winners via [Preferences.putRemote] — which stamps
     * the modified time to the pushed version and suppresses re-publish (no echo). The running-config
     * publisher then republishes the cold doc, fanning the merged value out to all clients.
     *
     * Value parsing handles Boolean/String/Int/UnitDouble (see the `when` below); extend it per type
     * as new types are marked `Bidirectional`.
     */
    private fun onVerifiedPreferencesUpdate(entry: AuthorizedClient, envelope: SignedEnvelope, message: ClientControlMessage.PreferencesUpdate, now: Long): AckOutcome {
        authorizedRepository.bumpLastSeen(entry.clientId, envelope.counter, now)
        val applied = mutableListOf<String>()
        message.prefs.forEach { (keyString, pushed) ->
            val key = preferences.get(keyString)
            if (key == null || key.sync?.direction != SyncDirection.Bidirectional) {
                aapsLogger.warn(LTag.NSCLIENT, "ClientControl: preferences.update from ${entry.name} rejected $keyString (unknown or not client-writable)")
                return@forEach
            }
            if (pushed.lastModified <= preferences.get(LongComposedKey.SyncedPrefModified, keyString)) return@forEach // stale, LWW
            when (key) {
                is BooleanNonPreferenceKey -> {
                    val value = pushed.value.toBooleanStrictOrNull()
                    if (value == null) {
                        aapsLogger.warn(LTag.NSCLIENT, "ClientControl: preferences.update from ${entry.name} bad boolean for $keyString: '${pushed.value}'")
                        return@forEach
                    }
                    preferences.putRemote(key, value, pushed.lastModified)
                    applied += "$keyString=$value"
                }

                is StringNonPreferenceKey  -> {
                    preferences.putRemote(key, pushed.value, pushed.lastModified)
                    applied += "$keyString=${pushed.value}"
                }

                is IntNonPreferenceKey     -> {
                    val value = pushed.value.toIntOrNull()
                    if (value == null) {
                        aapsLogger.warn(LTag.NSCLIENT, "ClientControl: preferences.update from ${entry.name} bad int for $keyString: '${pushed.value}'")
                        return@forEach
                    }
                    preferences.putRemote(key, value, pushed.lastModified)
                    applied += "$keyString=$value"
                }

                is DoubleNonPreferenceKey  -> {
                    val value = pushed.value.toDoubleOrNull()
                    if (value == null) {
                        aapsLogger.warn(LTag.NSCLIENT, "ClientControl: preferences.update from ${entry.name} bad double for $keyString: '${pushed.value}'")
                        return@forEach
                    }
                    preferences.putRemote(key, value, pushed.lastModified)
                    applied += "$keyString=$value"
                }

                is UnitDoublePreferenceKey -> {
                    val value = pushed.value.toDoubleOrNull()
                    if (value == null) {
                        aapsLogger.warn(LTag.NSCLIENT, "ClientControl: preferences.update from ${entry.name} bad double for $keyString: '${pushed.value}'")
                        return@forEach
                    }
                    preferences.putRemote(key, value, pushed.lastModified)   // raw mg/dl
                    applied += "$keyString=$value"
                }

                else                       ->
                    aapsLogger.warn(LTag.NSCLIENT, "ClientControl: preferences.update from ${entry.name} unsupported key type for $keyString")
            }
        }
        nsClientRepository.addLog("◄ CLIENTCTL", "preferences.update from ${entry.name}: " + if (applied.isEmpty()) "nothing applied" else "applied ${applied.joinToString()}")
        if (applied.isNotEmpty()) uel.log(Action.REMOTE_CONFIG_CHANGED, Sources.NSClient, note = "${entry.name}: ${applied.joinToString()}")
        // Always republish the cold doc so the client converges to the master's authoritative values —
        // including keys LWW-dropped here as a no-op (those don't change a value, so they wouldn't
        // trigger the observeChange-driven republish on their own). Without this a client that loses LWW
        // on a concurrent edit would stay stuck on its optimistic value.
        runningConfigurationPublisher.requestColdRepublish()
        // Best-effort merge: per-key rejects (unknown/stale/bad-type) are logged but the batch as a
        // whole is acknowledged Ok.
        return AckOutcome(AckStatus.Ok, null)
    }

    private fun SceneAutomationResult.tag(): String = when (this) {
        SceneAutomationResult.Success           -> "ok"
        SceneAutomationResult.SceneNotFound     -> "scene-not-found"
        SceneAutomationResult.SceneDisabled     -> "scene-disabled"
        is SceneAutomationResult.Failed         -> "failed:${message ?: "unknown"}"
        is SceneAutomationResult.ChainCompleted ->
            if (failedCount == 0) "chained:${endedSceneName ?: "?"}→$targetSceneName"
            else "chain-partial:${endedSceneName ?: "?"}→$targetSceneName($failedCount/$totalCount)"
    }

    private fun onVerifiedUndecodablePayload(entry: AuthorizedClient, envelope: SignedEnvelope, now: Long) {
        // Advance counter: the secret-holder sent this intentionally, this master just doesn't have a
        // ClientControlMessage variant for type=${envelope.type}. Not advancing would let the same doc
        // replay every WS update.
        authorizedRepository.bumpLastSeen(entry.clientId, envelope.counter, now)
        nsClientRepository.addLog("◄ CLIENTCTL", "type=${envelope.type} undecodable for ${entry.name}")
        aapsLogger.warn(LTag.NSCLIENT, "ClientControl: ${entry.clientId} verified envelope type=${envelope.type} has no decoder on this master, advancing counter and acking")
    }

    /**
     * Write a signed [AckEnvelope] to the per-client ACK identifier (overwritten in place). Signed
     * with the same shared secret as the inbound command so the client can prove it came from us.
     * Exception-safe: a failed ACK write must never abort command processing (the command already
     * applied) — it just degrades the client to its timeout/sync-back fallback.
     */
    private suspend fun writeAck(
        client: NSAndroidClient,
        secret: ByteArray,
        clientId: String,
        commandCounter: Long,
        phase: AckPhase,
        status: AckStatus,
        reason: String?,
        payload: String?,
        now: Long
    ) {
        val ack = ClientControlCrypto.signAck(
            secret,
            AckEnvelope(clientId = clientId, commandCounter = commandCounter, phase = phase, status = status, reason = reason, payload = payload, timestamp = now, signature = "")
        )
        val identifier = ClientControlPublisher.IDENTIFIER_ACK_PREFIX + clientId
        val doc = JSONObject().apply {
            put("date", ClientControlPublisher.DOC_DATE)
            put("utcOffset", 0)
            put("app", "AAPS")
            put("schemaVersion", ClientControlPublisher.SCHEMA_VERSION)
            put("ack", JSONObject(json.encodeToString(AckEnvelope.serializer(), ack)))
        }
        runCatching { client.updateSettings(identifier, doc) }
            .onSuccess { nsClientRepository.addLog("► CLIENTCTL", "ack $phase/$status counter=$commandCounter" + (reason?.let { " ($it)" } ?: "")) }
            .onFailure { aapsLogger.error(LTag.NSCLIENT, "ClientControl: ack write failed for $identifier: ${it.message}") }
    }

    /**
     * Relay an ASYNC bolus delivery failure (the pump failed AFTER the Done ack already said "queued") to the
     * initiating client as a late [AckPhase.Delivery]/[AckStatus.Failed] ack — the client turns it into an URGENT
     * alarm. Re-resolves the client + secret since the async failure fires long after [verifyAndAck] returned;
     * best-effort + exception-safe (writeAck swallows write failures). [comment] is the pump's error detail.
     */
    private suspend fun writeDeliveryFailureAck(entry: AuthorizedClient, commandCounter: Long, comment: String) {
        val client = nsClientV3Plugin.get().nsAndroidClient ?: return
        val secret = authorizedRepository.secretLookup(entry.clientId)?.secretBytes ?: return
        writeAck(client, secret, entry.clientId, commandCounter, AckPhase.Delivery, AckStatus.Failed, FailureReason.ExecutionFailed.name, comment, dateUtil.now())
    }

    /**
     * Write one throttled bolus-progress frame to the armed client's per-client doc (overwritten in place). Signed
     * with the shared secret so a forged "delivered" is no more believable than a forged ack. Best-effort — a dropped
     * frame just means a momentarily stale bar on the client; the next frame (or the terminal) corrects it.
     */
    private suspend fun writeProgress(clientId: String, phase: ProgressPhase, st: BolusProgressState) {
        val client = nsClientV3Plugin.get().nsAndroidClient ?: return
        val secret = authorizedRepository.secretLookup(clientId)?.secretBytes ?: return
        val env = ClientControlCrypto.signProgress(
            secret,
            ProgressEnvelope(
                clientId = clientId, phase = phase, insulin = st.insulin, percent = st.percent, status = st.status,
                delivered = st.delivered.cU, stopDeliveryEnabled = st.stopDeliveryEnabled, timestamp = dateUtil.now(), signature = ""
            )
        )
        val identifier = ClientControlPublisher.IDENTIFIER_PROGRESS_PREFIX + clientId
        val doc = JSONObject().apply {
            put("date", ClientControlPublisher.DOC_DATE)
            put("utcOffset", 0)
            put("app", "AAPS")
            put("schemaVersion", ClientControlPublisher.SCHEMA_VERSION)
            put("progress", JSONObject(json.encodeToString(ProgressEnvelope.serializer(), env)))
        }
        runCatching { client.updateSettings(identifier, doc) }
            .onFailure { aapsLogger.error(LTag.NSCLIENT, "ClientControl: progress write failed for $identifier: ${it.message}") }
    }

    /** Result a command handler reports back so [verifyAndAck] can write the terminal Done ack. */
    private data class AckOutcome(val status: AckStatus, val reason: String?, val payload: String? = null)
}

private fun EventData.WizardDetail.toDto() = WizardDetailDto(
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
