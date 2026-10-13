package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BigAPSMainInfoInquirePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is BigAPSMainInfoInquirePacket) {
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
    fun encodeShouldGenerateValidPacket() {
        // Given
        val packet = BigAPSMainInfoInquirePacket(packetInjector)
        val msgSeq = 7

        // When
        val encoded = packet.encode(msgSeq)

        // Then
        assertThat(encoded).isNotNull()
        assertThat(encoded.size).isGreaterThan(0)
        // Check message type
        assertThat(encoded[1]).isEqualTo(0x54.toByte())
        // Check sequence
        assertThat(encoded[2]).isEqualTo(msgSeq.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = BigAPSMainInfoInquirePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_BIG_APS_MAIN_INFO_INQUIRE")
    }
}
