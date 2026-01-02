package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LogStatusInquireResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is LogStatusInquireResponsePacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
                it.diaconnG8Pump = diaconnG8Pump
            }
        }
    }

    @BeforeEach
    fun setup() {
        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
    }

    @Test
    fun handleMessageShouldParseLogStatus() {
        // Given - Last log 1500, wrapping count 5
        val packet = LogStatusInquireResponsePacket(packetInjector)
        val data = createValidPacket(lastLogNum = 1500, wrappingCount = 5)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.pumpLastLogNum).isEqualTo(1500)
        assertThat(diaconnG8Pump.pumpWrappingCount).isEqualTo(5)
    }

    @Test
    fun handleMessageShouldHandleZeroLogNumber() {
        // Given - No logs yet (new pump)
        val packet = LogStatusInquireResponsePacket(packetInjector)
        val data = createValidPacket(lastLogNum = 0, wrappingCount = 0)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.pumpLastLogNum).isEqualTo(0)
        assertThat(diaconnG8Pump.pumpWrappingCount).isEqualTo(0)
    }

    @Test
    fun handleMessageShouldHandleMaxLogNumber() {
        // Given - Maximum log storage (65535 logs)
        val packet = LogStatusInquireResponsePacket(packetInjector)
        val data = createValidPacket(lastLogNum = 65535, wrappingCount = 255)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.pumpLastLogNum).isEqualTo(65535)
        assertThat(diaconnG8Pump.pumpWrappingCount).isEqualTo(255)
    }

    @Test
    fun handleMessageShouldHandleLargeLogNumber() {
        // Given - Large log number after wrapping
        val packet = LogStatusInquireResponsePacket(packetInjector)
        val data = createValidPacket(lastLogNum = 50000, wrappingCount = 10)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.pumpLastLogNum).isEqualTo(50000)
        assertThat(diaconnG8Pump.pumpWrappingCount).isEqualTo(10)
    }

    @Test
    fun handleMessageShouldHandleMultipleWrappings() {
        // Given - Multiple wrappings (pump used for long time)
        val packet = LogStatusInquireResponsePacket(packetInjector)
        val data = createValidPacket(lastLogNum = 32000, wrappingCount = 100)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.pumpLastLogNum).isEqualTo(32000)
        assertThat(diaconnG8Pump.pumpWrappingCount).isEqualTo(100)
    }

    @Test
    fun handleMessageShouldFailOnInvalidResult() {
        // Given
        val packet = LogStatusInquireResponsePacket(packetInjector)
        val data = createPacketWithResult(17) // CRC error

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = LogStatusInquireResponsePacket(packetInjector)
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
        val packet = LogStatusInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0x96.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = LogStatusInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_LOG_STATUS_INQUIRE_RESPONSE")
    }

    private fun createValidPacket(lastLogNum: Int, wrappingCount: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0x96.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = 16.toByte()   // result (success)
        data[5] = (lastLogNum and 0xFF).toByte() // lastLogNum low byte
        data[6] = ((lastLogNum shr 8) and 0xFF).toByte() // lastLogNum high byte
        data[7] = wrappingCount.toByte()

        for (i in 8 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }

    private fun createPacketWithResult(result: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0x96.toByte()
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
