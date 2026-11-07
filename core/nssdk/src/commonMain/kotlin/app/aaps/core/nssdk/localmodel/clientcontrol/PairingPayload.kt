package app.aaps.core.nssdk.localmodel.clientcontrol

import kotlinx.serialization.Serializable

/**
 * A pairing as the master hands it to a client: wrapped with the PIN in the offer doc, or inside the master's QR code
 * ([ClientSetupCode.pairing]).
 *
 * Carries everything a client needs to enroll: identity of the master, the
 * clientId the master assigned to this pairing, the shared HMAC secret, and
 * a hard expiry after which the master refuses the resulting `hello`.
 *
 * What the client may do is its role, kept and enforced on the master (`AuthorizedClient.role`). [role] only lets
 * the client show the right controls from the first moment, before the master's roster lists the new pairing.
 *
 * It is sensitive: the screen showing it should block screenshots. Treat the JSON as a one-time bearer token.
 */
@Serializable
data class PairingPayload(
    val v: Int = 1,
    val masterInstallId: String,
    val clientId: String,
    /** SENSITIVE — the raw shared HMAC secret (hex). Never log this field. */
    val secretHex: String,
    val expiresAt: Long,
    /** The role name (`ClientRole` in core/keys) the master gave this pairing. Null from a master that does not send it. */
    val role: String? = null,
    /**
     * The name this phone was added with on the master, for the PIN offer. The client takes it as its instance name when
     * it has none. In a QR code it is [ClientSetupCode.name] instead.
     */
    val name: String? = null
) {

    /** Hides the secret: the generated toString would print it, for example inside a logged [ClientSetupCode]. */
    override fun toString(): String =
        "PairingPayload(v=$v, masterInstallId=$masterInstallId, clientId=$clientId, secretHex=****, expiresAt=$expiresAt, role=$role, name=$name)"
}
