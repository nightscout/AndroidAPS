package app.aaps.plugins.libre3.crypto

/**
 * Where the driver's random bytes come from.
 *
 * `java.security.SecureRandom` is JVM only, which is the only reason this exists. A test passes a
 * fixed source so a pairing can be replayed; production always passes the platform's own secure
 * random, see [Libre3RandomSource.secure].
 */
fun interface Libre3RandomSource {

    /** Fills the whole array with random bytes. */
    fun nextBytes(out: ByteArray)

    companion object {

        /** The platform's own secure random. */
        fun secure(): Libre3RandomSource = libre3SecureRandom()
    }
}

/**
 * The platform's own secure random.
 *
 * ⚠️ This must be a real one. The entropy drawn here becomes the phone's scalar of a first
 * pairing, so a predictable source would make the session key predictable too.
 */
internal expect fun libre3SecureRandom(): Libre3RandomSource
