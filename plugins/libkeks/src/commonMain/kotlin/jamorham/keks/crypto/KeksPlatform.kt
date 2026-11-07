/*
 * KEKS J-PAKE / ExtraData authentication for Dexcom G7 / ONE+.
 *
 * The handful of things the KEKS sources need from the platform underneath them.
 * Translated to Kotlin from NightscoutFoundation/xDrip
 *   https://github.com/NightscoutFoundation/xDrip
 *   pin commit 1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f (tag 2026.07.15)
 * See plugins/libkeks/NOTICE.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Part of AndroidAPS (AGPL-3.0-or-later); these KEKS sources retain their
 * upstream GPL-3.0 terms. Free software, distributed WITHOUT ANY WARRANTY.
 * See https://www.gnu.org/licenses/gpl-3.0.html
 */
package jamorham.keks.crypto

/**
 * Fills [out] with bytes from the platform's own secure generator.
 *
 * On Android this is the same `java.security.SecureRandom` the Java code used.
 */
internal expect fun keksRandomBytes(out: ByteArray)

/**
 * One or more AES blocks encrypted in ECB mode with no padding.
 *
 * Returns null when the platform refuses, which is what the Java `AESwrapper` did: it caught
 * everything and answered null, and `Calc.calculateHash` turns that null into a failed handshake.
 *
 * AES is a fixed answer, so this is a real implementation on every platform and never a stub.
 */
internal expect fun keksAesEcbEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray?

/** Writes one KEKS log line on the platform the handshake is running on. */
internal expect fun keksLogWrite(tag: String, message: String)

/**
 * A lock that the same thread may take again, standing in for Java's `synchronized`.
 *
 * This module has no project dependencies on purpose - it is third party code translated from xDrip - so it
 * carries its own two line lock rather than reaching for `AapsLock` in `:core:interfaces`.
 */
internal expect class KeksLock() {
    fun lock()
    fun unlock()
}

internal inline fun <T> KeksLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}

/*
 * The three exceptions below are the only public part of this file. Everything else here is
 * internal, because it is machinery the handshake uses on itself. These three leave the module:
 * `OnePlusSessionAuthKeks` in `:plugins:dexcom_oneplus` names each of them in a `catch` and turns
 * it into an auth failure the session retry can act on. That driver is shared code now, so it
 * cannot fall back to the Android type names the way it did while it was Android only.
 */

/**
 * The exception the handshake raises when a reply does not match the challenge it answers.
 *
 * On Android this really is `java.lang.SecurityException`, because the ONE+ driver catches that
 * type by name and turns it into "mismatch, wait" rather than a crash.
 */
expect class KeksSecurityException(message: String) : RuntimeException

/**
 * The exception the handshake raises when the certificate material is missing or wrong.
 *
 * On Android this really is `java.security.InvalidParameterException`, for the same reason: the
 * ONE+ driver catches that type by name, and reads "Missing QR" out of its message to decide
 * whether to throw the stored shared key away.
 */
expect class KeksInvalidParameterException(message: String) : IllegalArgumentException

/**
 * The exception a short packet raises where a challenge was expected.
 *
 * On Android this really is `java.lang.ArrayIndexOutOfBoundsException`, which is the type the Java
 * `System.arraycopy` raised and which the ONE+ driver catches by name. Only the message differs:
 * the one here names the lengths rather than repeating the JVM's wording, and the driver puts that
 * message in a log line and nowhere else.
 */
expect class KeksArrayIndexOutOfBoundsException(message: String) : IndexOutOfBoundsException
