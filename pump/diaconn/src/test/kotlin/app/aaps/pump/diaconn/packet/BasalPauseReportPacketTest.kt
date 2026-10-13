package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BasalPauseReportPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is BasalPauseReportPacket) {
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
    fun handleMessageShouldParseBasalPausedStatus() {
        // Given
        val packet = BasalPauseReportPacket(packetInjector)
        val data = createValidPacket(status = 1)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(packet.status).isEqualTo(1)
    }

    @Test
    fun handleMessageShouldParseBasalPauseCancelStatus() {
        // Given
        val packet = BasalPauseReportPacket(packetInjector)
        val data = createValidPacket(status = 2)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(packet.status).isEqualTo(2)
    }

    @Test
    fun handleMessageShouldHandleZeroStatus() {
        // Given
        val packet = BasalPauseReportPacket(packetInjector)
        val data = createValidPacket(status = 0)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(packet.status).isEqualTo(0)
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = BasalPauseReportPacket(packetInjector)
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
        val packet = BasalPauseReportPacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0xC3.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = BasalPauseReportPacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_BASAL_PAUSE_REPORT")
    }

    private fun createValidPacket(status: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0xC3.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = status.toByte()

        for (i in 5 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }
}
