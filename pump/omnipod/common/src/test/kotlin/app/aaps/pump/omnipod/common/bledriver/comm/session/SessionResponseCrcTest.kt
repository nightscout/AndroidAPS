package app.aaps.pump.omnipod.common.bledriver.comm.session

import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.EnDecrypt
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.Nonce
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.CouldNotParseResponseException
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * [Session.validateCrc] - the O5-only response-envelope CRC check.
 *
 * Every vector below is real captured traffic, not synthesised, because the point of the test
 * is that the CRC is genuinely computable on Omnipod 5 responses. An earlier revision of the
 * driver asserted the opposite in a code comment and skipped the check entirely, on the
 * strength of Dash traffic that had been mistaken for O5.
 */
class SessionResponseCrcTest {

    private fun session(): Session {
        val ids = Ids.forController(Id.fromInt(1), Id.fromInt(2))
        val nonce = Nonce(ByteArray(8), 0)
        val sessionKeys = SessionKeys(ck = ByteArray(16), nonce = nonce, msgSequenceNumber = 1)
        val enDecrypt = EnDecrypt(AAPSLoggerTest(), nonce, sessionKeys.ck)
        return Session(AAPSLoggerTest(), mock<MessageIO>(), ids, sessionKeys, enDecrypt)
    }

    private fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /**
     * Omnipod 5 pod `002a1c6e`, captured from a Trio installation. Pod-generated responses -
     * the direction that matters, since these are what the driver acts on.
     */
    private val podResponses = listOf(
        "002a1c6e300a1d2803f04000001d83ff83db",
        "002a1c6e380a1d1803f06800001d83ff036a",
        "002a1c6e000a1d2803f07800001d83ff03eb",
        "002a1c6e301802160209020000010c92000000030d0cc8000000000070410258"
    )

    /** Same pod, phone-generated. This driver builds these itself, so they close the loop. */
    private val outgoingMessages = listOf(
        "002a1c6e2c030e0100802f",
        "002a1c6e34071f05494e532e02818b",
        "002a1c6e3c201a0e494e532e010091013840000c000c160e0000007d00dbba00007d00dbba008103"
    )

    /**
     * The two envelopes logged either side of a real `MessageError.invalidCrc` incident, where
     * five minutes of corrupted comms cost the user a loop cycle and raised a critical
     * delivery-uncertain alert. Both are well formed - the corrupt frames never reached the
     * log - so they belong with the passing vectors.
     */
    private val incidentEnvelopes = listOf(
        "002a1c6e08030e010002b6",
        "002a1c6e08030e010782a7"
    )

    @Test
    fun `real captured O5 pod responses all satisfy the envelope CRC`() {
        val session = session()
        for (message in podResponses) {
            session.validateCrc(hex(message))
        }
    }

    @Test
    fun `real captured O5 outgoing messages all satisfy the envelope CRC`() {
        val session = session()
        for (message in outgoingMessages) {
            session.validateCrc(hex(message))
        }
    }

    @Test
    fun `envelopes captured around a real invalidCrc incident are themselves valid`() {
        val session = session()
        for (message in incidentEnvelopes) {
            session.validateCrc(hex(message))
        }
    }

    @Test
    fun `a single flipped bit anywhere in the envelope is rejected`() {
        val session = session()
        val original = hex(podResponses.first())

        for (index in 0 until original.size - 2) {
            val corrupted = original.copyOf()
            corrupted[index] = (corrupted[index].toInt() xor 0x01).toByte()
            assertThrows(CouldNotParseResponseException::class.java) {
                session.validateCrc(corrupted)
            }
        }
    }

    @Test
    fun `a corrupted CRC field itself is rejected`() {
        val session = session()
        val corrupted = hex(podResponses.first())
        corrupted[corrupted.size - 1] = (corrupted[corrupted.size - 1].toInt() xor 0x01).toByte()

        assertThrows(CouldNotParseResponseException::class.java) {
            session.validateCrc(corrupted)
        }
    }

    @Test
    fun `the failure message reports both the expected and the received CRC`() {
        val session = session()
        val corrupted = hex(podResponses.first())
        corrupted[corrupted.size - 1] = 0x00

        val thrown = assertThrows(CouldNotParseResponseException::class.java) {
            session.validateCrc(corrupted)
        }
        assertThat(thrown.message).contains("83db")
        assertThat(thrown.message).contains("8300")
    }

    /**
     * Why [Session.parseResponse] gates this check on an O5 connection. These are Dash
     * responses (pod `1749dbcb`, `podTypeValue: 4` in the Loop Report they came from) and they
     * satisfy no CRC this driver can compute - no initial value in the entire 16-bit space and
     * no byte range reproduces their trailer. Running the check on a Dash connection would
     * reject every response the pod sends, which is exactly the regression this test guards.
     */
    @Test
    fun `Dash pod responses do NOT satisfy this CRC - hence the O5-only gate`() {
        val session = session()
        val dashResponses = listOf(
            "1749dbcb3c0a1d180403700000472fff0310",
            "1749dbcb040a1d280403000000472fff83db",
            "1749dbcb080a1d280403000000473bff8219",
            "1749dbcb0c0a1d280403800000474bff03c9",
            "1749dbcb100a1d2804038000004757ff0192"
        )
        for (message in dashResponses) {
            assertThrows(CouldNotParseResponseException::class.java) {
                session.validateCrc(hex(message))
            }
        }
    }

    /** Dash *outgoing* messages do satisfy it - this driver wrote them with the same function. */
    @Test
    fun `Dash outgoing messages satisfy the CRC even though its responses do not`() {
        val session = session()
        val dashRequests = listOf(
            "1749dbcb38071f05494e532e028173",
            "1749dbcb04030e010782e2",
            "1749dbcb08030e010700e7"
        )
        for (message in dashRequests) {
            session.validateCrc(hex(message))
        }
    }
}
