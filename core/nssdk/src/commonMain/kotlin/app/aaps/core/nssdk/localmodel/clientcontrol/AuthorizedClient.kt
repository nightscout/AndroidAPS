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
    val pairExpiresAt: Long = 0L
)

@Serializable
enum class ClientState {

    @SerialName("Pending") Pending,
    @SerialName("Active") Active
}
