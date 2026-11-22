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
import java.nio.ByteBuffer

/**
 * [Session.validateSequenceNumber] - the O5-only response-envelope sequence check that runs
 * alongside `Session.validateCrc` (covered separately in [SessionResponseCrcTest]).
 *
 * The rule under test is that a pod's response carries the request's sequence number **plus
 * one**, which is pinned below against real request/response pairs captured from a working
 * Loop/Trio installation's Device Communication Log. An earlier revision compared against the
 * request's own sequence number and would have rejected every response a real pod ever sent.
 *
 * Note these particular pairs are **Dash** traffic (`podTypeValue: 4` in the source report) -
 * the sequence rule is identical on both pod generations, unlike the CRC.
 */
class SessionResponseValidationTest {

    private fun session(): Session {
        val ids = Ids.forController(Id.fromInt(1), Id.fromInt(2))
        val nonce = Nonce(ByteArray(8), 0)
        val sessionKeys = SessionKeys(ck = ByteArray(16), nonce = nonce, msgSequenceNumber = 1)
        val enDecrypt = EnDecrypt(AAPSLoggerTest(), nonce, sessionKeys.ck)
        return Session(AAPSLoggerTest(), mock<MessageIO>(), ids, sessionKeys, enDecrypt)
    }

    private fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** Bytes 4..6 of an inner Omnipod message: uniqueId(4) + length/sequence(2) + body + CRC(2). */
    private fun lengthAndSequenceBytesOf(message: String): ByteArray = hex(message).copyOfRange(4, 6)

    private fun sequenceNumberOf(message: String): Short =
        (((ByteBuffer.wrap(lengthAndSequenceBytesOf(message)).short.toInt() shr 10) and 0x0f)).toShort()

    /**
     * Real Omnipod 5 traffic (pod address 1749dbcb), request paired with the response it drew.
     * Includes a request at sequence 14 so the pairing is pinned near the 4-bit wrap, not only
     * for small numbers.
     */
    private val capturedPairs = listOf(
        "1749dbcb38071f05494e532e028173" to "1749dbcb3c0a1d180403700000472fff0310",
        "1749dbcb00201a0e494e532e01008501384000060006160e0000003c01c9c380003c01c9c380010c" to
            "1749dbcb040a1d280403000000472fff83db",
        "1749dbcb04030e010782e2" to "1749dbcb080a1d280403000000473bff8219",
        "1749dbcb08030e010700e7" to "1749dbcb0c0a1d280403800000474bff03c9",
        "1749dbcb0c030e010781ec" to "1749dbcb100a1d2804038000004757ff0192"
    )

    @Test
    fun `real captured O5 traffic - every response carries the request sequence number plus one`() {
        val session = session()
        for ((request, response) in capturedPairs) {
            val sent = sequenceNumberOf(request)
            assertThat(sequenceNumberOf(response).toInt()).isEqualTo((sent.toInt() + 1) and 0x0f)
            session.validateSequenceNumber(lengthAndSequenceBytesOf(response), sent)
        }
    }

    @Test
    fun `a response echoing the request's own sequence number is rejected`() {
        val session = session()
        val (request, _) = capturedPairs.first()
        val sent = sequenceNumberOf(request)

        assertThrows(CouldNotParseResponseException::class.java) {
            session.validateSequenceNumber(lengthAndSequenceBytesOf(request), sent)
        }
    }

    @Test
    fun `sequence numbers wrap at 4 bits - a request at 15 expects a response at 0`() {
        val session = session()
        val response = packedLengthAndSequenceNumber(sequenceNumber = 0, length = 10, multiCommandFlag = false)

        session.validateSequenceNumber(response, sentSequenceNumber = 15)
    }

    @Test
    fun `the length and multiCommandFlag bits sharing the field are ignored`() {
        val session = session()
        val response = packedLengthAndSequenceNumber(sequenceNumber = 4, length = 1000, multiCommandFlag = true)

        session.validateSequenceNumber(response, sentSequenceNumber = 3)
    }

    @Test
    fun `a genuinely out-of-order response is rejected`() {
        val session = session()
        val response = packedLengthAndSequenceNumber(sequenceNumber = 9, length = 10, multiCommandFlag = false)

        assertThrows(CouldNotParseResponseException::class.java) {
            session.validateSequenceNumber(response, sentSequenceNumber = 3)
        }
    }

    /** Mirrors HeaderEnabledCommand.encodeHeader()'s second Short, minus the leading uniqueId. */
    private fun packedLengthAndSequenceNumber(sequenceNumber: Short, length: Short, multiCommandFlag: Boolean): ByteArray {
        val packed = (sequenceNumber.toInt() and 0x0f shl 10 or length.toInt() or ((if (multiCommandFlag) 1 else 0) shl 15)).toShort()
        return ByteBuffer.allocate(2).putShort(packed).array()
    }
}
