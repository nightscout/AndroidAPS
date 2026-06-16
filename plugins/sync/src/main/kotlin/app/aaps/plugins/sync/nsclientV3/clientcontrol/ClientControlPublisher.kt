package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.scenes.ClientControlSendResult
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientControlMessage
import app.aaps.core.nssdk.localmodel.clientcontrol.SignedEnvelope
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Client-side publisher for client-control envelopes.
 *
 * Writes signed envelopes into the NS `settings` collection under deterministic
 * identifiers the master polls for:
 * - `aaps_clientcontrol_hello_<clientId>` — first message after pairing
 * - (later) `aaps_clientcontrol_cmd_<clientId>_<counter>` for commands
 *
 * Settings collection is reused as the transport because the existing sync layer
 * already authenticates writes against NS and provides an obvious lifecycle. A
 * dedicated `clientControl` collection would be cleaner long-term but requires
 * NS server-side schema work; settings is good enough for v1.
 *
 * The doc carries `date`/`utcOffset`/`app` to satisfy `validateCommon` plus a
 * `schemaVersion` field for forward compatibility, with the signed envelope
 * embedded under `envelope`.
 */
@Singleton
class ClientControlPublisher @Inject constructor(
    private val pairingRepository: ClientPairingRepository,
    private val nsClientV3Plugin: Provider<NSClientV3Plugin>,
    private val nsClientRepository: NSClientRepository,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) {

    companion object {

        const val IDENTIFIER_PREFIX = "aaps_clientcontrol_"
        const val IDENTIFIER_HELLO_PREFIX = "${IDENTIFIER_PREFIX}hello_"
        const val IDENTIFIER_CMD_PREFIX = "${IDENTIFIER_PREFIX}cmd_"

        // Master-published pairing offers (PIN-wrapped PairingPayload). Not envelopes — the
        // receiver must skip these so it does not try to verify its own offer as a hello/cmd.
        const val IDENTIFIER_OFFER_PREFIX = "${IDENTIFIER_PREFIX}offer_"

        // Master→client command ACK (one per client, overwritten in place). The master writes here;
        // the client reads here. Shares IDENTIFIER_PREFIX, so the master receiver MUST skip it (it is
        // not an inbound command envelope) — see ClientControlReceiver.
        const val IDENTIFIER_ACK_PREFIX = "${IDENTIFIER_PREFIX}ack_"

        // Master→client live bolus-progress mirror (one per client, overwritten in place, throttled ~1/s).
        // The master writes here; the client reads here. Shares IDENTIFIER_PREFIX, so the master receiver MUST
        // skip it (it is not an inbound command envelope) — see ClientControlReceiver.
        const val IDENTIFIER_PROGRESS_PREFIX = "${IDENTIFIER_PREFIX}progress_"
        const val SCHEMA_VERSION = 1

        // Fire-and-forget commands keep the historical ±5 min skew window as their validity, so adding
        // validUntil does not change their master-side drop behaviour. The round-trip path overrides
        // with a much shorter value (see ClientControlRoundTrip).
        const val FIRE_AND_FORGET_TTL_MS = 5L * 60L * 1000L

        // 1 ms past NS APIv3 MIN_TIMESTAMP (946684800000 = 2000-01-01 UTC). validateCommon
        // requires `date` and the server rejects any modification on subsequent PUTs to the
        // same identifier ("Field date cannot be modified by the client"). The real
        // message timestamp lives in envelope.timestamp (signed); the doc-level date is
        // a constant placeholder only.
        const val DOC_DATE = 946684800001L
    }

    private val json = Json { encodeDefaults = true }

    /**
     * Build + sign an envelope carrying [message] and write it to the NS settings collection
     * under the identifier appropriate for that message family. Returns true on HTTP 2xx.
     *
     * **Identifier scheme:**
     * - Hello: `aaps_clientcontrol_hello_<clientId>`
     * - Commands: `aaps_clientcontrol_cmd_<type>_<clientId>` where `<type>` is the variant's
     *   polymorphic discriminator (e.g. `scene.start`). Per-type slots prevent cross-type
     *   collision (a fresh `scene.start` won't overwrite an unprocessed `scene.stop`).
     *
     * **Same-type latest-wins is intentional**: if the user fires two commands of the same
     * type before the master has acknowledged the first (e.g. tap "Sleep" then change mind
     * and tap "Exercise"), `updateSettings` with the same identifier overwrites the first.
     * Master only sees the latter — which matches user intent ("I changed my mind"). For
     * scene control with human-paced taps this is the right semantics; if a future variant
     * needs queueing (e.g. sequenced bolus commands), it must use a different identifier
     * scheme and is responsible for inventing one.
     *
     * The identifier selector is an exhaustive `when` so adding a new [ClientControlMessage]
     * variant forces a hello-vs-command classification at compile time.
     */
    suspend fun publish(message: ClientControlMessage): ClientControlSendResult =
        publishTracked(message, dateUtil.now() + FIRE_AND_FORGET_TTL_MS, wantsAck = false).result

    /**
     * Like [publish], but carries an explicit [validUntil] (command expiry the master enforces) and
     * returns the envelope's `counter` on success so a round-trip caller can correlate the master's
     * ACK. The counter is null on any non-Success result (nothing was signed/sent). [wantsAck]
     * defaults true (the round-trip use); [publish] passes false so the master writes no ACK doc for
     * fire-and-forget commands.
     */
    suspend fun publishTracked(message: ClientControlMessage, validUntil: Long, wantsAck: Boolean = true): TrackedPublish {
        val pairing = pairingRepository.currentPairing() ?: run {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: publish called while unpaired")
            return TrackedPublish(ClientControlSendResult.NotPaired, null)
        }
        val payload = json.encodeToString(ClientControlMessage.serializer(), message)
        // Derive envelope.type from the payload's polymorphic discriminator so the two cannot
        // drift — there is exactly one source of truth (the variant's @SerialName).
        val type = json.parseToJsonElement(payload).jsonObject["type"]?.jsonPrimitive?.content ?: run {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: serializer produced no discriminator for $message")
            return TrackedPublish(ClientControlSendResult.PublishFailed("no discriminator"), null)
        }
        val envelope = pairingRepository.nextSignedEnvelope(type, payload, dateUtil.now(), validUntil, wantsAck) ?: run {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: failed to sign envelope for type=$type")
            return TrackedPublish(ClientControlSendResult.PublishFailed("sign failed"), null)
        }
        val identifier = when (message) {
            is ClientControlMessage.Hello -> "$IDENTIFIER_HELLO_PREFIX${pairing.clientId}"
            is ClientControlMessage.ScenePrepare,
            is ClientControlMessage.SceneCommit,
            is ClientControlMessage.SceneStop,
            ClientControlMessage.Ping,
            ClientControlMessage.DismissAlarm,
            ClientControlMessage.StopBolus,
            is ClientControlMessage.PreferencesUpdate,
            is ClientControlMessage.BolusPrepare,
            is ClientControlMessage.BolusCommit,
            is ClientControlMessage.WizardPrepare,
            is ClientControlMessage.BatchPrepare -> "$IDENTIFIER_CMD_PREFIX${type}_${pairing.clientId}"
        }
        val result = uploadEnvelope(identifier, envelope)
        return TrackedPublish(result, if (result is ClientControlSendResult.Success) envelope.counter else null)
    }

    /** Result of [publishTracked]: the send outcome plus the correlation counter (non-null only on Success). */
    data class TrackedPublish(val result: ClientControlSendResult, val counter: Long?)

    private suspend fun uploadEnvelope(identifier: String, envelope: SignedEnvelope): ClientControlSendResult {
        val client = nsClientV3Plugin.get().nsAndroidClient ?: run {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: NS client not initialized")
            return ClientControlSendResult.PublishFailed("NS client not initialized")
        }
        val envelopeJson = json.encodeToString(SignedEnvelope.serializer(), envelope)
        val doc = JSONObject().apply {
            // validateCommon requires date/utcOffset/app, but `date` is immutable after first
            // create — sending the live envelope timestamp here fails the second PUT to the same
            // identifier with HTTP 400. The authoritative timestamp is envelope.timestamp (signed);
            // the doc-level date is a stable placeholder, matching RunningConfigurationPublisher.
            put("date", DOC_DATE)
            put("utcOffset", 0)
            put("app", "AAPS")
            put("schemaVersion", SCHEMA_VERSION)
            put("envelope", JSONObject(envelopeJson))
        }
        return runCatching { client.updateSettings(identifier, doc) }
            .fold(
                onSuccess = { resp ->
                    if (resp.response in 200..299) {
                        nsClientRepository.addLog("► CLIENTCTL", "$identifier counter=${envelope.counter}")
                        ClientControlSendResult.Success
                    } else {
                        aapsLogger.error(LTag.NSCLIENT, "ClientControl publish HTTP ${resp.response}: ${resp.errorResponse}")
                        nsClientRepository.addLog("✕ CLIENTCTL", "$identifier HTTP ${resp.response}")
                        ClientControlSendResult.PublishFailed("HTTP ${resp.response}")
                    }
                },
                onFailure = { t ->
                    aapsLogger.error(LTag.NSCLIENT, "ClientControl publish exception: ${t.message}", t)
                    ClientControlSendResult.PublishFailed(t.message)
                }
            )
    }
}
