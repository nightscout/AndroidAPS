package app.aaps.plugins.eversense.util

import app.aaps.plugins.eversense.storage.EversenseStore
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock

class EversenseLoggerTest {
    @TempDir lateinit var directory: Path
    private val instanceField = EversenseLogger::class.java.getDeclaredField("configuredInstance").apply { isAccessible = true }
    private var previous: Any? = null
    private lateinit var context: LoggerContext
    private class CaptureAppender : AppenderBase<ILoggingEvent>() {
        val list = mutableListOf<ILoggingEvent>()
        override fun append(event: ILoggingEvent) { list.add(event) }
    }
    private lateinit var events: CaptureAppender

    @BeforeEach fun setup() {
        previous = instanceField.get(null)
        EversenseLogger.setDiagnosticLogging(false)
        val logger = EversenseLogger::class.java.getDeclaredConstructor(String::class.java).apply { isAccessible = true }.newInstance(directory.toString())
        context = EversenseLogger::class.java.getDeclaredField("lc").apply { isAccessible = true }.get(logger) as LoggerContext
        val root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        root.detachAndStopAllAppenders()
        events = CaptureAppender().apply { start() }
        root.addAppender(events)
        instanceField.set(null, logger)
    }
    @AfterEach fun cleanup() {
        EversenseLogger.setDiagnosticLogging(false)
        context.stop()
        instanceField.set(null, previous)
    }
    @Test fun normalModeKeepsOperationalMessagesAndDoesNotFormatPacketDetails() {
        var formatted = false
        EversenseLogger.debug("test") { formatted = true; "raw packet data" }
        EversenseLogger.info("test", "connected")
        EversenseLogger.warning("test", "retrying")
        EversenseLogger.error("test", "link failed")
        assertFalse(formatted)
        assertEquals(listOf("connected", "retrying", "link failed"), events.list.map { it.formattedMessage })
    }
    @Test fun diagnosticSwitchTakesEffectImmediatelyAndStopsFormattingWhenDisabled() {
        var formatted = 0
        EversenseLogger.setDiagnosticLogging(true)
        EversenseLogger.debug("test") { formatted++; "packet" }
        EversenseLogger.setDiagnosticLogging(false)
        EversenseLogger.debug("test") { formatted++; "suppressed" }
        EversenseLogger.info("test", "still connected")
        assertEquals(1, formatted)
        assertEquals(listOf("packet", "still connected"), events.list.map { it.formattedMessage })
    }
    @Test fun aapsDiagnosticBlocksAreAlsoGated() {
        var emitted = 0
        EversenseLogger.diagnostic { emitted++ }
        EversenseLogger.setDiagnosticLogging(true)
        EversenseLogger.diagnostic { emitted++ }
        EversenseLogger.setDiagnosticLogging(false)
        EversenseLogger.diagnostic { emitted++ }
        assertEquals(1, emitted)
    }
    @Test fun derivingSessionKeyDoesNotWriteItsValueEvenInDiagnosticMode() {
        EversenseLogger.setDiagnosticLogging(true)
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val local = generator.generateKeyPair()
        val remote = generator.generateKeyPair().public as ECPublicKey
        val crypto = EversenseCrypto365Util(mock<EversenseStore>())
        EversenseCrypto365Util::class.java.getDeclaredField("ephemPrivate").apply { isAccessible = true; set(crypto, local.private) }
        EversenseCrypto365Util::class.java.getDeclaredField("ephemSalt").apply { isAccessible = true; set(crypto, ByteArray(8) { 1 }) }
        fun coordinate(value: java.math.BigInteger) = value.toByteArray().takeLast(32).toByteArray().let { ByteArray(32 - it.size) + it }
        crypto.generateSessionKey(coordinate(remote.w.affineX) + coordinate(remote.w.affineY))
        val key = EversenseCrypto365Util::class.java.getDeclaredField("sessionKey").apply { isAccessible = true }.get(crypto) as? ByteArray
        assertNotNull(key)
        assertEquals(16, key!!.size)
        val keyHex = key.joinToString("") { "%02x".format(it) }
        assertTrue(events.list.none { it.formattedMessage.contains(keyHex, ignoreCase = true) })
    }
}
