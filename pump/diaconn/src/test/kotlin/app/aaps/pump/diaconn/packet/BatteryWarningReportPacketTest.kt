package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BatteryWarningReportPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is BatteryWarningReportPacket) {
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
    fun handleMessageShouldParseBatteryWarning() {
        // Given - Warning level battery with 30% remaining
        val packet = BatteryWarningReportPacket(packetInjector)
        val data = createValidPacket(
            grade = 2,   // Warning
            process = 2, // Stop
            remain = 30  // 30%
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.batteryWaningGrade).isEqualTo(2)
        assertThat(diaconnG8Pump.batteryWaningProcess).isEqualTo(2)
        assertThat(diaconnG8Pump.batteryWaningRemain).isEqualTo(30)
    }

    @Test
    fun handleMessageShouldParseInfoLevelBattery() {
        // Given - Info level (grade 1) with 50% remaining
        val packet = BatteryWarningReportPacket(packetInjector)
        val data = createValidPacket(
            grade = 1,   // Info
            process = 1, // Skip
            remain = 50
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.batteryWaningGrade).isEqualTo(1)
        assertThat(diaconnG8Pump.batteryWaningProcess).isEqualTo(1)
        assertThat(diaconnG8Pump.batteryWaningRemain).isEqualTo(50)
    }

    @Test
    fun handleMessageShouldParseCriticalBattery() {
        // Given - Critical level (grade 4) with 5% remaining
        val packet = BatteryWarningReportPacket(packetInjector)
        val data = createValidPacket(
            grade = 4,   // Critical
            process = 2, // Stop
            remain = 5
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.batteryWaningGrade).isEqualTo(4)
        assertThat(diaconnG8Pump.batteryWaningProcess).isEqualTo(2)
        assertThat(diaconnG8Pump.batteryWaningRemain).isEqualTo(5)
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = BatteryWarningReportPacket(packetInjector)
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
        val packet = BatteryWarningReportPacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0xD7.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = BatteryWarningReportPacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_BATTERY_WARNING_REPORT")
    }

    private fun createValidPacket(
        grade: Int,
        process: Int,
        remain: Int
    ): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0xD7.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = grade.toByte()
        data[5] = process.toByte()
        data[6] = remain.toByte()

        // Fill rest with padding
        for (i in 7 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }
}
