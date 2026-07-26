package app.aaps.core.nssdk.localmodel.clientcontrol

/**
 * Client-local snapshot of a successful pairing. Reconstructed from the
 * individual `NsClientControl*` non-keys for convenience; not a single
 * persisted blob (each field has its own preference for clean migration).
 *
 * `masterSecretEnc` mirrors [AuthorizedClient.encryptedSecret] on the master:
 * SecureEncrypt-wrapped HMAC secret bytes, opaque without TEE access.
 */
data class MasterPairing(
    val masterInstallId: String,
    val clientId: String,
    val masterSecretEnc: String
)
