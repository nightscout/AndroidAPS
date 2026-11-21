package app.aaps.plugins.eversense.util

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.plugins.eversense.MemoryStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.mock
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/** The 365 session key protects every reading: it must never reach the AAPS log, which users export and share. */
class SessionKeyNotLoggedTest {

    @Test fun derivingTheSessionKeyDoesNotLogIt() {
        val logger = mock<AAPSLogger>()
        // The transmitter's side of the key agreement, as X || Y
        val remote = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair().public as ECPublicKey
        fun coordinate(value: BigInteger) = value.toByteArray().takeLast(32).toByteArray().let { ByteArray(32 - it.size) + it }
        val crypto = EversenseCrypto365Util(MemoryStore(), logger)
        assertTrue(crypto.generateKeyPairIfNotExists())
        assertNotNull(crypto.generateEphem())

        crypto.generateSessionKey(coordinate(remote.w.affineX) + coordinate(remote.w.affineY))

        val key = EversenseCrypto365Util::class.java.getDeclaredField("sessionKey").apply { isAccessible = true }.get(crypto) as? ByteArray
        assertNotNull(key)
        assertEquals(16, key!!.size)
        val keyHex = key.joinToString("") { "%02x".format(it) }
        // Every argument of every log call, with a lazy message evaluated
        val logged = mockingDetails(logger).invocations
            .flatMap { it.arguments.toList() }
            .joinToString(" ") { argument -> ((argument as? Function0<*>)?.invoke() ?: argument).toString() }
        assertFalse(logged.contains(keyHex, ignoreCase = true))
    }
}
