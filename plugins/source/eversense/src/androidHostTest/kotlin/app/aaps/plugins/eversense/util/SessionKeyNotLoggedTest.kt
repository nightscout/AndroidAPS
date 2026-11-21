package app.aaps.plugins.eversense.util

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.plugins.eversense.storage.EversenseStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val local = generator.generateKeyPair()
        val remote = generator.generateKeyPair().public as ECPublicKey
        val crypto = EversenseCrypto365Util(mock<EversenseStore>(), logger)
        EversenseCrypto365Util::class.java.getDeclaredField("ephemPrivate").apply { isAccessible = true; set(crypto, local.private) }
        EversenseCrypto365Util::class.java.getDeclaredField("ephemSalt").apply { isAccessible = true; set(crypto, ByteArray(8) { 1 }) }
        fun coordinate(value: BigInteger) = value.toByteArray().takeLast(32).toByteArray().let { ByteArray(32 - it.size) + it }

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
