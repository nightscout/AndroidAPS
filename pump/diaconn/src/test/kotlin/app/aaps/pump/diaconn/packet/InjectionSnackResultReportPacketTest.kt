package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InjectionSnackResultReportPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is InjectionSnackResultReportPacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
                it.diaconnG8Pump = diaconnG8Pump
                it.rxBus = rxBus
                it.rh = rh
            }
        }
    }

    @BeforeEach
    fun setup() {
        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
        // Reset bolus progress data
        BolusProgressData.delivered = 0.0
    }

    @Test
    fun handleMessageShouldProcessValidReport() {
        // Given - Successful bolus completion
        val packet = InjectionSnackResultReportPacket(packetInjector)
        val data = createValidPacket(
            result = 0, // Success
            bolusAmount = 500, // 5.00 U
            deliveredAmount = 500 // 5.00 U
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.lastBolusAmount).isEqualTo(5.0)
        assertThat(diaconnG8Pump.bolusDone).isTrue()
        assertThat(diaconnG8Pump.bolusStopped).isFalse()
        assertThat(BolusProgressData.delivered).isEqualTo(5.0)
    }

    @Test
    fun handleMessageShouldHandleUserCancellation() {
        // Given - User stopped bolus (result = 1)
        val packet = InjectionSnackResultReportPacket(packetInjector)
        val data = createValidPacket(
            result = 1, // User stop
            bolusAmount = 1000, // 10.00 U requested
            deliveredAmount = 450 // 4.50 U delivered
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.lastBolusAmount).isEqualTo(4.5)
        assertThat(diaconnG8Pump.bolusDone).isTrue()
        assertThat(diaconnG8Pump.bolusStopped).isTrue()
        assertThat(BolusProgressData.delivered).isEqualTo(4.5)
    }

    @Test
    fun handleMessageShouldHandleSmallBolus() {
        // Given - Small bolus of 0.5 U
        val packet = InjectionSnackResultReportPacket(packetInjector)
        val data = createValidPacket(
            result = 0,
            bolusAmount = 50, // 0.50 U
            deliveredAmount = 50 // 0.50 U
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.lastBolusAmount).isEqualTo(0.5)
        assertThat(BolusProgressData.delivered).isEqualTo(0.5)
    }

    @Test
    fun handleMessageShouldHandleLargeBolus() {
        // Given - Large bolus of 15 U
        val packet = InjectionSnackResultReportPacket(packetInjector)
        val data = createValidPacket(
            result = 0,
            bolusAmount = 1500, // 15.00 U
            deliveredAmount = 1500 // 15.00 U
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.lastBolusAmount).isEqualTo(15.0)
        assertThat(BolusProgressData.delivered).isEqualTo(15.0)
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = InjectionSnackResultReportPacket(packetInjector)
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
        val packet = InjectionSnackResultReportPacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0xe4.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = InjectionSnackResultReportPacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_INJECTION_SNACK_REPORT")
    }

    private fun createValidPacket(
        result: Int,
        bolusAmount: Int,
        deliveredAmount: Int
    ): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0xe4.toByte()
        data[2] = 0x01.toByte()
        data[3] = 0x00.toByte()
        data[4] = result.toByte()
        // Bolus amount to be delivered (2 bytes) - LITTLE_ENDIAN
        data[5] = bolusAmount.toByte()             // Low byte
        data[6] = (bolusAmount shr 8).toByte()     // High byte
        // Delivered bolus amount (2 bytes) - LITTLE_ENDIAN
        data[7] = deliveredAmount.toByte()         // Low byte
        data[8] = (deliveredAmount shr 8).toByte() // High byte

        for (i in 9 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }
}
