/*
 * KEKS J-PAKE / ExtraData authentication for Dexcom G7 / ONE+.
 *
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

import android.util.Log
import java.security.InvalidParameterException
import java.security.SecureRandom
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

private val secureRandom = SecureRandom()

internal actual fun keksRandomBytes(out: ByteArray) {
    secureRandom.nextBytes(out)
}

/** Byte for byte what the Java `AESwrapper` did, including answering null on any failure. */
internal actual fun keksAesEcbEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray? =
    try {
        val aesCipher = Cipher.getInstance("AES/ECB/NoPadding")
        val secretKeySpec = SecretKeySpec(key, "AES")
        aesCipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
        aesCipher.doFinal(plaintext, 0, plaintext.size)
    } catch (e: Exception) {
        null
    }

internal actual fun keksLogWrite(tag: String, message: String) {
    Log.d(tag, message)
}

internal actual class KeksLock actual constructor() {
    private val delegate = ReentrantLock()
    actual fun lock() = delegate.lock()
    actual fun unlock() = delegate.unlock()
}

actual typealias KeksSecurityException = SecurityException

actual typealias KeksInvalidParameterException = InvalidParameterException

actual typealias KeksArrayIndexOutOfBoundsException = ArrayIndexOutOfBoundsException
