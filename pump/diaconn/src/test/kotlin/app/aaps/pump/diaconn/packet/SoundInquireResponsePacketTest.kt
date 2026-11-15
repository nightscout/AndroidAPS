package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SoundInquireResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = MetroMemberInjector {
        if (it is SoundInquireResponsePacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
                it.diaconnG8Pump = diaconnG8Pump
        }
        true
    }

    @BeforeEach
    fun setup() {
        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
    }

    /**
     * A real G8 (fw 2.50, 2026-11-12) set to silent and low answered `03 01`. The values are those the setting and the
     * pump settings screen use (1 sound, 2 vibrate, 3 silent; 1 low, 2 middle, 3 high). They were read as one less.
     */
    @Test
    fun theSettingsOfARealPumpAreReadAsTheSettingTakesThem() {
        val packet = SoundInquireResponsePacket(packetInjector)

        packet.handleMessage(createValidPacket(beepAndAlarm = 3, alarmIntensity = 1))

        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.beepAndAlarm).isEqualTo(3)
        assertThat(diaconnG8Pump.alarmIntensity).isEqualTo(1)
    }

    @Test
    fun soundAndHighAreRead() {
        val packet = SoundInquireResponsePacket(packetInjector)

        packet.handleMessage(createValidPacket(beepAndAlarm = 1, alarmIntensity = 3))

        assertThat(diaconnG8Pump.beepAndAlarm).isEqualTo(1)
        assertThat(diaconnG8Pump.alarmIntensity).isEqualTo(3)
    }

    @Test
    fun handleMessageShouldFailOnInvalidResult() {
        // Given
        val packet = SoundInquireResponsePacket(packetInjector)
        val data = createPacketWithResult(17) // CRC error

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = SoundInquireResponsePacket(packetInjector)
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
        val packet = SoundInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0x8D.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = SoundInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_SOUND_INQUIRE_RESPONSE")
    }

    private fun createValidPacket(beepAndAlarm: Int, alarmIntensity: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0x8D.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = 16.toByte()   // result (success)
        data[5] = beepAndAlarm.toByte()
        data[6] = alarmIntensity.toByte()

        for (i in 7 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }

    private fun createPacketWithResult(result: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0x8D.toByte()
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
