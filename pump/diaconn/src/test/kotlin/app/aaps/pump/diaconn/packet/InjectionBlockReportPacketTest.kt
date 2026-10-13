package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InjectionBlockReportPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is InjectionBlockReportPacket) {
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
    fun handleMessageShouldParseInjectionBlockFields() {
        // Given
        val packet = InjectionBlockReportPacket(packetInjector)
        val data = createValidPacket(
            grade = 2,
            process = 1,
            remainAmount = 12345, // 123.45 units
            type = 3
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.injectionBlockGrade).isEqualTo(2)
        assertThat(diaconnG8Pump.injectionBlockProcess).isEqualTo(1)
        assertThat(diaconnG8Pump.injectionBlockRemainAmount).isWithin(0.01).of(123.45)
        assertThat(diaconnG8Pump.injectionBlockType).isEqualTo(3)
    }

    @Test
    fun handleMessageShouldHandleCriticalGrade() {
        // Given
        val packet = InjectionBlockReportPacket(packetInjector)
        val data = createValidPacket(
            grade = 4,
            process = 2,
            remainAmount = 0,
            type = 1
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.injectionBlockGrade).isEqualTo(4)
        assertThat(diaconnG8Pump.injectionBlockProcess).isEqualTo(2)
        assertThat(diaconnG8Pump.injectionBlockRemainAmount).isWithin(0.01).of(0.0)
        assertThat(diaconnG8Pump.injectionBlockType).isEqualTo(1)
    }

    @Test
    fun handleMessageShouldHandleNeedleType() {
        // Given
        val packet = InjectionBlockReportPacket(packetInjector)
        val data = createValidPacket(
            grade = 1,
            process = 3,
            remainAmount = 30000, // 300.00 units
            type = 7
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.injectionBlockGrade).isEqualTo(1)
        assertThat(diaconnG8Pump.injectionBlockProcess).isEqualTo(3)
        assertThat(diaconnG8Pump.injectionBlockRemainAmount).isWithin(0.01).of(300.0)
        assertThat(diaconnG8Pump.injectionBlockType).isEqualTo(7)
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = InjectionBlockReportPacket(packetInjector)
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
        val packet = InjectionBlockReportPacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0xD8.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = InjectionBlockReportPacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_INJECTION_BLOCK_REPORT")
    }

    private fun createValidPacket(
        grade: Int,
        process: Int,
        remainAmount: Int,
        type: Int
    ): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0xD8.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = grade.toByte()
        data[5] = process.toByte()
        data[6] = (remainAmount and 0xFF).toByte()
        data[7] = ((remainAmount shr 8) and 0xFF).toByte()
        data[8] = type.toByte()

        for (i in 9 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }
}
