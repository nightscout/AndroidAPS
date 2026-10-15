package app.aaps.pump.medtronic.comm

import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.pump.common.hw.rileylink.ble.RFSpy
import app.aaps.pump.common.hw.rileylink.ble.data.RFSpyResponse
import app.aaps.pump.common.hw.rileylink.ble.data.RadioPacket
import app.aaps.pump.common.hw.rileylink.ble.data.RadioResponse
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.common.utils.CRC
import app.aaps.pump.medtronic.MedtronicPumpPlugin
import app.aaps.pump.medtronic.comm.MedtronicCommunicationManager.Companion.FRAMES_PER_PAGE
import app.aaps.pump.medtronic.comm.MedtronicCommunicationManager.Companion.NAK_PAGE_DOES_NOT_EXIST
import app.aaps.pump.medtronic.comm.history.pump.MedtronicPumpHistoryDecoder
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntry
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryResult
import com.google.common.truth.Truth.assertThat
import org.joda.time.LocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Seen on a Medtronic 515 (#3378): page 0 of the history was read, but the pump did not answer the request for
 * page 1. The entries of page 0 were processed alone, and a temp basal was ended by the wrong entry. The entries
 * of page 1 came later as new, but older than the ones already processed, and a temp basal got a negative length.
 */
class HistoryReadFailedTest {

    private val pump = FakePump()
    private val decoder: MedtronicPumpHistoryDecoder = mock()
    private lateinit var communicationManager: MedtronicCommunicationManager

    /** The read asks for everything after 09:11:29. Page 0 starts later, so page 1 is needed too. */
    private val targetDate = LocalDateTime(2024, 7, 12, 9, 11, 29)

    @BeforeEach
    fun setUp() {
        val rileyLinkServiceData: RileyLinkServiceData = mock()
        whenever(rileyLinkServiceData.pumpIDBytes).thenReturn(PUMP_ID)
        val activePlugin: ActivePlugin = mock()
        whenever(activePlugin.activePumpInternal).thenReturn(mock<MedtronicPumpPlugin>())
        val rfspy: RFSpy = mock()
        whenever(rfspy.transmitThenReceive(any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer {
            radioReply(pump.answer(it.getArgument<RadioPacket>(0).pkt))
        }
        whenever(decoder.processPageAndCreateRecords(any())).thenAnswer { entriesOfPage(pump.lastSentPage) }

        communicationManager = MedtronicCommunicationManager(
            medtronicPumpStatus = mock(),
            medtronicPumpPlugin = mock(),
            medtronicConverter = mock(),
            medtronicUtil = mock(),
            medtronicPumpHistoryDecoder = decoder,
            aapsLogger = mock(),
            preferences = mock(),
            rileyLinkServiceData = rileyLinkServiceData,
            serviceTaskExecutor = mock(),
            rfspy = rfspy,
            activePlugin = activePlugin,
            rileyLinkUtil = mock(),
            wakeAndTuneTaskProvider = { mock() },
            radioResponseProvider = { mock() }
        )
        communicationManager.setDoWakeUpBeforeCommand(false)
    }

    private fun readHistory(): PumpHistoryResult = communicationManager.getPumpHistory(null, targetDate)

    @Test
    fun `a page the pump does not answer makes the whole read fail`() {
        pump.unansweredAttentions[1] = Int.MAX_VALUE

        val result = readHistory()

        assertThat(result.readFailed).isTrue()
        // Only the frames of page 0 were ACKed. A page that never started was ACKed until the read gave up (~70 s).
        assertThat(pump.acks).isEqualTo(FRAMES_PER_PAGE - 1)
    }

    @Test
    fun `a page request with no answer is sent again`() {
        pump.unansweredAttentions[1] = 1

        val result = readHistory()

        assertThat(result.readFailed).isFalse()
        assertThat(result.validEntries.map { it.atechDateTime })
            .containsExactly(20240712094629L, 20240712094627L, 20240712092939L)
    }

    @Test
    fun `no older page on the pump is a complete read`() {
        pump.pages = 1

        val result = readHistory()

        assertThat(result.readFailed).isFalse()
        assertThat(result.validEntries.map { it.atechDateTime }).containsExactly(20240712094629L, 20240712094627L)
    }

    /** Page 0 has only entries newer than the target date. Page 1 reaches back past it, so the search ends there. */
    private fun entriesOfPage(page: Int): MutableList<PumpHistoryEntry> =
        when (page) {
            0    -> listOf(20240712094627L, 20240712094629L)
            1    -> listOf(20240712090000L, 20240712092939L)
            else -> emptyList()
        }.map { PumpHistoryEntry().apply { atechDateTime = it } }.toMutableList()

    private fun radioReply(payload: ByteArray): RFSpyResponse {
        val radioResponse: RadioResponse = mock { on { getPayload() } doReturn payload }
        return mock { on { getRadioResponse() } doReturn radioResponse }
    }

    /**
     * A pump with [pages] history pages, each 16 frames of 64 bytes with a valid CRC. It answers the attention packet
     * with an ACK, the page request with frame 1 and each ACK with the next frame. The attention packet for a page is
     * left without an answer as often as [unansweredAttentions] says for that page, as on the radio in #3378.
     */
    private class FakePump {

        var pages = 2
        val unansweredAttentions = mutableMapOf<Int, Int>()
        var acks = 0
            private set
        var lastSentPage = -1
            private set

        private var nextPage = 0
        private var page = -1
        private var frame = 0
        private val pageData = ByteArray(1022).let { it + CRC.calculate16CCITT(it) }

        fun answer(sent: ByteArray): ByteArray =
            when (sent[4]) {
                GET_HISTORY -> if (sent.size == ATTENTION_SIZE) attention() else request(sent[6].toInt())
                ACK         -> {
                    acks++
                    nextFrame()
                }

                else        -> NO_ANSWER
            }

        private fun attention(): ByteArray {
            val left = unansweredAttentions[nextPage] ?: 0
            if (left > 0) {
                unansweredAttentions[nextPage] = left - 1
                return NO_ANSWER
            }
            return message(ACK, 0)
        }

        private fun request(pageNumber: Int): ByteArray {
            if (pageNumber >= pages) return message(NAK, NAK_PAGE_DOES_NOT_EXIST.toByte())
            page = pageNumber
            frame = 1
            return currentFrame()
        }

        private fun nextFrame(): ByteArray {
            if (page < 0) return NO_ANSWER
            frame++
            return currentFrame()
        }

        private fun currentFrame(): ByteArray {
            val last = frame == FRAMES_PER_PAGE
            val frameByte = if (last) frame or 0x80 else frame
            val data = pageData.copyOfRange((frame - 1) * 64, frame * 64)
            if (last) {
                lastSentPage = page
                nextPage = page + 1
                page = -1
            }
            return message(GET_HISTORY, frameByte.toByte(), *data)
        }

        private fun message(command: Byte, vararg body: Byte): ByteArray = byteArrayOf(0xA7.toByte(), *PUMP_ID, command, *body)
    }

    companion object {

        private val PUMP_ID = byteArrayOf(0x12, 0x34, 0x56)
        private const val GET_HISTORY = 0x80.toByte()
        private const val ACK: Byte = 0x06
        private const val NAK: Byte = 0x15

        /** Header, pump id, command and one byte: the attention packet. A page request is a long message. */
        private const val ATTENTION_SIZE = 6

        /** RileyLink heard nothing from the pump. */
        private val NO_ANSWER = byteArrayOf()
    }
}
