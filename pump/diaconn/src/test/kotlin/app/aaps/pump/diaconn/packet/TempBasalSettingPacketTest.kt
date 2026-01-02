package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TempBasalSettingPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is TempBasalSettingPacket) {
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
    fun encodeShouldGenerateValidPacketForStartingTempBasal() {
        // Given - Start temp basal at 150% for 4 hours (16 * 15min intervals)
        val status = 1 // Running
        val time = 16 // 4 hours = 16 * 15min
        val injectRateRatio = 50150 // 150% = 50000 + 150

        val packet = TempBasalSettingPacket(packetInjector, status, time, injectRateRatio)
        val msgSeq = 10

        // When
        val encoded = packet.encode(msgSeq)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(encoded[0]).isEqualTo(0xef.toByte()) // SOP
        assertThat(encoded[1]).isEqualTo(0x0A.toByte()) // msgType
        assertThat(encoded[2]).isEqualTo(msgSeq.toByte())
        assertThat(encoded[3]).isEqualTo(0x00.toByte()) // MSG_CON_END
        assertThat(encoded[4]).isEqualTo(status.toByte())
        assertThat(encoded[5]).isEqualTo(time.toByte())

        // Verify CRC
        val defectResult = DiaconnG8Packet.defect(encoded)
        assertThat(defectResult).isEqualTo(0)
    }

    @Test
    fun encodeShouldGenerateValidPacketForStoppingTempBasal() {
        // Given - Stop temp basal
        val status = 2 // Dismissed
        val time = 0
        val injectRateRatio = 0

        val packet = TempBasalSettingPacket(packetInjector, status, time, injectRateRatio)

        // When
        val encoded = packet.encode(5)

        // Then
        assertThat(encoded[4]).isEqualTo(status.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldGenerateValidPacketForAbsoluteTempBasal() {
        // Given - Absolute temp basal 3.5 U/h = 350 + 1000 = 1350
        val status = 1
        val time = 8 // 2 hours
        val injectRateRatio = 1350 // 3.5 U/h

        val packet = TempBasalSettingPacket(packetInjector, status, time, injectRateRatio)

        // When
        val encoded = packet.encode(1)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = TempBasalSettingPacket(packetInjector, 1, 10, 50100)

        // Then
        assertThat(packet.msgType).isEqualTo(0x0A.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = TempBasalSettingPacket(packetInjector, 1, 10, 50100)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_TEMP_BASAL_SETTING")
    }
}
