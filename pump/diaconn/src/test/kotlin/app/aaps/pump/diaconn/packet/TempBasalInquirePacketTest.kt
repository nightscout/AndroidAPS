package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TempBasalInquirePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is TempBasalInquirePacket) {
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
    fun encodeShouldGenerateValidPacket() {
        // Given
        val packet = TempBasalInquirePacket(packetInjector)
        val msgSeq = 5

        // When
        val encoded = packet.encode(msgSeq)

        // Then
        assertThat(encoded.size).isEqualTo(20)
        assertThat(encoded[0]).isEqualTo(0xef.toByte()) // SOP
        assertThat(encoded[1]).isEqualTo(0x4A.toByte()) // msgType
        assertThat(encoded[2]).isEqualTo(msgSeq.toByte())
        assertThat(encoded[3]).isEqualTo(0x00.toByte()) // MSG_CON_END
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = TempBasalInquirePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0x4A.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = TempBasalInquirePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_TEMP_BASAL_INQUIRE_REQUEST")
    }
}
