package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfirmReportPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is ConfirmReportPacket) {
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
    fun handleMessageShouldParseConfirmReport() {
        // Given - Confirm report for temp basal (0x0A)
        val packet = ConfirmReportPacket(packetInjector)
        val data = createValidPacket(reqMsgType = 0x0A)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(packet.reqMsgType).isEqualTo(0x0A)
    }

    @Test
    fun handleMessageShouldHandleDifferentMessageTypes() {
        // Test various message types
        val messageTypes = listOf(0x06, 0x07, 0x08, 0x0A, 0x0B)

        messageTypes.forEach { msgType ->
            val packet = ConfirmReportPacket(packetInjector)
            val data = createValidPacket(reqMsgType = msgType)

            packet.handleMessage(data)

            assertThat(packet.failed).isFalse()
            assertThat(packet.reqMsgType).isEqualTo(msgType)
        }
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = ConfirmReportPacket(packetInjector)
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
        val packet = ConfirmReportPacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0xE8.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = ConfirmReportPacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_CONFIRM_REPORT")
    }

    private fun createValidPacket(reqMsgType: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0xE8.toByte()
        data[2] = 0x01.toByte()
        data[3] = 0x00.toByte()
        data[4] = reqMsgType.toByte()

        for (i in 5 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }
}
