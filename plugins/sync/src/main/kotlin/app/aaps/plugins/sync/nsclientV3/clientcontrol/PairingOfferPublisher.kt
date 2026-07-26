package app.aaps.plugins.sync.nsclientV3.clientcontrol

import android.util.Base64
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.nssdk.localmodel.clientcontrol.PairingOffer
import app.aaps.core.nssdk.localmodel.clientcontrol.PairingPayload
import app.aaps.core.nssdk.utils.ClientControlPairingCrypto
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Master-side publisher for PIN-wrapped pairing offers.
 *
 * Writes an `aaps_clientcontrol_offer_<clientId>` doc to the NS `settings` collection so a
 * client that knows the user-displayed PIN — but cannot scan a QR — can fetch it, unwrap,
 * and complete pairing through the normal `hello` flow.
 *
 * The doc body is a [PairingOffer] with the AES-GCM-wrapped `PairingPayload` JSON. The PIN
 * itself is never written to NS; only the salt + IV + ciphertext travel. Deleted by the
 * master on dismiss / pair-complete / expiry so the brute-force window is bounded.
 *
 * The wrap + upload runs on [Dispatchers.Default] — PBKDF2 is ~200ms; on Main it freezes the
 * dialog as it opens.
 */
@Singleton
class PairingOfferPublisher @Inject constructor(
    private val nsClientV3Plugin: Provider<NSClientV3Plugin>,
    private val nsClientRepository: NSClientRepository,
    private val aapsLogger: AAPSLogger
) {

    private val json = Json { encodeDefaults = true }

    suspend fun publishOffer(payload: PairingPayload, pin: String): Boolean = withContext(Dispatchers.Default) {
        val client = nsClientV3Plugin.get().nsAndroidClient ?: run {
            aapsLogger.error(LTag.NSCLIENT, "PairingOffer: NS client not initialized")
            return@withContext false
        }
        val doc = try {
            buildOfferDoc(payload, pin)
        } catch (e: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "PairingOffer build failed: ${e.message}", e)
            return@withContext false
        }
        val identifier = "${ClientControlPublisher.IDENTIFIER_OFFER_PREFIX}${payload.clientId}"
        try {
            val resp = client.updateSettings(identifier, doc)
            if (resp.response in 200..299) {
                nsClientRepository.addLog("► CLIENTCTL", "$identifier offer published")
                true
            } else {
                aapsLogger.error(LTag.NSCLIENT, "PairingOffer publish HTTP ${resp.response}: ${resp.errorResponse}")
                nsClientRepository.addLog("✕ CLIENTCTL", "$identifier HTTP ${resp.response}")
                false
            }
        } catch (e: IOException) {
            aapsLogger.error(LTag.NSCLIENT, "PairingOffer publish IO exception: ${e.message}", e)
            false
        }
    }

    /**
     * Removes the published `aaps_clientcontrol_offer_<clientId>` doc from NS.
     *
     * Called on dialog dismiss / pair-complete / expiry to close the PIN brute-force window —
     * while the offer doc lives on NS, anyone with read access can keep trying PINs against the
     * wrapped payload, so prompt deletion is a security-relevant operation.
     *
     * A delete failure is logged (ERROR) but never thrown: a left-over offer is bounded anyway
     * by its own `expiresAt`, so failing the caller would be worse than retrying on the next
     * cleanup pass. Non-throwing is intentional — do not change the signature/return type.
     *
     * Uses a *permanent* delete so the wrapped secret is actually removed from NS rather than left
     * behind in a soft-delete tombstone (which would keep the brute-force window open). Safe here:
     * the offer identifier embeds a fresh per-pairing clientId, so it is never re-PUT after deletion.
     */
    suspend fun deleteOffer(clientId: String) {
        val client = nsClientV3Plugin.get().nsAndroidClient ?: return
        val identifier = "${ClientControlPublisher.IDENTIFIER_OFFER_PREFIX}$clientId"
        try {
            client.deleteSettingsPermanent(identifier)
        } catch (e: IOException) {
            aapsLogger.error(LTag.NSCLIENT, "PairingOffer delete failed for $identifier: ${e.message}")
        }
    }

    private fun buildOfferDoc(payload: PairingPayload, pin: String): JSONObject {
        val payloadBytes = json.encodeToString(PairingPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        val salt = ClientControlPairingCrypto.newSalt()
        val iv = ClientControlPairingCrypto.newIv()
        val wrapped = ClientControlPairingCrypto.wrap(payloadBytes, pin, salt, iv)
        val offer = PairingOffer(
            clientId = payload.clientId,
            expiresAt = payload.expiresAt,
            kdfSaltB64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            wrappedB64 = Base64.encodeToString(wrapped, Base64.NO_WRAP)
        )
        return JSONObject().apply {
            // Same placeholder convention as ClientControlPublisher.uploadEnvelope — `date` is
            // immutable on subsequent PUTs, so a constant placeholder is the only safe value.
            put("date", ClientControlPublisher.DOC_DATE)
            put("utcOffset", 0)
            put("app", "AAPS")
            put("schemaVersion", ClientControlPublisher.SCHEMA_VERSION)
            put("offer", JSONObject(json.encodeToString(PairingOffer.serializer(), offer)))
        }
    }
}
