package app.aaps.core.nssdk.localmodel.clientcontrol

import kotlinx.serialization.Serializable

/**
 * Contents of the pairing QR shown by the master and scanned by the client.
 *
 * Carries everything a client needs to enroll: identity of the master, the
 * clientId the master assigned to this pairing, the shared HMAC secret, and
 * a hard expiry after which the master refuses the resulting `hello`.
 *
 * Authorization is binary per-client: a paired client can issue any command
 * the master supports. A revocable list of permissions is intentionally not
 * modeled — keeps the surface small and the audit story simple (one switch
 * to revoke a client).
 *
 * The QR is sensitive — the screen showing it should be FLAG_SECURE and not
 * leak via screenshots. Treat the JSON as a one-time bearer token.
 */
@Serializable
data class PairingPayload(
    val v: Int = 1,
    val masterInstallId: String,
    val clientId: String,
    /** SENSITIVE — the raw shared HMAC secret (hex). Never log this field. */
    val secretHex: String,
    val expiresAt: Long
)
