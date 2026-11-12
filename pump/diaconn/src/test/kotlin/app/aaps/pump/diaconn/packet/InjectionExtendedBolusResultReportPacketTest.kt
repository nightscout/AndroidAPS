package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InjectionExtendedBolusResultReportPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = MetroMemberInjector {
        if (it is InjectionExtendedBolusResultReportPacket) {
                it.aapsLogger = aapsLogger
                it.diaconnG8Pump = diaconnG8Pump
        }
        true
    }

    @BeforeEach
    fun setup() {
        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
    }

    /** Result 0 is "given in full": the extended bolus has ended. It was read as "running". */
    @Test
    fun anExtendedBolusGivenInFullHasEnded() {
        val packet = InjectionExtendedBolusResultReportPacket(packetInjector)
        diaconnG8Pump.squareStatus = 1
        val data = createValidPacket(
            result = 0,
            settingMinutes = 60,
            elapsedTime = 60,
            bolusAmount = 500, // 5.00 U
            deliveredAmount = 500
        )

        packet.handleMessage(data)

        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.squareStatus).isEqualTo(2)
        assertThat(diaconnG8Pump.squareTime).isEqualTo(60)
        assertThat(diaconnG8Pump.squareInjTime).isEqualTo(60)
        assertThat(diaconnG8Pump.squareAmount).isEqualTo(5.0)
        assertThat(diaconnG8Pump.squareInjAmount).isEqualTo(5.0)
    }

    /** The bytes a real G8 (fw 2.50, 2026-11-11) sent after the stop of an extended bolus of 0.50 U over 30 min. */
    @Test
    fun theReportOfARealStopIsRead() {
        val packet = InjectionExtendedBolusResultReportPacket(packetInjector)
        diaconnG8Pump.squareStatus = 1

        packet.handleMessage(createValidPacket(result = 1, settingMinutes = 0x1e, elapsedTime = 3, bolusAmount = 0x32, deliveredAmount = 5))

        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.squareStatus).isEqualTo(2)
        assertThat(diaconnG8Pump.squareTime).isEqualTo(30)
        assertThat(diaconnG8Pump.squareInjTime).isEqualTo(3)
        assertThat(diaconnG8Pump.squareAmount).isEqualTo(0.5)
        assertThat(diaconnG8Pump.squareInjAmount).isEqualTo(0.05)
    }

    @Test
    fun handleMessageShouldHandleCompletedBolus() {
        // Given - Extended bolus completed (result = 1 or 2)
        val packet = InjectionExtendedBolusResultReportPacket(packetInjector)
        val data = createValidPacket(
            result = 1, // User stop
            settingMinutes = 120,
            elapsedTime = 120,
            bolusAmount = 1000, // 10.00 U
            deliveredAmount = 1000 // 10.00 U (fully delivered)
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.squareStatus).isEqualTo(2) // 2 = stopped
        assertThat(diaconnG8Pump.squareAmount).isEqualTo(10.0)
        assertThat(diaconnG8Pump.squareInjAmount).isEqualTo(10.0)
    }

    @Test
    fun aFailedExtendedBolusHasEnded() {
        val packet = InjectionExtendedBolusResultReportPacket(packetInjector)
        diaconnG8Pump.squareStatus = 1
        val data = createValidPacket(
            result = 2,
            settingMinutes = 90,
            elapsedTime = 45,
            bolusAmount = 900, // 9.00 U
            deliveredAmount = 450 // 4.50 U (half delivered)
        )

        packet.handleMessage(data)

        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.squareStatus).isEqualTo(2)
        assertThat(diaconnG8Pump.squareAmount).isEqualTo(9.0)
        assertThat(diaconnG8Pump.squareInjAmount).isEqualTo(4.5)
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = InjectionExtendedBolusResultReportPacket(packetInjector)
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
        val packet = InjectionExtendedBolusResultReportPacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0xe5.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = InjectionExtendedBolusResultReportPacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_INJECTION_EXTENDED_BOLUS_RESULT_REPORT")
    }

    private fun createValidPacket(
        result: Int,
        settingMinutes: Int,
        elapsedTime: Int,
        bolusAmount: Int,
        deliveredAmount: Int
    ): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0xe5.toByte()
        data[2] = 0x01.toByte()
        data[3] = 0x00.toByte()
        data[4] = result.toByte()
        // Setting minutes (2 bytes) - LITTLE_ENDIAN
        data[5] = settingMinutes.toByte()             // Low byte
        data[6] = (settingMinutes shr 8).toByte()     // High byte
        // Elapsed time (2 bytes) - LITTLE_ENDIAN
        data[7] = elapsedTime.toByte()                // Low byte
        data[8] = (elapsedTime shr 8).toByte()        // High byte
        // Bolus amount (2 bytes) - LITTLE_ENDIAN
        data[9] = bolusAmount.toByte()                // Low byte
        data[10] = (bolusAmount shr 8).toByte()       // High byte
        // Delivered amount (2 bytes) - LITTLE_ENDIAN
        data[11] = deliveredAmount.toByte()           // Low byte
        data[12] = (deliveredAmount shr 8).toByte()   // High byte

        for (i in 13 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }
}
