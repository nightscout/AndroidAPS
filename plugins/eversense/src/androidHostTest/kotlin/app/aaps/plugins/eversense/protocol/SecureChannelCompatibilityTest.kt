package app.aaps.plugins.eversense.protocol

import app.aaps.plugins.eversense.storage.EversenseStore
import app.aaps.plugins.eversense.util.EversenseCrypto365Util
import java.lang.reflect.InvocationTargetException
import java.util.Random
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock

class SecureChannelCompatibilityTest {
    private val referenceType = Class.forName("app.aaps.plugins.eversense.reference.util.EversenseCrypto365Util")
    private fun reference(): Any = referenceType.constructors.single().let { it.newInstance(Mockito.mock(it.parameterTypes[0])) }
    private fun material(target: Any, salt: ByteArray?, key: ByteArray?, sequence: Int) {
        for ((name, value) in listOf("ephemSalt" to salt, "sessionKey" to key, "messageSequenceNumber" to sequence)) {
            target.javaClass.getDeclaredField(name).apply { isAccessible = true; set(target, value) }
        }
    }
    private fun call(target: Any, name: String, input: ByteArray): ByteArray = target.javaClass.getMethod(name, ByteArray::class.java).invoke(target, input) as ByteArray
    private fun sequence(target: Any): Int = target.javaClass.getDeclaredField("messageSequenceNumber").apply { isAccessible = true }.getInt(target)

    @Test fun authenticatedCiphertextAndCounterRolloverMatchFrozenSession() {
        val random = Random(365191)
        for (start in listOf(0, 1, 16382, 16383, 16384, 65535, Int.MAX_VALUE, Int.MIN_VALUE)) {
            for (size in listOf(0, 1, 2, 15, 16, 17, 64, 185, 512)) {
                val old = reference()
                val current = EversenseCrypto365Util(mock<EversenseStore>())
                val key = ByteArray(16).also(random::nextBytes)
                val salt = ByteArray(8).also(random::nextBytes)
                material(old, salt, key, start); material(current, salt, key, start)
                repeat(4) {
                    val plaintext = ByteArray(size).also(random::nextBytes)
                    val expected = call(old, "encrypt", plaintext)
                    val actual = current.encrypt(plaintext)
                    assertArrayEquals(expected, actual, "start=$start size=$size")
                    assertEquals(sequence(old), sequence(current))
                    assertArrayEquals(plaintext, call(old, "decrypt", actual))
                    assertArrayEquals(plaintext, current.decrypt(expected))
                    val damaged = actual.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
                    assertArrayEquals(call(old, "decrypt", damaged), current.decrypt(damaged))
                }
            }
        }
    }
    @Test fun missingMaterialTruncatedMessagesAndInvalidKeySizesRetainFailureOutcomes() {
        for (salt in listOf(null, ByteArray(8))) for (key in listOf(null, ByteArray(0), ByteArray(15), ByteArray(16))) {
            val old = reference(); val current = EversenseCrypto365Util(mock<EversenseStore>())
            material(old, salt, key, 1); material(current, salt, key, 1)
            for (bytes in listOf(byteArrayOf(), byteArrayOf(1), byteArrayOf(1,2), ByteArray(10))) {
                for (method in listOf("encrypt", "decrypt")) {
                    assertEquals(outcome { call(old, method, bytes) }, outcome { call(current, method, bytes) }, "$method with ${bytes.size} bytes")
                    assertEquals(sequence(old), sequence(current))
                }
            }
        }
    }
    private fun outcome(operation: () -> ByteArray): String = try { operation().joinToString(",") } catch (error: Exception) {
        val cause = if (error is InvocationTargetException) error.targetException else error
        cause.javaClass.simpleName
    }
}
