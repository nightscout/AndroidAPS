package app.aaps.plugins.dexcomoneplus.session

/**
 * Session authentication / J-PAKE key exchange.
 *
 * Production path: [OnePlusSessionAuthKeks] via `:plugins:libkeks` (GPL-3 xDrip pin). Both are
 * shared code; only OS bonding is platform specific, behind [app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattClient].
 * **No proprietary Dexcom secrets or hard-coded session keys belong in this module.**
 */
interface OnePlusSessionAuth {
    /**
     * Runs auth after GATT connect. Returns success/failure without throwing for expected auth fails.
     *
     * @param savedSharedKey optional 16-byte key from a prior session (libkeks channel 2 /
     * Juggluco reconnect short path). When present and Android-bonded, Round1–3 are skipped.
     */
    fun authenticate(pairingCode: String, savedSharedKey: ByteArray? = null): AuthResult
}

data class AuthResult(
    val ok: Boolean,
    val message: String? = null,
    /** 16-byte KEKS shared key when [ok] — persist for reconnect short-auth. */
    val sharedKey: ByteArray? = null,
    /**
     * Juggluco-style: wipe persisted shared key before the next attempt
     * (short-auth rejected / bond failure / missing certs).
     */
    val invalidateSharedKey: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuthResult) return false
        return ok == other.ok &&
            message == other.message &&
            sharedKey.contentEquals(other.sharedKey) &&
            invalidateSharedKey == other.invalidateSharedKey
    }

    override fun hashCode(): Int {
        var result = ok.hashCode()
        result = 31 * result + (message?.hashCode() ?: 0)
        result = 31 * result + (sharedKey?.contentHashCode() ?: 0)
        result = 31 * result + invalidateSharedKey.hashCode()
        return result
    }
}
