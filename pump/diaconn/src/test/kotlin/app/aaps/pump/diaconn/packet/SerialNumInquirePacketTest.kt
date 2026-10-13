package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SerialNumInquirePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is SerialNumInquirePacket) {
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
        val packet = SerialNumInquirePacket(packetInjector)
        val msgSeq = 5

        // When
        val encoded = packet.encode(msgSeq)

        // Then
        assertThat(encoded.size).isEqualTo(20) // Standard packet size
        assertThat(encoded[0]).isEqualTo(0xef.toByte()) // SOP
        assertThat(encoded[1]).isEqualTo(0x6E.toByte()) // msgType
        assertThat(encoded[2]).isEqualTo(msgSeq.toByte()) // sequence
        assertThat(encoded[3]).isEqualTo(0x00.toByte()) // MSG_CON_END
        assertThat(encoded[19]).isEqualTo(DiaconnG8Packet.getCRC(encoded, 19)) // CRC
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = SerialNumInquirePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0x6E.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = SerialNumInquirePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_SERIAL_NUM_INQUIRE")
    }

    @Test
    fun encodedPacketShouldHaveValidCRC() {
        // Given
        val packet = SerialNumInquirePacket(packetInjector)
        val encoded = packet.encode(10)

        // When
        val defectResult = DiaconnG8Packet.defect(encoded)

        // Then
        assertThat(defectResult).isEqualTo(0) // No defect
    }
}
