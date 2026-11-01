package app.aaps.plugins.libre3.crypto

import java.security.SecureRandom

/** Android secure random, the same `java.security.SecureRandom` the driver always used. */
internal actual fun libre3SecureRandom(): Libre3RandomSource {
    val random = SecureRandom()
    return Libre3RandomSource { out -> random.nextBytes(out) }
}
