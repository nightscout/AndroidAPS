package app.aaps.pump.omnipod.omnipod5.bledriver.pod.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

/**
 * Real instrumented test for [AndroidKeystoreAesCipher] - the one piece of cryptography
 * built this session that genuinely could not be verified in the development sandbox,
 * since `AndroidKeyStore` is a real Android OS provider that only exists on an actual
 * device or emulator. Run this via Android Studio's test runner (or
 * `./gradlew :pump:omnipod:omnipod5:connectedFullDebugAndroidTest`) against a real device or
 * emulator to close that gap for real, rather than relying on code review alone for a class
 * that handles actual key material.
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreAesCipherTest {

    private val random = SecureRandom()

    @Test
    fun encryptDecryptRoundTrip_variousSizes() {
        val cipher = AndroidKeystoreAesCipher()

        repeat(20) { i ->
            val plaintext = ByteArray(1 + i * 7).also { random.nextBytes(it) }
            val encrypted = cipher.encrypt(plaintext)
            val decrypted = cipher.decrypt(encrypted)
            assertThat(decrypted).isEqualTo(plaintext)
        }
    }

    @Test
    fun emptyPlaintext_roundTrips() {
        val cipher = AndroidKeystoreAesCipher()
        val encrypted = cipher.encrypt(ByteArray(0))
        val decrypted = cipher.decrypt(encrypted)
        assertThat(decrypted).isEqualTo(ByteArray(0))
    }

    @Test
    fun sameEncryptedValue_decryptsDifferentlyEachTime_dueToRandomIv() {
        val cipher = AndroidKeystoreAesCipher()
        val plaintext = "same plaintext both times".toByteArray()

        val encrypted1 = cipher.encrypt(plaintext)
        val encrypted2 = cipher.encrypt(plaintext)

        assertThat(encrypted1).isNotEqualTo(encrypted2)
        assertThat(cipher.decrypt(encrypted1)).isEqualTo(plaintext)
        assertThat(cipher.decrypt(encrypted2)).isEqualTo(plaintext)
    }

    @Test
    fun keyPersistsAcrossSeparateCipherInstances() {
        val cipherBeforeRestart = AndroidKeystoreAesCipher()
        val plaintext = "must survive a simulated restart".toByteArray()
        val encrypted = cipherBeforeRestart.encrypt(plaintext)

        val cipherAfterRestart = AndroidKeystoreAesCipher()
        val decrypted = cipherAfterRestart.decrypt(encrypted)

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test(expected = Exception::class)
    fun tamperedCiphertext_failsToDecrypt() {
        val cipher = AndroidKeystoreAesCipher()
        val plaintext = "sensitive credential material".toByteArray()
        val encrypted = cipher.encrypt(plaintext)

        val raw = java.util.Base64.getDecoder().decode(encrypted)
        raw[raw.size - 1] = (raw[raw.size - 1] + 1).toByte()
        val tampered = java.util.Base64.getEncoder().encodeToString(raw)

        cipher.decrypt(tampered)
    }

    @Test(expected = Exception::class)
    fun malformedBase64_failsToDecryptRatherThanCrashingUnpredictably() {
        val cipher = AndroidKeystoreAesCipher()
        cipher.decrypt("not valid base64 at all !!!")
    }
}
