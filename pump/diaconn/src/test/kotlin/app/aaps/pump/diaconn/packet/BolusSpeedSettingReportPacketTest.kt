package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BolusSpeedSettingReportPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is BolusSpeedSettingReportPacket) {
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
    fun handleMessageShouldProcessValidReport() {
        // Given - Speed level 5 report
        val packet = BolusSpeedSettingReportPacket(packetInjector)
        val data = createValidPacket(speed = 5)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.speed).isEqualTo(5)
    }

    @Test
    fun handleMessageShouldHandleMinSpeed() {
        // Given - Speed level 1 (slowest)
        val packet = BolusSpeedSettingReportPacket(packetInjector)
        val data = createValidPacket(speed = 1)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.speed).isEqualTo(1)
    }

    @Test
    fun handleMessageShouldHandleMaxSpeed() {
        // Given - Speed level 8 (fastest)
        val packet = BolusSpeedSettingReportPacket(packetInjector)
        val data = createValidPacket(speed = 8)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.speed).isEqualTo(8)
    }

    @Test
    fun handleMessageShouldHandleAllSpeedLevels() {
        // Test all speed levels 1-8
        for (speed in 1..8) {
            val packet = BolusSpeedSettingReportPacket(packetInjector)
            val data = createValidPacket(speed = speed)

            packet.handleMessage(data)

            assertThat(packet.failed).isFalse()
            assertThat(diaconnG8Pump.speed).isEqualTo(speed)
        }
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = BolusSpeedSettingReportPacket(packetInjector)
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
        val packet = BolusSpeedSettingReportPacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0xC5.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = BolusSpeedSettingReportPacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_BOLUS_SPEED_SETTING_REPORT")
    }

    private fun createValidPacket(speed: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0xC5.toByte()
        data[2] = 0x01.toByte()
        data[3] = 0x00.toByte()
        data[4] = speed.toByte()

        for (i in 5 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }
}
