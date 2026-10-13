package app.aaps.pump.medtronic.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.medtronic.comm.MedtronicCommunicationManager.Companion.NAK_PAGE_DOES_NOT_EXIST
import app.aaps.pump.medtronic.comm.MedtronicCommunicationManager.Companion.nakErrorCode
import app.aaps.pump.medtronic.comm.message.PumpMessage
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * A Medtronic answers a request for a history page it does not have with a NAK. Its error code was read as a
 * frame number ("expected frame 1, received 13"), and every history read then lost about 80 s in timeouts.
 * The packets are from a real Medtronic 522 (serial 678734).
 */
class HistoryPageNakTest {

    private val aapsLogger: AAPSLogger = mock()

    private fun reply(vararg bytes: Int) = PumpMessage(aapsLogger, bytes.map { it.toByte() }.toByteArray())

    @Test
    fun `a page that does not exist is a NAK with error 0x0D`() {
        val nak = reply(0xA7, 0x67, 0x87, 0x34, 0x15, 0x0D)

        assertThat(nakErrorCode(nak)).isEqualTo(NAK_PAGE_DOES_NOT_EXIST)
    }

    @Test
    fun `a history frame is not a NAK`() {
        val frame = reply(0xA7, 0x67, 0x87, 0x34, 0x80, 0x01, *IntArray(64))

        assertThat(nakErrorCode(frame)).isNull()
    }

    @Test
    fun `an ACK is not a NAK`() {
        val ack = reply(0xA7, 0x67, 0x87, 0x34, 0x06, 0x00)

        assertThat(nakErrorCode(ack)).isNull()
    }
}
