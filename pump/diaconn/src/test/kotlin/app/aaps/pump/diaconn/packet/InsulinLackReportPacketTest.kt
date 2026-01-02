package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InsulinLackReportPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is InsulinLackReportPacket) {
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
    fun handleMessageShouldParseInsulinWarningFields() {
        // Given
        val packet = InsulinLackReportPacket(packetInjector)
        val data = createValidPacket(
            grade = 2,
            process = 1,
            remain = 50
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.insulinWarningGrade).isEqualTo(2)
        assertThat(diaconnG8Pump.insulinWarningProcess).isEqualTo(1)
        assertThat(diaconnG8Pump.insulinWarningRemain).isEqualTo(50)
    }

    @Test
    fun handleMessageShouldHandleCriticalWarning() {
        // Given
        val packet = InsulinLackReportPacket(packetInjector)
        val data = createValidPacket(
            grade = 4,
            process = 2,
            remain = 0
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.insulinWarningGrade).isEqualTo(4)
        assertThat(diaconnG8Pump.insulinWarningProcess).isEqualTo(2)
        assertThat(diaconnG8Pump.insulinWarningRemain).isEqualTo(0)
    }

    @Test
    fun handleMessageShouldHandleFullInsulinLevel() {
        // Given
        val packet = InsulinLackReportPacket(packetInjector)
        val data = createValidPacket(
            grade = 1,
            process = 3,
            remain = 100
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.insulinWarningGrade).isEqualTo(1)
        assertThat(diaconnG8Pump.insulinWarningProcess).isEqualTo(3)
        assertThat(diaconnG8Pump.insulinWarningRemain).isEqualTo(100)
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = InsulinLackReportPacket(packetInjector)
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
        val packet = InsulinLackReportPacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0xD8.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = InsulinLackReportPacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_INJECTION_LACK_REPORT")
    }

    private fun createValidPacket(
        grade: Int,
        process: Int,
        remain: Int
    ): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0xD8.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = grade.toByte()
        data[5] = process.toByte()
        data[6] = remain.toByte()

        for (i in 7 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }
}
