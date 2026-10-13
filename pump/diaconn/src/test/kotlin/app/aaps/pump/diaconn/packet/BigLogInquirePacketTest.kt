package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BigLogInquirePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is BigLogInquirePacket) {
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
    fun encodeShouldGenerateValidPacketWithParameters() {
        // Given
        val start = 100
        val end = 200
        val delay = 5
        val packet = BigLogInquirePacket(packetInjector, start, end, delay)
        val msgSeq = 8

        // When
        val encoded = packet.encode(msgSeq)

        // Then
        assertThat(encoded).isNotNull()
        assertThat(encoded.size).isGreaterThan(0)
        // Check message type
        assertThat(encoded[1]).isEqualTo(0x72.toByte())
        // Check sequence
        assertThat(encoded[2]).isEqualTo(msgSeq.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleZeroValues() {
        // Given - All zero parameters
        val start = 0
        val end = 0
        val delay = 0
        val packet = BigLogInquirePacket(packetInjector, start, end, delay)

        // When
        val encoded = packet.encode(1)

        // Then
        assertThat(encoded[1]).isEqualTo(0x72.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun encodeShouldHandleMaxValues() {
        // Given - Maximum log range
        val start = 1
        val end = 32767
        val delay = 255
        val packet = BigLogInquirePacket(packetInjector, start, end, delay)

        // When
        val encoded = packet.encode(5)

        // Then
        assertThat(encoded[1]).isEqualTo(0x72.toByte())
        assertThat(DiaconnG8Packet.defect(encoded)).isEqualTo(0)
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = BigLogInquirePacket(packetInjector, 0, 0, 0)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_BIG_LOG_INQUIRE")
    }
}
