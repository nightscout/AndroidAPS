package app.aaps.plugins.constraints.signatureVerifier.keys

import app.aaps.core.keys.LongNonPreferenceKey

enum class SignatureVerifierLongKey(
    override val key: String,
    override val defaultValue: Long,
) : LongNonPreferenceKey {

    LastRevokedCertCheck("last_revoked_certs_check", 0L),
}
