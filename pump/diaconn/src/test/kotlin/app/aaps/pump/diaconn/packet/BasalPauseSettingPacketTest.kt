package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BasalPauseSettingPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is BasalPauseSettingPacket) {
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
    fun encodeShouldGenerateValidPacketForPauseBasal() {
        // Given - Pause basal
        val status = 1 // Pause
        val packet = BasalPauseSettingPacket(packetInjector, status)

        // When
        val encoded = packet.encode(10)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(encoded[0]).isEqualTo(0xef.toByte()) // SOP
        assertThat(encoded[1]).isEqualTo(0x03.toByte()) // msgType
        assertThat(encoded[2]).isEqualTo(10.toByte()) // seq
        assertThat(encoded[3]).isEqualTo(0x00.toByte()) // MSG_CON_END
        assertThat(encoded[4]).isEqualTo(status.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldGenerateValidPacketForResumeBasal() {
        // Given - Resume/cancel pause
        val status = 2 // Cancel pause
        val packet = BasalPauseSettingPacket(packetInjector, status)

        // When
        val encoded = packet.encode(5)

        // Then
        assertThat(encoded[4]).isEqualTo(status.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = BasalPauseSettingPacket(packetInjector, 1)

        // Then
        assertThat(packet.msgType).isEqualTo(0x03.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = BasalPauseSettingPacket(packetInjector, 1)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_BASAL_PAUSE_SETTING")
    }
}
