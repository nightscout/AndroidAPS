package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IncarnationInquireResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is IncarnationInquireResponsePacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
                it.diaconnG8Pump = diaconnG8Pump
                it.rh = rh
            }
        }
    }

    @BeforeEach
    fun setup() {
        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
    }

    @Test
    fun handleMessageShouldParseIncarnationNumber() {
        // Given - Incarnation number 12345
        val packet = IncarnationInquireResponsePacket(packetInjector)
        val data = createValidPacket(incarnationNum = 12345)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.pumpIncarnationNum).isEqualTo(12345)
    }

    @Test
    fun handleMessageShouldHandleZeroIncarnation() {
        // Given - Zero incarnation (new pump)
        val packet = IncarnationInquireResponsePacket(packetInjector)
        val data = createValidPacket(incarnationNum = 0)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.pumpIncarnationNum).isEqualTo(0)
    }

    @Test
    fun handleMessageShouldHandleLargeIncarnationNumber() {
        // Given - Large incarnation number
        val packet = IncarnationInquireResponsePacket(packetInjector)
        val data = createValidPacket(incarnationNum = 65000)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.pumpIncarnationNum).isEqualTo(65000)
    }

    @Test
    fun handleMessageShouldHandleMaxIncarnationNumber() {
        // Given - Maximum short value
        val packet = IncarnationInquireResponsePacket(packetInjector)
        val data = createValidPacket(incarnationNum = 65535)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.pumpIncarnationNum).isEqualTo(65535)
    }

    @Test
    fun handleMessageShouldFailOnInvalidResult() {
        // Given
        val packet = IncarnationInquireResponsePacket(packetInjector)
        val data = createPacketWithResult(17) // CRC error

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = IncarnationInquireResponsePacket(packetInjector)
        val data = ByteArray(20)
        data[0] = 0x00 // Wrong SOP

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = IncarnationInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0xBA.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = IncarnationInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_INCARNATION_INQUIRE_RESPONSE")
    }

    private fun createValidPacket(incarnationNum: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0xBA.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = 16.toByte()   // result (success)
        data[5] = (incarnationNum and 0xFF).toByte() // incarnation low byte
        data[6] = ((incarnationNum shr 8) and 0xFF).toByte() // incarnation high byte

        for (i in 7 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }

    private fun createPacketWithResult(result: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0xBA.toByte()
        data[2] = 0x01.toByte()
        data[3] = 0x00.toByte()
        data[4] = result.toByte()

        for (i in 5 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }
}
