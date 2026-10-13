package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RejectReportPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is RejectReportPacket) {
                it.aapsLogger = aapsLogger
                it.diaconnG8Pump = diaconnG8Pump
            }
        }
    }

    @BeforeEach
    fun setup() {
        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
    }

    @Test
    fun handleMessageShouldProcessCancelReason() {
        // Given - Reject with cancel reason (6)
        val packet = RejectReportPacket(packetInjector)
        val data = createValidPacket(reqMsgType = 0x52, reason = 6)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(packet.reqMsgType).isEqualTo(0x52)
        assertThat(packet.reason).isEqualTo(6) // Cancel
    }

    @Test
    fun handleMessageShouldProcessTimeoutReason() {
        // Given - Reject with timeout reason (10)
        val packet = RejectReportPacket(packetInjector)
        val data = createValidPacket(reqMsgType = 0x45, reason = 10)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(packet.reqMsgType).isEqualTo(0x45)
        assertThat(packet.reason).isEqualTo(10) // Timeout
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = RejectReportPacket(packetInjector)
        val data = ByteArray(20)
        data[0] = 0x00 // Wrong SOP

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = RejectReportPacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_REJECT_REPORT")
    }

    private fun createValidPacket(reqMsgType: Int, reason: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0xE2.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = reqMsgType.toByte()
        data[5] = reason.toByte()

        // Fill rest with padding
        for (i in 6 until 19) {
            data[i] = 0xff.toByte()
        }

        // Calculate and set CRC
        data[19] = DiaconnG8Packet.getCRC(data, 19)

        return data
    }
}
