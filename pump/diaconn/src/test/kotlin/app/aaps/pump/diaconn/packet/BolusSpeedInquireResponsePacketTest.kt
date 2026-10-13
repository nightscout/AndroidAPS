package app.aaps.pump.diaconn.packet

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.keys.DiaconnIntKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify

class BolusSpeedInquireResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is BolusSpeedInquireResponsePacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
                it.diaconnG8Pump = diaconnG8Pump
                it.preferences = preferences
            }
        }
    }

    @BeforeEach
    fun setup() {
        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
    }

    @Test
    fun handleMessageShouldParseBolusSpeed() {
        // Given - Speed level 5
        val packet = BolusSpeedInquireResponsePacket(packetInjector)
        val data = createValidPacket(speed = 5)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.speed).isEqualTo(5)
        verify(preferences).put(DiaconnIntKey.BolusSpeed, 5)
    }

    @Test
    fun handleMessageShouldHandleMinSpeed() {
        // Given - Speed level 1 (slowest)
        val packet = BolusSpeedInquireResponsePacket(packetInjector)
        val data = createValidPacket(speed = 1)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.speed).isEqualTo(1)
        verify(preferences).put(DiaconnIntKey.BolusSpeed, 1)
    }

    @Test
    fun handleMessageShouldHandleMaxSpeed() {
        // Given - Speed level 8 (fastest)
        val packet = BolusSpeedInquireResponsePacket(packetInjector)
        val data = createValidPacket(speed = 8)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.speed).isEqualTo(8)
        verify(preferences).put(DiaconnIntKey.BolusSpeed, 8)
    }

    @Test
    fun handleMessageShouldHandleAllSpeedLevels() {
        // Test all speed levels 1-8
        for (speed in 1..8) {
            val packet = BolusSpeedInquireResponsePacket(packetInjector)
            val data = createValidPacket(speed = speed)

            packet.handleMessage(data)

            assertThat(packet.failed).isFalse()
            assertThat(diaconnG8Pump.speed).isEqualTo(speed)
        }
    }

    @Test
    fun handleMessageShouldFailOnInvalidResult() {
        // Given
        val packet = BolusSpeedInquireResponsePacket(packetInjector)
        val data = createPacketWithResult(17) // CRC error

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = BolusSpeedInquireResponsePacket(packetInjector)
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
        val packet = BolusSpeedInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0x85.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = BolusSpeedInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_BOLUS_SPEED_INQUIRE_RESPONSE")
    }

    private fun createValidPacket(speed: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0x85.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = 16.toByte()   // result (success)
        data[5] = speed.toByte()

        for (i in 6 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }

    private fun createPacketWithResult(result: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0x85.toByte()
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
