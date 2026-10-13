package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SoundInquireResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is SoundInquireResponsePacket) {
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
    fun handleMessageShouldParseSoundSettings() {
        // Given - Beep on (2), alarm intensity level 3 (4)
        val packet = SoundInquireResponsePacket(packetInjector)
        val data = createValidPacket(beepAndAlarm = 2, alarmIntensity = 4)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.beepAndAlarm).isEqualTo(1) // 2 - 1
        assertThat(diaconnG8Pump.alarmIntensity).isEqualTo(3) // 4 - 1
    }

    @Test
    fun handleMessageShouldHandleBeepOff() {
        // Given - Beep off (1), alarm intensity low (1)
        val packet = SoundInquireResponsePacket(packetInjector)
        val data = createValidPacket(beepAndAlarm = 1, alarmIntensity = 1)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.beepAndAlarm).isEqualTo(0) // 1 - 1
        assertThat(diaconnG8Pump.alarmIntensity).isEqualTo(0) // 1 - 1
    }

    @Test
    fun handleMessageShouldHandleMaxVolume() {
        // Given - Max settings
        val packet = SoundInquireResponsePacket(packetInjector)
        val data = createValidPacket(beepAndAlarm = 3, alarmIntensity = 5)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.beepAndAlarm).isEqualTo(2) // 3 - 1
        assertThat(diaconnG8Pump.alarmIntensity).isEqualTo(4) // 5 - 1
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
