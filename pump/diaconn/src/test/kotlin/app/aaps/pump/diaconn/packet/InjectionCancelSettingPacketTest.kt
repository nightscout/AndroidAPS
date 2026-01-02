package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InjectionCancelSettingPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is InjectionCancelSettingPacket) {
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
    fun encodeShouldGenerateValidPacketForMealBolusCancel() {
        // Given - Cancel meal bolus (msgType 0x06)
        val reqMsgType = 0x06.toByte()
        val packet = InjectionCancelSettingPacket(packetInjector, reqMsgType)

        // When
        val encoded = packet.encode(10)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(encoded[0]).isEqualTo(0xef.toByte()) // SOP
        assertThat(encoded[1]).isEqualTo(0x2B.toByte()) // msgType
        assertThat(encoded[2]).isEqualTo(10.toByte()) // seq
        assertThat(encoded[3]).isEqualTo(0x00.toByte()) // MSG_CON_END
        assertThat(encoded[4]).isEqualTo(reqMsgType) // Request type to cancel
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldGenerateValidPacketForExtendedBolusCancel() {
        // Given - Cancel extended bolus (msgType 0x08)
        val reqMsgType = 0x08.toByte()
        val packet = InjectionCancelSettingPacket(packetInjector, reqMsgType)

        // When
        val encoded = packet.encode(5)

        // Then
        assertThat(encoded[4]).isEqualTo(reqMsgType)
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldGenerateValidPacketForSnackBolusCancel() {
        // Given - Cancel snack bolus (msgType 0x07)
        val reqMsgType = 0x07.toByte()
        val packet = InjectionCancelSettingPacket(packetInjector, reqMsgType)

        // When
        val encoded = packet.encode(1)

        // Then
        assertThat(encoded[4]).isEqualTo(reqMsgType)
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = InjectionCancelSettingPacket(packetInjector, 0x06.toByte())

        // Then
        assertThat(packet.msgType).isEqualTo(0x2B.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = InjectionCancelSettingPacket(packetInjector, 0x06.toByte())

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_INJECTION_CANCEL_SETTING_REQUEST")
    }
}
