package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InjectionSnackInquireResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is InjectionSnackInquireResponsePacket) {
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
    fun handleMessageShouldParseSnackInjectionStatus() {
        // Given - Snack injection in progress
        val packet = InjectionSnackInquireResponsePacket(packetInjector)
        val data = createValidPacket(
            status = 1,      // Injecting
            amount = 500,    // 5.0U
            injAmount = 250, // 2.5U injected
            speed = 3        // Speed 3
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.snackStatus).isEqualTo(1)
        assertThat(diaconnG8Pump.snackAmount).isWithin(0.01).of(5.0)
        assertThat(diaconnG8Pump.snackInjAmount).isWithin(0.01).of(2.5)
        assertThat(diaconnG8Pump.snackSpeed).isEqualTo(3)
    }

    @Test
    fun handleMessageShouldHandleNoInjection() {
        // Given - No injection in progress
        val packet = InjectionSnackInquireResponsePacket(packetInjector)
        val data = createValidPacket(
            status = 2,    // Not injecting
            amount = 0,
            injAmount = 0,
            speed = 0
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.snackStatus).isEqualTo(2)
        assertThat(diaconnG8Pump.snackAmount).isWithin(0.01).of(0.0)
        assertThat(diaconnG8Pump.snackInjAmount).isWithin(0.01).of(0.0)
    }

    @Test
    fun handleMessageShouldHandleCompletedInjection() {
        // Given - Injection completed (10U set, 10U injected)
        val packet = InjectionSnackInquireResponsePacket(packetInjector)
        val data = createValidPacket(
            status = 2,       // Completed
            amount = 1000,    // 10.0U
            injAmount = 1000, // 10.0U
            speed = 5
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.snackAmount).isWithin(0.01).of(10.0)
        assertThat(diaconnG8Pump.snackInjAmount).isWithin(0.01).of(10.0)
    }

    @Test
    fun handleMessageShouldHandlePartialInjection() {
        // Given - Partial injection (8U set, 3.5U injected)
        val packet = InjectionSnackInquireResponsePacket(packetInjector)
        val data = createValidPacket(
            status = 1,
            amount = 800,
            injAmount = 350,
            speed = 4
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.snackAmount).isWithin(0.01).of(8.0)
        assertThat(diaconnG8Pump.snackInjAmount).isWithin(0.01).of(3.5)
    }

    @Test
    fun handleMessageShouldHandleDifferentSpeeds() {
        // Given - Test different injection speeds
        val packet = InjectionSnackInquireResponsePacket(packetInjector)

        for (speed in 1..8) {
            val data = createValidPacket(
                status = 1,
                amount = 500,
                injAmount = 250,
                speed = speed
            )
            packet.handleMessage(data)
            assertThat(diaconnG8Pump.snackSpeed).isEqualTo(speed)
        }
    }

    @Test
    fun handleMessageShouldFailOnInvalidResult() {
        // Given
        val packet = InjectionSnackInquireResponsePacket(packetInjector)
        val data = createPacketWithResult(17) // CRC error

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = InjectionSnackInquireResponsePacket(packetInjector)
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
        val packet = InjectionSnackInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0x87.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = InjectionSnackInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_INJECTION_SNACK_INQUIRE_RESPONSE")
    }

    private fun createValidPacket(status: Int, amount: Int, injAmount: Int, speed: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0x87.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = 16.toByte()   // result (success)
        data[5] = status.toByte()
        data[6] = (amount and 0xFF).toByte() // amount low byte
        data[7] = ((amount shr 8) and 0xFF).toByte() // amount high byte
        data[8] = (injAmount and 0xFF).toByte() // injAmount low byte
        data[9] = ((injAmount shr 8) and 0xFF).toByte() // injAmount high byte
        data[10] = speed.toByte()

        for (i in 11 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }

    private fun createPacketWithResult(result: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0x87.toByte()
        data[2] = 0x01.toByte()
        data[3] = 0x00.toByte()
        data[4] = result.toByte()

        for (i in 5 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }
}
