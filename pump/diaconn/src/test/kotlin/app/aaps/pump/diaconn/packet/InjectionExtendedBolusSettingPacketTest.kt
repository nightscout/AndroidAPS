package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InjectionExtendedBolusSettingPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is InjectionExtendedBolusSettingPacket) {
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
    fun encodeShouldGenerateValidPacketForExtendedBolus() {
        // Given - 5.0U extended over 60 minutes
        val amount = 500 // 5.0U * 100
        val minutes = 60
        val bcDttm = 946652400L

        val packet = InjectionExtendedBolusSettingPacket(packetInjector, amount, minutes, bcDttm)

        // When
        val encoded = packet.encode(10)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(encoded[0]).isEqualTo(0xef.toByte()) // SOP
        assertThat(encoded[1]).isEqualTo(0x08.toByte()) // msgType
        assertThat(encoded[2]).isEqualTo(10.toByte()) // seq
        assertThat(encoded[3]).isEqualTo(0x00.toByte()) // MSG_CON_END
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleShortDurationExtendedBolus() {
        // Given - 2.0U extended over 30 minutes (minimum)
        val amount = 200
        val minutes = 30
        val bcDttm = 946652400L

        val packet = InjectionExtendedBolusSettingPacket(packetInjector, amount, minutes, bcDttm)

        // When
        val encoded = packet.encode(1)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleLongDurationExtendedBolus() {
        // Given - 10.0U extended over 4 hours (240 minutes)
        val amount = 1000
        val minutes = 240
        val bcDttm = 946652400L

        val packet = InjectionExtendedBolusSettingPacket(packetInjector, amount, minutes, bcDttm)

        // When
        val encoded = packet.encode(1)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleMaxDurationExtendedBolus() {
        // Given - Extended bolus over max duration (300 minutes / 5 hours)
        val amount = 1500
        val minutes = 300
        val bcDttm = 946652400L

        val packet = InjectionExtendedBolusSettingPacket(packetInjector, amount, minutes, bcDttm)

        // When
        val encoded = packet.encode(1)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = InjectionExtendedBolusSettingPacket(packetInjector, 500, 60, 946652400L)

        // Then
        assertThat(packet.msgType).isEqualTo(0x08.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = InjectionExtendedBolusSettingPacket(packetInjector, 500, 60, 946652400L)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_INJECTION_EXTENDED_BOLUS_SETTING")
    }
}
