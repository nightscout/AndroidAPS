package app.aaps.plugins.eversense.protocol

import app.aaps.plugins.eversense.storage.EversenseStore
import app.aaps.plugins.eversense.util.EversenseCrypto365Util
import app.aaps.shared.tests.AAPSLoggerTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.Random

/**
 * The 365's encrypted channel (AES-CCM built here from AES blocks) against digests of what the first driver produced
 * (see [GoldenDigests]): ciphertexts, the message counter and its rollover, and the outcome of broken material and
 * damaged messages.
 */
class SecureChannelGoldenTest {

    private val golden = GoldenDigests("secure-channel")

    private fun session(salt: ByteArray?, key: ByteArray?, sequence: Int) = EversenseCrypto365Util(mock<EversenseStore>(), AAPSLoggerTest()).also { crypto ->
        for ((name, value) in listOf("ephemSalt" to salt, "sessionKey" to key, "messageSequenceNumber" to sequence)) {
            crypto.javaClass.getDeclaredField(name).apply { isAccessible = true; set(crypto, value) }
        }
    }

    private fun EversenseCrypto365Util.sequence(): Int = javaClass.getDeclaredField("messageSequenceNumber").apply { isAccessible = true }.getInt(this)

    private fun outcome(operation: () -> ByteArray): String = try {
        operation().joinToString(",")
    } catch (error: Exception) {
        error.javaClass.simpleName
    }

    @Test fun ciphertextsAndCounterMatchTheFirstDriver() {
        val random = Random(365191)
        for (start in listOf(0, 1, 16382, 16383, 16384, 65535, Int.MAX_VALUE, Int.MIN_VALUE)) {
            for (size in listOf(0, 1, 2, 15, 16, 17, 64, 185, 512)) {
                val key = ByteArray(16).also(random::nextBytes)
                val salt = ByteArray(8).also(random::nextBytes)
                val crypto = session(salt, key, start)
                repeat(4) {
                    val plaintext = ByteArray(size).also(random::nextBytes)
                    val ciphertext = crypto.encrypt(plaintext)
                    golden.add("ciphertext", ciphertext)
                    golden.add("counter", crypto.sequence())
                    assertArrayEquals(plaintext, crypto.decrypt(ciphertext))
                    val damaged = ciphertext.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
                    golden.add("damaged", crypto.decrypt(damaged))
                }
            }
        }
        golden.check()
    }

    @Test fun brokenMaterialAndShortMessagesFailAsInTheFirstDriver() {
        for (salt in listOf(null, ByteArray(8))) for (key in listOf(null, ByteArray(0), ByteArray(15), ByteArray(16))) {
            val crypto = session(salt, key, 1)
            for (bytes in listOf(byteArrayOf(), byteArrayOf(1), byteArrayOf(1, 2), ByteArray(10))) {
                golden.add("encrypt", outcome { crypto.encrypt(bytes) })
                golden.add("encrypt", crypto.sequence())
                golden.add("decrypt", outcome { crypto.decrypt(bytes) })
                golden.add("decrypt", crypto.sequence())
            }
        }
        golden.check()
    }
}
