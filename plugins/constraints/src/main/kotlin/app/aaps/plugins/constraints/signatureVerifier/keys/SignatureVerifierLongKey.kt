package app.aaps.plugins.constraints.signatureVerifier.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class SignatureVerifierLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    LastRevokedCertCheck("last_revoked_certs_check", 0L),
}
