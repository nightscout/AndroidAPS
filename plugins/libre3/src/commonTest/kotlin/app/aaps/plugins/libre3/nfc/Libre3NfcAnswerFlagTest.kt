package app.aaps.plugins.libre3.nfc

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The ISO 15693 answer flag check, which used to sit inside the Android reader.
 *
 * It is protocol and not platform - the same check whichever radio carried the bytes - so it runs
 * on every target now.
 */
class Libre3NfcAnswerFlagTest {

    @Test
    fun aGoodAnswerIsHandedBackUnchanged() {
        val answer = byteArrayOf(0x00, 0x11, 0x22)
        assertSame(answer, Libre3NfcCommands.checkAnswerFlag(answer))
    }

    @Test
    fun anEmptyAnswerIsALostLink() {
        val failure = assertFailsWith<Libre3NfcException> { Libre3NfcCommands.checkAnswerFlag(ByteArray(0)) }
        assertSame(Libre3NfcFailure.LINK_LOST, failure.failure)
    }

    @Test
    fun aRefusalCarriesTheErrorCodeInUpperCaseHex() {
        val failure = assertFailsWith<Libre3NfcException> {
            Libre3NfcCommands.checkAnswerFlag(byteArrayOf(0x01, 0x0F))
        }
        assertSame(Libre3NfcFailure.SENSOR_REFUSED, failure.failure)
        assertTrue(failure.message!!.contains("0x0F"), "the log line must name the error code: ${failure.message}")
    }

    @Test
    fun aRefusalWithNoCodeSaysSo() {
        val failure = assertFailsWith<Libre3NfcException> { Libre3NfcCommands.checkAnswerFlag(byteArrayOf(0x01)) }
        assertSame(Libre3NfcFailure.SENSOR_REFUSED, failure.failure)
        assertTrue(failure.message!!.contains("unknown"), "the log line must say the code is unknown")
    }
}
