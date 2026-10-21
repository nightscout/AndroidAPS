package app.aaps.core.nssdk.localmodel.clientcontrol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Master-local record of a client allowed (or pending allowance) to send
 * commands. Persisted as JSON inside `StringNonKey.NsClientControlAuthorizedClients`.
 *
 * `encryptedSecret` is the SecureEncrypt-wrapped HMAC secret (AndroidKeyStore-
 * backed AES/GCM). The plaintext bytes never sit in shared preferences, so a
 * leaked prefs file is useless without TEE access.
 *
 * Lifecycle:
 * - `Pending` from PIN generation; auto-pruned after `pairExpiresAt`.
 * - `Active` after first signed `hello` verifies; `lastSeenAt` updated on
 *   every accepted command.
 *
 * `counterReceived` is the highest counter value already accepted from this
 * client — strictly increasing.
 */
@Serializable
data class AuthorizedClient(
    val clientId: String,
    val name: String,
    val encryptedSecret: String,
    val state: ClientState,
    val createdAt: Long,
    val lastSeenAt: Long = 0L,
    /**
     * Highest command counter already accepted from this client. Defaults to `0L` for a freshly
     * paired (or legacy) entry, so the very first command — which carries counter ≥ 1 — passes the
     * strictly-greater replay gate. The receiver accepts a command only when its counter is
     * STRICTLY greater than this, then advances this to it; a replayed command (counter ≤ this) is
     * rejected.
     */
    val counterReceived: Long = 0L,

    @SerialName("pairExpiresAt")
    val pairExpiresAt: Long = 0L,

    /**
     * Name of the client's role (`ClientRole` in core/keys). Kept as a plain string so that a role this
     * build does not know can never fail to decode: a decode failure would read the whole stored list as
     * empty. Null - a client stored before roles existed - means Admin, the full control it had.
     */
    val role: String? = null,

    /**
     * Name of the Nightscout token (the subject) this client writes with. Nightscout puts it on every document the
     * client writes, so the master learns it from each verified message; for a token the master made, it is known from
     * the start. Null until then. Removing the client also deletes this token, if it is a client token.
     */
    val nsSubject: String? = null
)

@Serializable
enum class ClientState {

    @SerialName("Pending") Pending,
    @SerialName("Active") Active
}
