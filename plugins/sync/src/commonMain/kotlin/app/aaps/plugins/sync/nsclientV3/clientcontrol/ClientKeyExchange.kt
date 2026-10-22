package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.data.json.OrgJsonCompat.optJsonObjectCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.NSAndroidClientImpl
import app.aaps.core.nssdk.localmodel.clientcontrol.KeyExchangeEnvelope
import app.aaps.plugins.sync.nsclientV3.nsClientBaseUrl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Client side of the key exchange that replaces the pairing secret (`ClientControlKeyExchangeCrypto`), and of the
 * client's own Nightscout token the master sends in the same answer.
 *
 * The hello carries the client's public key; the master answers in `aaps_clientcontrol_key_<clientId>`. This takes the
 * answer, switches to the new secret, and uses the token, but only after a login with it works: a token that does not
 * work would cut the phone off from Nightscout, and so from its master. The master sees the new secret in use with the
 * next message, takes it as the only one and deletes the answer.
 */
@SingleIn(AppScope::class)
@Inject
class ClientKeyExchange(
    private val pairingRepository: ClientPairingRepository,
    private val roundTrip: () -> ClientControlRoundTrip,
    private val preferences: Preferences,
    private val nsClientRepository: NSClientRepository,
    private val aapsLogger: AAPSLogger
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Tells whether [token] logs in to Nightscout at [url] with the rights a client needs. Replaced in tests. */
    internal var tokenWorks: suspend (url: String, token: String) -> Boolean = ::loginWorks

    /** The master's answer, pushed over the websocket. Answers for other clients and for older hellos are ignored. */
    suspend fun onKeyDoc(doc: JsonObject) {
        val answer = doc.optJsonObjectCompat("key")
            ?.let { runCatching { json.decodeFromJsonElement(KeyExchangeEnvelope.serializer(), it) }.getOrNull() }
            ?: return
        val result = pairingRepository.completeKeyExchange(answer) as? ClientPairingRepository.KeyExchangeResult.Done ?: return
        nsClientRepository.addLog("◄ CLIENTCTL", "pairing secret replaced")
        aapsLogger.info(LTag.NSCLIENT, "ClientControl: the pairing secret was replaced by the key exchange")
        val token = result.token
        if (token != null && token != preferences.get(StringKey.NsClientAccessToken)) {
            if (tokenWorks(preferences.get(StringKey.NsClientUrl), token)) {
                // Restarts the connection with the new token.
                preferences.put(StringKey.NsClientAccessToken, token)
                nsClientRepository.addLog("◄ CLIENTCTL", "own Nightscout token from the master")
            } else
                aapsLogger.error(LTag.NSCLIENT, "ClientControl: the Nightscout token from the master does not work, keeping the one in use")
        }
        // Tells the master the new secret is in use, so it drops the old one and deletes its answer. Sent at once: the
        // probe after a reconnect is rate limited. Through the old connection if the restart is not done yet, which
        // still works; a lost ping only means the master learns it from the next message instead.
        roundTrip().sendPing()
    }

    private suspend fun loginWorks(url: String, token: String): Boolean {
        val client = NSAndroidClientImpl(baseUrl = nsClientBaseUrl(url), accessToken = token, logging = false, logger = {})
        return try {
            client.getStatus().apiPermissions.isClient()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: login with the token from the master failed: ${e.message}")
            false
        } finally {
            client.close()
        }
    }
}
