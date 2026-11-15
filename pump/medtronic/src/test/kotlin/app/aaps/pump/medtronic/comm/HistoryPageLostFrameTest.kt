package app.aaps.pump.medtronic.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.medtronic.comm.MedtronicCommunicationManager.Companion.pumpSkippedFrame
import app.aaps.pump.medtronic.comm.message.GetHistoryPageCarelinkMessageBody
import app.aaps.pump.medtronic.comm.message.PumpMessage
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * Seen on a Medtronic 522 (serial 678734): frame 7 of a history page was lost on the radio. The pump took the
 * next ACK as the ACK for frame 7 and sent frame 8, then 9, 10, 11 and 12 for the next ACKs. Frame 7 never came
 * again, and the read gave up after 6 tries.
 */
class HistoryPageLostFrameTest {

    private val aapsLogger: AAPSLogger = mock()

    /** A history frame as the pump sends it: header, command 0x80, frame byte, 64 bytes of data. */
    private fun frame(frameByte: Int): GetHistoryPageCarelinkMessageBody {
        val bytes = intArrayOf(0xA7, 0x67, 0x87, 0x34, 0x80, frameByte, *IntArray(64)).map { it.toByte() }.toByteArray()
        return GetHistoryPageCarelinkMessageBody(PumpMessage(aapsLogger, bytes).messageBody!!.txData)
    }

    @Test
    fun `a later frame means the expected one was lost`() {
        assertThat(pumpSkippedFrame(frame(0x08), expectedFrame = 7)).isTrue()
    }

    @Test
    fun `the last frame after a lost one is a skip too`() {
        // Frame 16 has the last-frame bit: 0x90.
        assertThat(pumpSkippedFrame(frame(0x90), expectedFrame = 15)).isTrue()
    }

    @Test
    fun `the expected frame is not a skip`() {
        assertThat(pumpSkippedFrame(frame(0x07), expectedFrame = 7)).isFalse()
    }

    /** An empty reply reads as frame 0. Sending the ACK again can still bring the expected frame. */
    @Test
    fun `an empty reply is not a skip`() {
        assertThat(pumpSkippedFrame(GetHistoryPageCarelinkMessageBody(byteArrayOf()), expectedFrame = 7)).isFalse()
    }

    @Test
    fun `an earlier frame is not a skip`() {
        assertThat(pumpSkippedFrame(frame(0x06), expectedFrame = 7)).isFalse()
    }
}
