package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppConfirmSettingResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is AppConfirmSettingResponsePacket) {
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
    fun handleMessageShouldSucceedOnValidResult() {
        // Given
        val packet = AppConfirmSettingResponsePacket(packetInjector)
        val data = createValidPacket(result = 0)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(packet.result).isEqualTo(0)
    }

    @Test
    fun handleMessageShouldSetReadyToBolusOnBolusConfirm() {
        // Given
        val packet = AppConfirmSettingResponsePacket(packetInjector)
        diaconnG8Pump.bolusConfirmMessage = 0x07.toByte()
        val data = createValidPacket(result = 0)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.isReadyToBolus).isTrue()
    }

    @Test
    fun handleMessageShouldNotSetReadyToBolusOnNonBolusConfirm() {
        // Given
        val packet = AppConfirmSettingResponsePacket(packetInjector)
        diaconnG8Pump.bolusConfirmMessage = 0x05.toByte()
        val data = createValidPacket(result = 0)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.isReadyToBolus).isFalse()
    }

    @Test
    fun handleMessageShouldFailOnInvalidResult() {
        // Given
        val packet = AppConfirmSettingResponsePacket(packetInjector)
        val data = createValidPacket(result = 1)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
        assertThat(diaconnG8Pump.resultErrorCode).isEqualTo(1)
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = AppConfirmSettingResponsePacket(packetInjector)
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
        val packet = AppConfirmSettingResponsePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0xB7.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = AppConfirmSettingResponsePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_APP_CONFIRM_SETTING_RESPONSE")
    }

    private fun createValidPacket(result: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0xB7.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = result.toByte()

        for (i in 5 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }
}
