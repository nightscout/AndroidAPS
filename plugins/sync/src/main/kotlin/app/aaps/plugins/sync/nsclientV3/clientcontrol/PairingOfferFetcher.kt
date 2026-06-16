package app.aaps.plugins.sync.nsclientV3.clientcontrol

import android.util.Base64
import androidx.annotation.VisibleForTesting
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.nssdk.localmodel.clientcontrol.PairingOffer
import app.aaps.core.nssdk.localmodel.clientcontrol.PairingPayload
import app.aaps.core.nssdk.utils.ClientControlPairingCrypto
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Client-side fetcher for PIN-wrapped pairing offers.
 *
 * Lists every `aaps_clientcontrol_offer_*` doc on the NS instance and tries to unwrap each
 * with the user-typed PIN. Returns the unique successfully decrypted [PairingPayload], or
 * `NoMatch` if no offer accepts the PIN, or `Ambiguous` if more than one does (8-digit PIN
 * collision — refuse rather than pair to the wrong master).
 *
 * The unwrap loop runs on [Dispatchers.Default] because each PBKDF2 attempt costs ~200ms;
 * trying it on Main blocks the UI long enough to trigger ANR with a handful of stale offers.
 */
@Singleton
class PairingOfferFetcher @Inject constructor(
    private val nsClientV3Plugin: Provider<NSClientV3Plugin>,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) {

    companion object {

        // Higher than the receiver's 100 because the offer doc must be present in a single
        // page or the user gets a misleading "wrong PIN" — a busy NS holds many other settings.
        private const val SEARCH_LIMIT = 500
    }

    private val json = Json { ignoreUnknownKeys = true }

    // The PBKDF2 unwrap loop runs off Main (see [findOfferForPin]). Injectable only so unit tests can
    // pin it to the test thread; production always uses [Dispatchers.Default].
    @VisibleForTesting internal var unwrapDispatcher: CoroutineDispatcher = Dispatchers.Default

    sealed class Result {
        data class Success(val payload: PairingPayload) : Result()

        /** More than one offer decrypted with the same PIN — collision; refuse and force retry. */
        data object Ambiguous : Result()
        data object NoMatch : Result()
        data object NotAvailable : Result()
    }

    suspend fun findOfferForPin(pin: String): Result {
        val client = nsClientV3Plugin.get().nsAndroidClient ?: run {
            aapsLogger.error(LTag.NSCLIENT, "PairingOfferFetcher: NS client not initialized")
            return Result.NotAvailable
        }
        val resp = try {
            client.searchSettings(limit = SEARCH_LIMIT)
        } catch (e: IOException) {
            aapsLogger.error(LTag.NSCLIENT, "PairingOfferFetcher: searchSettings failed: ${e.message}")
            return Result.NotAvailable
        }
        // Off Main: PBKDF2 is ~200ms per candidate offer; on Main this trips ANR with a few stale offers.
        return withContext(unwrapDispatcher) {
            val now = dateUtil.now()
            var scanned = 0
            var candidates = 0
            val matches = mutableListOf<PairingPayload>()
            for (doc in resp.values) {
                val identifier = doc.optString("identifier")
                if (!identifier.startsWith(ClientControlPublisher.IDENTIFIER_OFFER_PREFIX)) continue
                scanned++
                val offerObj = doc.optJSONObject("offer") ?: continue
                val offer = try {
                    json.decodeFromString<PairingOffer>(offerObj.toString())
                } catch (_: SerializationException) {
                    aapsLogger.warn(LTag.NSCLIENT, "PairingOfferFetcher: offer $identifier malformed, skipping")
                    continue
                }
                // Skip server-side stale offers up-front so we don't burn PBKDF2 on a hopeless candidate.
                if (offer.expiresAt > 0L && offer.expiresAt < now) continue
                candidates++
                val payload = tryUnwrap(offer, pin, now) ?: continue
                matches += payload
            }
            aapsLogger.debug(LTag.NSCLIENT, "PairingOfferFetcher: scanned=$scanned live=$candidates matched=${matches.size}")
            when (matches.size) {
                0    -> Result.NoMatch
                1    -> Result.Success(matches[0])
                else -> Result.Ambiguous
            }
        }
    }

    private fun tryUnwrap(offer: PairingOffer, pin: String, now: Long): PairingPayload? {
        val salt = decodeB64(offer.kdfSaltB64) ?: return null
        val iv = decodeB64(offer.ivB64) ?: return null
        val wrapped = decodeB64(offer.wrappedB64) ?: return null
        val plaintext = ClientControlPairingCrypto.unwrap(wrapped, pin, salt, iv) ?: return null
        val payloadJson = String(plaintext, Charsets.UTF_8)
        val payload = try {
            json.decodeFromString<PairingPayload>(payloadJson)
        } catch (_: SerializationException) {
            return null
        }
        if (payload.masterInstallId.isBlank() || payload.clientId.isBlank() || payload.secretHex.isBlank()) return null
        // Defense-in-depth: the wrapped payload carries its own hard expiry independent of the outer
        // offer.expiresAt checked above. Reject an expired payload even if the (forgeable) outer field
        // claims otherwise — the master refuses a `hello` past expiresAt anyway.
        if (payload.expiresAt > 0L && payload.expiresAt < now) return null
        return payload
    }

    private fun decodeB64(s: String): ByteArray? = try {
        Base64.decode(s, Base64.NO_WRAP)
    } catch (_: IllegalArgumentException) {
        null
    }
}
